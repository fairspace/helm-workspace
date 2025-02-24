package io.fairspace.saturn.services.views;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.config.properties.SearchProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.config.properties.ViewsProperties.ColumnType;
import io.fairspace.saturn.config.properties.ViewsProperties.View;
import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.ValueDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.config.properties.ViewsProperties.ColumnType.Date;
import static io.fairspace.saturn.services.views.Table.idColumn;

/**
 * Executes SQL queries via JDBC on the view database
 * to obtain row data or aggregate data for a specified view
 * while applying specified filters.
 * Access to collections is not checked here. Restricting results
 * to only allowed collections can be achieved by providing these
 * collections in a filter with 'Resource_collection' as field.
 */
@Slf4j
@Component
public class ViewStoreReader {
    final SearchProperties searchProperties;
    final ViewsProperties viewsProperties;
    final ViewStoreClient.ViewStoreConfiguration configuration;
    final ViewStoreClientFactory viewStoreClientFactory;

    public ViewStoreReader(
            SearchProperties searchProperties,
            ViewsProperties viewsProperties,
            ViewStoreClientFactory viewStoreClientFactory,
            ViewStoreClient.ViewStoreConfiguration configuration) {
        this.searchProperties = searchProperties;
        this.viewsProperties = viewsProperties;
        this.configuration = configuration;
        this.viewStoreClientFactory = viewStoreClientFactory;
    }

    List<Object> getLabelsByIds(List<String> ids) throws SQLException {
        String query = "select label from label where id = ANY(?::text[])";
        try (var connection = viewStoreClientFactory.getConnection();
                var preparedStatement = connection.prepareStatement(query)) {
            var array = preparedStatement.getConnection().createArrayOf("text", ids.toArray());
            preparedStatement.setArray(1, array);
            var result = preparedStatement.executeQuery();
            var labels = new ArrayList<>();
            while (result.next()) {
                labels.add(result.getObject("label"));
            }
            return labels;
        }
    }

    String iriForLabel(String type, String label) throws SQLException {
        try (var connection = viewStoreClientFactory.getConnection();
                var query = connection.prepareStatement("select id from label where type = ? and label = ?")) {
            query.setString(1, type);
            query.setString(2, label);
            var result = query.executeQuery();
            if (result.next()) {
                return result.getString("id");
            }
        }
        return null;
    }

    Map<String, Set<ValueDto>> transformRow(View viewConfig, ResultSet result) throws SQLException {
        Map<String, Set<ValueDto>> row = new HashMap<>();
        row.put(
                viewConfig.name,
                Collections.singleton(new ValueDto(result.getString("label"), result.getString("id"))));
        if (viewConfig.name.equalsIgnoreCase("Collection")) {
            var collection = result.getString("collection");
            row.put(viewConfig.name + "_collection", Collections.singleton(new ValueDto(collection, collection)));
        }
        for (var viewColumn : viewConfig.columns) {
            if (viewColumn.type.isSet()) {
                continue;
            }
            var column = configuration.viewTables.get(viewConfig.name).getColumn(viewColumn.name.toLowerCase());
            var columnName = viewConfig.name + "_" + viewColumn.name;
            if (column.type == ColumnType.Number) {
                var value = result.getBigDecimal(column.name);
                if (value != null) {
                    row.put(columnName, Collections.singleton(new ValueDto(value.toString(), value.floatValue())));
                }
            } else if (column.type == Date) {
                var value = result.getTimestamp(column.name);
                if (value != null) {
                    row.put(
                            columnName,
                            Collections.singleton(new ValueDto(value.toInstant().toString(), value.toInstant())));
                }
            } else {
                var value = result.getString(column.name);
                if (viewColumn.type == ColumnType.Term) {
                    row.put(
                            columnName,
                            Collections.singleton(new ValueDto(value, iriForLabel(viewColumn.rdfType, value))));
                } else {
                    row.put(columnName, Collections.singleton(new ValueDto(value, value)));
                }
            }
        }
        return row;
    }

    View.Column checkField(String field) {
        if (field.equalsIgnoreCase("Resource_collection")) {
            return null;
        }
        if (field.equalsIgnoreCase("Resource")) {
            return null;
        }
        var fieldNameParts = field.split("_");
        if (fieldNameParts.length > 2) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
        var config = configuration.viewConfig.get(fieldNameParts[0]);
        if (config == null) {
            throw new IllegalArgumentException("Unknown view name: " + fieldNameParts[0]);
        }
        if (fieldNameParts.length == 1) {
            return null;
        }
        var viewColumn = config.columns.stream()
                .filter(column -> column.name.equalsIgnoreCase(fieldNameParts[1]))
                .findFirst()
                .orElse(null);
        if (viewColumn == null) {
            log.error("Unknown column for view {}: {}", fieldNameParts[0], fieldNameParts[1]);
            log.error(
                    "Expected one of {}",
                    config.columns.stream().map(column -> column.name).collect(Collectors.joining(", ")));
            throw new IllegalArgumentException(
                    "Unknown column for view " + fieldNameParts[0] + ": " + fieldNameParts[1]);
        }
        return viewColumn;
    }

    void prepareFilters(List<ViewFilter> filters) throws SQLException {
        for (var filter : filters) {
            if (filter.getField().equalsIgnoreCase("location")) {
                filter.setField("Resource");
                filter.setPrefixes(filter.values.stream().map(Object::toString).collect(Collectors.toList()));
                filter.setValues(null);
            }
            var column = checkField(filter.getField());
            if (column == null) {
                continue;
            }
            if (EnumSet.of(ColumnType.Term, ColumnType.TermSet).contains(column.type)) {
                var labelIds = filter.values.stream().map(Object::toString).toList();
                filter.values = getLabelsByIds(labelIds);
            } else if (column.type == Date) {
                if (filter.min != null) {
                    filter.min = Instant.parse(filter.min.toString());
                }
                if (filter.max != null) {
                    filter.max = Instant.parse(filter.max.toString());
                }
            }
        }
    }

    String escapeLikeString(String value) {
        return value.replaceAll("\\[", "\\[")
                .replaceAll("]", "\\]")
                .replaceAll("_", "\\_")
                .replaceAll("%", "\\%")
                .replaceAll("\\\\", "\\\\");
    }

    String sqlConstraint(String fieldName, ViewFilter filter, List<Object> values) {
        if (fieldName.isBlank()) {
            fieldName = "id";
        }
        if (filter.getValues() != null && !filter.getValues().isEmpty()) {
            if (filter.getValues().size() == 1) {
                values.add(filter.getValues().get(0));
                return String.format("%s = ?", fieldName);
            }
            return String.format(
                    "%s in ( %s )",
                    fieldName,
                    filter.getValues().stream()
                            .map(value -> {
                                values.add(value);
                                return "?";
                            })
                            .collect(Collectors.joining(", ")));
        }
        var constraints = new ArrayList<String>();
        // NOTE: for NUMERIC filter, turned out, PreparedStatement may cast them as real or double,
        // what causes a dramatic slow down for the query execution (up to x100).
        // As a workaround, we force PrepareStatement to send the request with numeric type (the same as it is defined
        // in the database schema)
        var isNumericFilter = filter.numericValue != null && filter.numericValue;
        if (filter.getMin() != null) {
            values.add(filter.getMin());
            var constraint = isNumericFilter ? " >= ?::numeric" : " >= ?";
            constraints.add(fieldName + constraint);
        }
        if (filter.getMax() != null) {
            values.add(filter.getMax());
            var constraint = isNumericFilter ? " <= ?::numeric" : " <= ?";
            constraints.add(fieldName + constraint);
        }
        if (filter.getPrefix() != null && !filter.getPrefix().isBlank()) {
            // Use view label instead of id for prefix filters
            String prefixFieldName = fieldName;
            if (prefixFieldName.endsWith(".id")) {
                prefixFieldName = prefixFieldName.replaceAll("\\.id$", ".label");
            }
            values.add(escapeLikeString("%" + filter.getPrefix().trim().toLowerCase()) + "%");
            constraints.add(prefixFieldName + " ilike ? escape '\\'");
        }
        if (filter.getPrefixes() != null && !filter.getPrefixes().isEmpty()) {
            String finalFieldName = fieldName;
            var prefixes = filter.getPrefixes().stream()
                    .map(prefix -> {
                        values.add(escapeLikeString(prefix) + "%");
                        return finalFieldName + " like ? escape '\\'";
                    })
                    .collect(Collectors.joining(" or "));
            constraints.add("(" + prefixes + ")");
        }
        if (filter.booleanValue != null) {
            if (filter.booleanValue) {
                constraints.add("(" + fieldName + "=true)");
            } else {
                constraints.add("(" + fieldName + "=false OR " + fieldName + " IS NULL)");
            }
        }
        if (!constraints.isEmpty()) {
            return String.join(" and ", constraints);
        }
        return null;
    }

    String sqlFilter(String alias, View view, List<ViewFilter> filters, List<Object> values) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        return filters.stream()
                .map(filter -> {
                    var field = filter.getField().equalsIgnoreCase(view.name)
                            ? "id"
                            : filter.getField().split("_")[1].toLowerCase();
                    var column = view.columns.stream()
                            .filter(c -> c.name.equalsIgnoreCase(field))
                            .findFirst()
                            .orElse(null);
                    if (column != null && column.type.isSet()) {
                        var propertyTable =
                                configuration.propertyTables.get(view.name).get(column.name);
                        var subConstraint = sqlConstraint("pt." + field, filter, values);
                        return "exists (select *"
                                + " from " + propertyTable.name + " pt "
                                + " where pt." + idColumn(view.name).name + " = " + alias + ".id"
                                + (subConstraint == null || subConstraint.isBlank() ? "" : " and " + subConstraint)
                                + ")";
                    }
                    return sqlConstraint(alias + "." + field, filter, values);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" and "));
    }

    PreparedStatement query(Connection connection, String view, List<ViewFilter> filters, String scope, boolean isCount)
            throws SQLException {
        if (filters == null) {
            filters = Collections.emptyList();
        }
        prepareFilters(filters);
        var filtersByView = filters.stream()
                .collect(Collectors.groupingBy(
                        filter -> filter.getField().toLowerCase().split("_")[0]));
        var values = new ArrayList<>();
        var constraints =
                sqlFilter("v", configuration.viewConfig.get(view), filtersByView.get(view.toLowerCase()), values);
        var subqueries = filtersByView.entrySet().stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase(view))
                .map(entry -> {
                    String resultCondition;
                    var subView = entry.getKey();
                    if (configuration.joinTables.get(view) == null
                            || !configuration.joinTables.get(view).containsKey(subView)) {
                        resultCondition = "false"; // to return no rows
                    } else {
                        var subConstraints =
                                sqlFilter("jv", configuration.viewConfig.get(subView), entry.getValue(), values);
                        var subViewTable = configuration.viewTables.get(subView);
                        var joinTable = configuration.joinTables.get(view).get(subView);
                        resultCondition = "exists (select *" + " from "
                                + joinTable.name + " jt " + " join "
                                + subViewTable.name + " jv " + " on jv.id = jt."
                                + idColumn(subView).name + " where jt."
                                + idColumn(view).name + " = v.id"
                                + (subConstraints.isBlank() ? "" : " and " + subConstraints)
                                + ")";
                    }
                    return resultCondition;
                })
                .toList();
        constraints = Stream.concat(Stream.of(constraints), subqueries.stream())
                .filter(constraint -> constraint != null && !constraint.isBlank())
                .collect(Collectors.joining(" and "));

        var viewTable = configuration.viewTables.get(view);
        String query = "select %s from "
                + viewTable.name + " v " + (constraints.isBlank() ? "" : " where " + constraints)
                + (scope == null ? "" : (" " + scope));

        query = isCount ? transformToCountQuery(view, query) : query.formatted("*");
        var preparedStatement = connection.prepareStatement(query);
        for (var i = 0; i < values.size(); i++) {
            var value = values.get(i);
            if (value instanceof Number) {
                preparedStatement.setFloat(i + 1, ((Number) value).floatValue());
            } else if (value instanceof Instant) {
                preparedStatement.setTimestamp(i + 1, Timestamp.from((Instant) value));
            } else {
                preparedStatement.setString(i + 1, value.toString());
            }
        }
        log.debug("Query: {}", preparedStatement.toString());
        return preparedStatement;
    }

    private String transformToCountQuery(String viewName, String query) {
        boolean isCountLimitDefined = viewsProperties
                .getViewConfig(viewName)
                .map(c -> c.maxDisplayCount != null)
                .orElse(false);
        if (isCountLimitDefined) { // limit defined
            query = query.formatted("*"); // set projection to *
            // limitation is applied to improve performance
            // we just set limit for the query and then wrap with count query
            // on UI user either exact number if less the limit or "more than 'limit'" if more
            query = "select count(*) as rowCount from ( " + query + " limit %s) as count";
            query = query.formatted(viewsProperties
                    .getViewConfig(viewName)
                    .map(c -> c.maxDisplayCount)
                    .orElseThrow());
        } else {
            query = query.formatted("count(*) as rowCount");
        }
        return query;
    }

    Map<String, ViewRow> retrieveViewTableRows(String view, List<ViewFilter> filters, int offset, int limit)
            throws SQLException {
        var viewConfig = configuration.viewConfig.get(view);
        if (viewConfig == null) {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        // retrieve view rows with fields from the view table only (not of the Set type)
        var rowsById = getViewRowsForNonSetType(viewConfig, filters, offset, limit);

        if (!rowsById.isEmpty()) {
            // TODO: with materialized or normal view we can retrieve all data in one go adding one more join in the
            // query above
            // retrieve view rows with fields from table only (of the Set type only)
            String[] viewIds = rowsById.keySet().toArray(new String[0]);
            var valueSetProperties = viewConfig.columns.stream()
                    .filter(column -> column.type.isSet())
                    .map(column -> column.name)
                    .toList();
            if (!valueSetProperties.isEmpty()) {
                var viewRowsForSetType = getViewRowsForSetType(view, valueSetProperties, viewIds);
                // merge the data from the view (for instance, Study) table and related tables (for instance,
                // Study_TreatmentId)
                viewRowsForSetType.forEach((key, value) -> rowsById.get(key).merge(value));
            }
        }
        return rowsById;
    }

    private Map<String, ViewRow> getViewRowsForNonSetType(View view, List<ViewFilter> filters, int offset, int limit)
            throws SQLException {
        try (var connection = viewStoreClientFactory.getConnection();
                var query = query(
                        connection,
                        view.name,
                        filters,
                        String.format(
                                "order by id %s limit %d", offset > 0 ? String.format("offset %d", offset) : "", limit),
                        false)) {
            query.setQueryTimeout(searchProperties.getPageRequestTimeout());
            var result = query.executeQuery();
            Map<String, ViewRow> rowsById = new HashMap<>();
            while (result.next()) {
                var row = transformRow(view, result);
                rowsById.put(result.getString("id"), new ViewRow(row));
            }
            return rowsById;
        }
    }

    private Map<String, ViewRow> getViewRowsForSetType(String view, List<String> valueSetProperties, String[] viewIds)
            throws SQLException {

        var columns = String.join(", ", valueSetProperties);
        var query = "select %sid, %s from mv_%s where %sid = ANY(?::text[])".formatted(view, columns, view, view);

        try (var connection = viewStoreClientFactory.getConnection();
                var ps = connection.prepareStatement(query)) {
            Array array = ps.getConnection().createArrayOf("text", viewIds);
            ps.setArray(1, array);
            ResultSet resultSet = ps.executeQuery();

            Map<String, ViewRow> viewRowsForSetTypeById = new HashMap<>();

            while (resultSet.next()) {
                var id = resultSet.getString(view + "id");
                var newViewRow = ViewRow.viewSetOf(resultSet, valueSetProperties, view);
                viewRowsForSetTypeById.compute(
                        id, (studyId, viewRow) -> viewRow == null ? newViewRow : viewRow.merge(newViewRow));
            }
            return viewRowsForSetTypeById;
        }
    }

    private ViewRowCollection retrieveJoinTableRows(String view, View.JoinView joinView, Collection<String> ids)
            throws SQLException {

        var joinedTable = configuration.viewTables.get(joinView.view);
        var viewIdColumn = idColumn(view).name;
        var joinIdColumn = idColumn(joinView.view).name;
        var projectionColumns = Stream.concat(
                        Stream.of(joinView.view + "_id", joinView.view + "_label"),
                        joinView.include.stream().map(i -> joinView.view + "_" + i))
                .collect(Collectors.toSet());

        var rows = new ViewRowCollection(searchProperties.getMaxJoinItems());

        if (!ids.isEmpty()) {
            try (var connection = viewStoreClientFactory.getConnection();
                    var query = getJoinQuery(connection, view, joinedTable, ids);
                    var result = query.executeQuery()) {
                while (result.next()) {
                    var id = result.getString(viewIdColumn);
                    var row = buildJoinRows(joinView, joinIdColumn, projectionColumns, result);
                    rows.add(id, row);
                }
            }
        }

        return rows;
    }

    private ViewRow buildJoinRows(
            View.JoinView joinView, String joinViewIdName, Set<String> projectionColumns, ResultSet result)
            throws SQLException {

        var row = new ViewRow();
        var joinViewId = result.getString(joinViewIdName);
        if (joinViewId != null) { // could be null as we do the left join for join views
            row.put(
                    joinView.view,
                    Sets.newHashSet(new ValueDto(
                            result.getString(joinView.view + "_label"), result.getString(joinViewIdName))));
            for (var column : projectionColumns) {
                var columnDefinition = Optional.ofNullable(
                                configuration.viewTables.get(joinView.view).getColumn(column.toLowerCase()))
                        // to support Set/TermSet types which does not have column definition out of the views.yaml
                        // todo: find a better way to aggregate together set and non-set column types
                        .orElse(Table.ColumnDefinition.builder().name(column).build());
                parseAndSetValueForColumn(result, columnDefinition, row);
            }
        }
        return row;
    }

    private static void parseAndSetValueForColumn(
            ResultSet result, Table.ColumnDefinition columnDefinition, ViewRow row) throws SQLException {
        if (columnDefinition.type == ColumnType.Number) {
            var value = result.getBigDecimal(columnDefinition.name);
            if (value != null) {
                row.put(columnDefinition.name, Sets.newHashSet(new ValueDto(value.toString(), value)));
            }
        } else if (columnDefinition.type == Date) {
            var value = result.getTimestamp(columnDefinition.name);
            if (value != null) {
                row.put(
                        columnDefinition.name,
                        Sets.newHashSet(new ValueDto(value.toInstant().toString(), value.toString())));
            }
        } else {
            var label = result.getString(columnDefinition.name);
            if (label != null) {
                row.put(columnDefinition.name, Sets.newHashSet(new ValueDto(label, label)));
            }
        }
    }

    private PreparedStatement getJoinQuery(
            Connection connection, String view, Table joinedTable, Collection<String> ids) throws SQLException {
        var query = "select * from mv_%s_join_%s where %s = ANY(?::text[])"
                .formatted(view, joinedTable.name, idColumn(view).name);
        var ps = connection.prepareStatement(query);
        var array = ps.getConnection().createArrayOf("text", ids.toArray());
        ps.setArray(1, array);
        return ps;
    }

    /**
     * Compute the range of numerical or date values in a column of a view.
     *
     * @param view   the view name.
     * @param column the column name.
     * @return a range object containing the minimum and maximum values.
     */
    public Range aggregate(String view, String column) {
        var viewConfig = configuration.viewConfig.get(view);
        if (viewConfig == null) {
            throw new IllegalArgumentException("View not supported: " + view);
        }
        var table = configuration.viewTables.get(view);
        var columnDefinition = table.getColumn(column.toLowerCase());
        try (var connection = viewStoreClientFactory.getConnection();
                var query = connection.prepareStatement("select min(" + columnDefinition.name + ") as min, max("
                        + columnDefinition.name + ") as max" + " from " + table.name)) {
            var result = query.executeQuery();
            if (!result.next()) {
                return null;
            }
            Object min;
            Object max;
            if (columnDefinition.type == Date) {
                min = result.getTimestamp("min");
                max = result.getTimestamp("max");
            } else {
                min = result.getBigDecimal("min");
                max = result.getBigDecimal("max");
            }
            return new Range(min, max);
        } catch (SQLException e) {
            throw new QueryException("Error aggregating column values", e);
        }
    }

    /**
     * Reads rows from a view table after applying the specified filters.
     * A row is represented as a map from column name to the set of values,
     * as there may be multiple values in a cell.
     *
     * @param view               the name of the view.
     * @param filters            the filters to apply.
     * @param offset             the index (zero-based) of the first row to include (for pagination)
     * @param limit              the maximum number of results to return.
     * @param includeJoinedViews if true, include joined views in the resulting rows.
     * @return the list of rows.
     */
    public List<Map<String, Set<ValueDto>>> retrieveRows(
            String view, List<ViewFilter> filters, int offset, int limit, boolean includeJoinedViews) {
        try {
            var viewConfig = configuration.viewConfig.get(view);
            if (viewConfig == null) {
                throw new IllegalArgumentException("View not supported: " + view);
            }
            // Fetch rows with columns from the view table
            var rowsById = this.retrieveViewTableRows(view, filters, offset, limit);

            // Add items from join tables
            if (includeJoinedViews && !rowsById.isEmpty()) {
                for (var joinView : viewConfig.join) {
                    var allJoinTableRows = this.retrieveJoinTableRows(view, joinView, rowsById.keySet());
                    rowsById.forEach((id, row) -> addJoinTableRowsForRow(row, allJoinTableRows.getRowsForId(id)));
                }
            }
            return rowsById.values().stream().map(ViewRow::getRawData).toList();
        } catch (SQLException e) {
            throw new QueryException("Error retrieving page rows", e);
        }
    }

    private void addJoinTableRowsForRow(ViewRow viewRow, List<ViewRow> allJoinTableRows) {
        allJoinTableRows.forEach(viewRow::merge);
    }

    public long countRows(String view, List<ViewFilter> filters) throws SQLTimeoutException {
        try (var connection = viewStoreClientFactory.getConnection();
                var q = query(connection, view, filters, null, true)) {
            q.setQueryTimeout(searchProperties.getCountRequestTimeout());
            var result = q.executeQuery();
            result.next();
            return result.getLong("rowCount");
        } catch (SQLTimeoutException e) {
            throw e;
        } catch (SQLException e) {
            throw new QueryException("Error counting rows", e);
        }
    }

    public List<SearchResultDto> searchFiles(FileSearchRequest request, List<String> userCollections) {
        if (userCollections == null || userCollections.isEmpty()) {
            return Collections.emptyList();
        }

        var searchString = "%" + escapeLikeString(request.getQuery().toLowerCase()) + "%";

        var values = new ArrayList<String>();
        values.add(searchString);
        values.add(searchString);
        values.addAll(userCollections);

        var collectionPlaceholders = userCollections.stream().map(uc -> "?").collect(Collectors.toList());
        var collectionConstraint = "and collection in (" + String.join(", ", collectionPlaceholders) + ") ";

        var idConstraint = StringUtils.isBlank(request.getParentIRI())
                ? ""
                : "and id like '" + escapeLikeString(request.getParentIRI()) + "%' ";

        var queryString = new StringBuilder()
                .append("select id, label, description, type FROM resource ")
                .append("where (label ilike ? OR description ilike ?) ")
                .append(collectionConstraint)
                .append(idConstraint)
                .append("order by id asc limit 1000");

        try (var connection = viewStoreClientFactory.getConnection();
                var statement = connection.prepareStatement(queryString.toString())) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }

            statement.setQueryTimeout(searchProperties.getPageRequestTimeout());

            var result = statement.executeQuery();
            return convertResult(result);

        } catch (SQLException e) {
            log.error("Error searching files.", e);
            throw new RuntimeException("Error searching files.", e); // Terminates Saturn
        }
    }

    @SneakyThrows
    private List<SearchResultDto> convertResult(ResultSet resultSet) {
        var rows = new ArrayList<SearchResultDto>();
        while (resultSet.next()) {
            var row = SearchResultDto.builder()
                    .id(resultSet.getString("id"))
                    .label(resultSet.getString("label"))
                    .type(FS.NS + resultSet.getString("type"))
                    .comment(resultSet.getString("description"))
                    .build();

            rows.add(row);
        }
        return rows;
    }
}

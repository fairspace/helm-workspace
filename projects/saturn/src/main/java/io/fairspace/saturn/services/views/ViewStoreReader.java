package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.View;
import io.fairspace.saturn.mapper.*;
import io.fairspace.saturn.services.search.FileSearchRequest;
import io.fairspace.saturn.services.search.SearchResultDTO;
import io.fairspace.saturn.vocabulary.FS;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static io.fairspace.saturn.config.ViewsConfig.ColumnType.Date;

/**
 * Executes SQL queries via JDBC on the view database
 * to obtain row data or aggregate data for a specified view
 * while applying specified filters.
 * Access to collections is not checked here. Restricting results
 * to only allowed collections can be achieved by providing these
 * collections in a filter with 'Resource_collection' as field.
 */
@Slf4j
public class ViewStoreReader implements AutoCloseable {

    final Config.Search searchConfig;
    final ViewsConfig viewsConfig;
    final ViewStoreClient.ViewStoreConfiguration configuration;
    final Connection postgresConnection;
    final SqlSessionFactory clickhouseSessionFactory;

    public ViewStoreReader(
            Config.Search searchConfig, ViewsConfig viewsConfig, ViewStoreClientFactory viewStoreClientFactory)
            throws SQLException {
        this.searchConfig = searchConfig;
        this.viewsConfig = viewsConfig;
        this.configuration = viewStoreClientFactory.configuration;
        this.postgresConnection = viewStoreClientFactory.getPostgresConnection();
        this.clickhouseSessionFactory = viewStoreClientFactory.getSqlSessionFactory();
    }

    String escapeLikeString(String value) {
        return value.replaceAll("\\[", "\\[")
                .replaceAll("]", "\\]")
                .replaceAll("_", "\\_")
                .replaceAll("%", "\\%")
                .replaceAll("\\\\", "\\\\");
    }

    String iriForLabel(String type, String label) throws SQLException {
        try (var query = postgresConnection.prepareStatement("select id from label where type = ? and label = ?")) {
            query.setString(1, type);
            query.setString(2, label);
            var result = query.executeQuery();
            if (result.next()) {
                return result.getString("id");
            }
        }
        return null;
    }

    Map<String, Set<ValueDTO>> transformRow(View viewConfig,
                                            String viewName,
                                            List<ViewRelation> viewRelations,
                                            List<ViewAttributeString> stringAttributes,
                                            List<ViewAttributeInt> intAttributes,
                                            List<ViewAttributeDate> dateAttributes) throws SQLException {

        Map<String, String> stringColumnMap = stringAttributes != null ? stringAttributes.stream()
                .collect(Collectors.toMap(ViewAttributeString::getAttributeName, ViewAttributeString::getValue)) : Collections.emptyMap();
        Map<String, Integer> intColumnMap = intAttributes != null ? intAttributes.stream()
                .collect(Collectors.toMap(ViewAttributeInt::getAttributeName, ViewAttributeInt::getValue)) : Collections.emptyMap();
        Map<String, java.util.Date> dateColumnMap = dateAttributes != null ? dateAttributes.stream()
                .collect(Collectors.toMap(ViewAttributeDate::getAttributeName, ViewAttributeDate::getValue)) : Collections.emptyMap();

        Map<String, Set<ValueDTO>> row = new HashMap<>();
        row.put(
                viewConfig.name,
                Collections.singleton(new ValueDTO(viewName, viewName))
        );
        for (var viewColumn : viewConfig.columns) {
            if (viewColumn.type.isSet()) {
                continue;
            }
            var column = configuration.viewTables.get(viewConfig.name).getColumn(viewColumn.name.toLowerCase());
            var columnName = viewConfig.name + "_" + viewColumn.name;
            if (column.type == ViewsConfig.ColumnType.Number) {
                var value = intColumnMap.get(column.name);
                if (value != null) {
                    row.put(columnName, Collections.singleton(new ValueDTO(value.toString(), value.floatValue())));
                }
            } else if (column.type == Date) {
                var value = dateColumnMap.get(column.name);
                if (value != null) {
                    row.put(
                            columnName,
                            Collections.singleton(new ValueDTO(value.toInstant().toString(), value.toInstant())));
                }
            } else {
                var value = stringColumnMap.get(column.name);
                if (viewColumn.type == ViewsConfig.ColumnType.Term) {
                    row.put(
                            columnName,
                            Collections.singleton(new ValueDTO(value, iriForLabel(viewColumn.rdfType, value))));
                } else {
                    row.put(columnName, Collections.singleton(new ValueDTO(value, value)));
                }
            }
        }
        return row;
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
        try (PreparedStatement query = postgresConnection.prepareStatement("select min(" + columnDefinition.name
                + ") as min, max(" + columnDefinition.name + ") as max" + " from " + table.name)) {
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
    // TODO I believe that includeJoinedViews is not needed.
    public List<Map<String, Set<ValueDTO>>> retrieveRows(
            String view, List<ViewFilter> filters, int offset, int limit, boolean includeJoinedViews) throws SQLException {

        var viewConfig = configuration.viewConfig.get(view);
        if (viewConfig == null) {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        ViewQueryParameters filterParameters = new ViewQueryParameters(filters);

        String viewType = view.toLowerCase();
        try (SqlSession sqlSession = clickhouseSessionFactory.openSession()) {
            ViewMapper mapper = sqlSession.getMapper(ViewMapper.class);
            List<ViewRelation> viewRelations = mapper.selectParentViewRelationsPaginated(viewType, filterParameters, offset, limit);

            List<String> parentViewNames = viewRelations.stream()
                    .map(ViewRelation::getParentViewName)
                    .distinct()
                    .toList();
            List<ViewAttributeString> stringAttributes = mapper.selectStringViewAttributes(viewType, parentViewNames);
            List<ViewAttributeInt> intAttributes = mapper.selectIntViewAttributes(viewType, parentViewNames);
            List<ViewAttributeDate> dateAttributes = mapper.selectDateViewAttributes(viewType, parentViewNames);

            Map<String, List<ViewRelation>> viewRelationMap = viewRelations.stream()
                    .collect(Collectors.groupingBy(ViewRelation::getParentViewName));
            Map<String, List<ViewAttributeString>> stringAttributeMap = stringAttributes.stream()
                    .collect(Collectors.groupingBy(ViewAttributeString::getViewName));
            Map<String, List<ViewAttributeInt>> intAttributeMap = intAttributes.stream()
                    .collect(Collectors.groupingBy(ViewAttributeInt::getViewName));
            Map<String, List<ViewAttributeDate>> dateAttributeMap = dateAttributes.stream()
                    .collect(Collectors.groupingBy(ViewAttributeDate::getViewName));

            Map<String, ViewRow> rowsById = new HashMap<>();
            viewRelationMap.keySet().forEach((parentViewName) -> {
                try {
                    rowsById.put(
                        parentViewName,
                        new ViewRow(
                            transformRow(
                                viewConfig,
                                parentViewName,
                                viewRelationMap.get(parentViewName),
                                stringAttributeMap.get(parentViewName),
                                intAttributeMap.get(parentViewName),
                                dateAttributeMap.get(parentViewName)
                            )
                        )
                    );
                } catch (SQLException e) {
                    throw new QueryException("Error transforming row data", e);
                }
            });

            return rowsById.values().stream().map(ViewRow::getRawData).toList();
        }
    }

    public long countRows(String view, List<ViewFilter> filters) throws SQLTimeoutException {
        try (SqlSession sqlSession = clickhouseSessionFactory.openSession()) {
            ViewMapper mapper = sqlSession.getMapper(ViewMapper.class);
            return mapper.getTotalCount(view.toLowerCase(), new ViewQueryParameters(filters));
        }
    }

    public List<SearchResultDTO> searchFiles(FileSearchRequest request, List<String> userCollections) {
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

        try (var statement = postgresConnection.prepareStatement(queryString.toString())) {
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }

            statement.setQueryTimeout((int) searchConfig.pageRequestTimeout);

            var result = statement.executeQuery();
            return convertResult(result);

        } catch (SQLException e) {
            log.error("Error searching files.", e);
            throw new RuntimeException("Error searching files.", e); // Terminates Saturn
        }
    }

    @SneakyThrows
    private List<SearchResultDTO> convertResult(ResultSet resultSet) {
        var rows = new ArrayList<SearchResultDTO>();
        while (resultSet.next()) {
            var row = SearchResultDTO.builder()
                    .id(resultSet.getString("id"))
                    .label(resultSet.getString("label"))
                    .type(FS.NS + resultSet.getString("type"))
                    .comment(resultSet.getString("description"))
                    .build();

            rows.add(row);
        }
        return rows;
    }

    @Override
    public void close() throws Exception {
        postgresConnection.close();
    }
}

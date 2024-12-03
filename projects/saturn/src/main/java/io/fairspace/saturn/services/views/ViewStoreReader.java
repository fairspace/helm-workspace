package io.fairspace.saturn.services.views;

import io.fairspace.saturn.config.properties.SearchProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.config.properties.ViewsProperties.ColumnType;
import io.fairspace.saturn.config.properties.ViewsProperties.View;
import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.ValueDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.mapper.*;
import io.fairspace.saturn.vocabulary.FS;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.*;
import java.util.stream.Collectors;

import static io.fairspace.saturn.config.properties.ViewsProperties.ColumnType.Date;

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
    final Connection postgresConnection;

    @Autowired ViewMapper viewMapper;
    @Autowired LabelMapper labelMapper;

    public ViewStoreReader(
            SearchProperties searchProperties,
            ViewsProperties viewsProperties,
            ViewStoreClientFactory viewStoreClientFactory,
            ViewStoreClient.ViewStoreConfiguration configuration
        ) throws SQLException {
        this.searchProperties = searchProperties;
        this.viewsProperties = viewsProperties;
        this.configuration = configuration;
        this.postgresConnection = viewStoreClientFactory.getPostgresConnection();
    }

    String escapeLikeString(String value) {
        return value.replaceAll("\\[", "\\[")
                .replaceAll("]", "\\]")
                .replaceAll("_", "\\_")
                .replaceAll("%", "\\%")
                .replaceAll("\\\\", "\\\\");
    }

    String iriForLabel(String type, String label) {
        return labelMapper.getId(type, label);
    }

    Map<String, Set<ValueDto>> transformRow(View viewConfig,
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

        Map<String, Set<ValueDto>> row = new HashMap<>();
        row.put(
                viewConfig.name,
                Collections.singleton(new ValueDto(viewName, viewName))
        );
        for (var viewColumn : viewConfig.columns) {
            if (viewColumn.type.isSet()) {
                continue;
            }
            var column = configuration.viewTables.get(viewConfig.name).getColumn(viewColumn.name.toLowerCase());
            var columnName = viewConfig.name + "_" + viewColumn.name;
            if (column.type == ColumnType.Number) {
                var value = intColumnMap.get(column.name);
                if (value != null) {
                    row.put(columnName, Collections.singleton(new ValueDto(value.toString(), value.floatValue())));
                }
            } else if (column.type == Date) {
                var value = dateColumnMap.get(column.name);
                if (value != null) {
                    row.put(
                            columnName,
                            Collections.singleton(new ValueDto(value.toInstant().toString(), value.toInstant())));
                }
            } else {
                var value = stringColumnMap.get(column.name);
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

    /**
     * Compute the range of numerical or date values in a column of a view.
     *
     * @param viewType   the view type.
     * @param columnName the column name.
     * @return a range object containing the minimum and maximum values.
     */
    public Range aggregate(String viewType, String columnName) {
        var viewConfig = configuration.viewConfig.get(viewType);
        if (viewConfig == null) {
            throw new IllegalArgumentException("View not supported: " + viewType);
        }
        var table = configuration.viewTables.get(viewType);
        var columnDefinition = table.getColumn(columnName.toLowerCase());
        var dataType = columnDefinition.type;
        if (dataType != ColumnType.Number && dataType != Date) {
            throw new IllegalArgumentException("Column type not supported for min/max queries: " + dataType);
        }
        if (dataType == ColumnType.Number) {
            return viewMapper.selectNumericViewAttributeMinMax(viewType.toLowerCase(), columnName.toLowerCase());
        } else {
            return viewMapper.selectDateViewAttributeMinMax(viewType.toLowerCase(), columnName.toLowerCase());
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
    public List<Map<String, Set<ValueDto>>> retrieveRows(
            String view, List<ViewFilter> filters, int offset, int limit, boolean includeJoinedViews) throws Exception {

        var viewConfig = configuration.viewConfig.get(view);
        if (viewConfig == null) {
            throw new IllegalArgumentException("View not supported: " + view);
        }

        ViewQueryParameters filterParameters = new ViewQueryParameters(filters);

        String viewType = view.toLowerCase();
        List<ViewRelation> viewRelations = viewMapper.selectParentViewRelationsPaginated(viewType, filterParameters, offset, limit);

        List<String> parentViewNames = viewRelations.stream()
                .map(ViewRelation::getParentViewName)
                .distinct()
                .toList();
        List<ViewAttributeString> stringAttributes = viewMapper.selectStringViewAttributes(viewType, parentViewNames);
        List<ViewAttributeInt> intAttributes = viewMapper.selectIntViewAttributes(viewType, parentViewNames);
        List<ViewAttributeDate> dateAttributes = viewMapper.selectDateViewAttributes(viewType, parentViewNames);

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

    public long countRows(String view, List<ViewFilter> filters) throws SQLTimeoutException {
        return viewMapper.getTotalCount(view.toLowerCase(), new ViewQueryParameters(filters));
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

        try (var statement = postgresConnection.prepareStatement(queryString.toString())) {
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

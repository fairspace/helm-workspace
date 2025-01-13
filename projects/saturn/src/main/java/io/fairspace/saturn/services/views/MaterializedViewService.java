package io.fairspace.saturn.services.views;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.sql.DataSource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.config.properties.ViewsProperties;

@Slf4j
@Service
public class MaterializedViewService {

    private static final String INDEX_POSTFIX = "_idx";
    private static final String UNIQUE_INDEX_POSTFIX = "_unique_idx";
    private static final int FIRST_ROW_IDX = 1;

    private final DataSource dataSource;
    private final ViewStoreClient.ViewStoreConfiguration configuration;
    private final ViewsProperties viewsProperties;
    private final int maxJoinItems;

    public MaterializedViewService(
            DataSource dataSource,
            ViewStoreClient.ViewStoreConfiguration configuration,
            ViewsProperties viewsProperties,
            @Value("${application.search.maxJoinItems}") int maxJoinItems) {
        this.dataSource = dataSource;
        this.configuration = configuration;
        this.viewsProperties = viewsProperties;
        this.maxJoinItems = maxJoinItems;
    }

    public void createOrUpdateAllMaterializedViews() {
        try (var connection = dataSource.getConnection()) {
            for (var view : viewsProperties.views) {
                createOrUpdateViewMaterializedView(view, connection);
                createOrUpdateJoinMaterializedView(view, connection);
            }
        } catch (SQLException e) {
            log.error("Materialized view create/update failed", e);
            throw new RuntimeException(e);
        }
    }

    private void createOrUpdateViewMaterializedView(ViewsProperties.View view, Connection connection)
            throws SQLException {
        var setColumns =
                view.columns.stream().filter(column -> column.type.isSet()).toList();
        if (!setColumns.isEmpty()) {
            String viewName = view.name.toLowerCase();
            var mvName = "mv_%s".formatted(viewName);

            log.info("View materialized view {} create/update has started", mvName);
            // all checks and changes to be done in one transaction
            connection.setAutoCommit(false);
            try {
                List<String> columns = collectViewColumns(view);
                if (doesMaterializedViewExist(mvName, connection)) {
                    // 'refresh' requires unique index based on all columns, so we have to check if it exists
                    createMaterializedViewUniqueIndexIfNotExist(
                            viewName + UNIQUE_INDEX_POSTFIX, mvName, columns, connection);
                    refreshMaterializedView(mvName, connection);
                } else {
                    createViewMaterializedView(mvName, view, setColumns, connection);
                    createMaterializedViewIndex(viewName + INDEX_POSTFIX, mvName, viewName + "id", connection);
                    createMaterializedViewUniqueIndexIfNotExist(
                            viewName + UNIQUE_INDEX_POSTFIX, mvName, columns, connection);
                }
                connection.commit();
                log.info("View materialized view {} create/update has finished successfully", mvName);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void createOrUpdateJoinMaterializedView(ViewsProperties.View view, Connection connection)
            throws SQLException {
        for (ViewsProperties.View.JoinView joinView : view.join) {
            String viewName = view.name.toLowerCase();
            String joinViewName = joinView.view.toLowerCase();
            var mvName = "mv_%s_join_%s".formatted(viewName, joinViewName);

            log.info("Join materialized view {} create/update has started", mvName);
            // all checks and changes to be done in one transaction
            connection.setAutoCommit(false);
            try {
                List<String> columns = collectJoinColumns(view, joinView);
                if (doesMaterializedViewExist(mvName, connection)) {
                    // 'refresh' requires unique index based on all columns, so we have to check if it exists
                    createMaterializedViewUniqueIndexIfNotExist(
                            mvName + UNIQUE_INDEX_POSTFIX, mvName, columns, connection);
                    refreshMaterializedView(mvName, connection);
                } else {
                    createJoinMaterializedViews(view, joinView, connection);
                    createMaterializedViewIndex(
                            mvName + "_" + viewName + INDEX_POSTFIX, mvName, viewName + "_id", connection);
                    createMaterializedViewIndex(
                            mvName + "_" + joinViewName + INDEX_POSTFIX, mvName, joinViewName + "_id", connection);
                    createMaterializedViewUniqueIndexIfNotExist(
                            mvName + UNIQUE_INDEX_POSTFIX, mvName, columns, connection);
                }
                connection.commit();
                log.info("Join  materialized view {} create/update has finished successfully", mvName);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void refreshMaterializedView(String mvName, Connection connection) throws SQLException {
        var query = "REFRESH MATERIALIZED VIEW CONCURRENTLY %s".formatted(mvName);
        try (var ps = connection.prepareStatement(query)) {
            ps.execute();
        }
    }

    private void createMaterializedViewUniqueIndexIfNotExist(
            String idxName, String mvName, List<String> columns, Connection connection) throws SQLException {
        if (!doesIndexExist(mvName, idxName, connection)) {
            var joinedColumns = String.join(", ", columns);
            var query = "CREATE UNIQUE INDEX %s ON %s (%s)".formatted(idxName, mvName, joinedColumns);
            try (var ps = connection.prepareStatement(query)) {
                ps.execute();
            }
        }
    }

    private boolean doesMaterializedViewExist(String viewName, Connection connection) throws SQLException {
        var query = "SELECT EXISTS (SELECT 1 FROM pg_matviews WHERE matviewname = '%s')".formatted(viewName);
        try (var ps = connection.prepareStatement(query)) {
            var rs = ps.executeQuery();
            rs.next();
            return rs.getBoolean(FIRST_ROW_IDX);
        }
    }

    private boolean doesIndexExist(String mvName, String idxName, Connection connection) throws SQLException {
        var query = "SELECT EXISTS (SELECT * FROM pg_indexes WHERE tablename = '%s' AND indexname = '%s')"
                .formatted(mvName, idxName);
        try (var ps = connection.prepareStatement(query)) {
            var rs = ps.executeQuery();
            rs.next();
            return rs.getBoolean(FIRST_ROW_IDX);
        }
    }

    private void createViewMaterializedView(
            String viewOrTableName,
            ViewsProperties.View view,
            List<ViewsProperties.View.Column> setColumns,
            Connection connection)
            throws SQLException {
        String viewName = view.name.toLowerCase();
        var queryBuilder = new StringBuilder()
                .append("CREATE MATERIALIZED VIEW ")
                .append(viewOrTableName)
                .append(" AS ")
                .append("SELECT v.id AS ")
                .append(viewName)
                .append("id, ");
        for (int i = 0; i < setColumns.size(); i++) {
            queryBuilder
                    .append("i")
                    .append(i)
                    .append(".")
                    .append(setColumns.get(i).name.toLowerCase());
            if (i != setColumns.size() - 1) {
                queryBuilder.append(", ");
            }
        }
        queryBuilder.append(" FROM ").append(viewName).append(" v ");
        for (int i = 0; i < setColumns.size(); i++) {
            var alias = "i" + i;
            var columnName = setColumns.get(i).name.toLowerCase();
            queryBuilder
                    .append("LEFT JOIN ")
                    .append(viewName)
                    .append("_")
                    .append(columnName)
                    .append(" ")
                    .append(alias)
                    .append(" ON ")
                    .append("v.id = ")
                    .append(alias)
                    .append(".")
                    .append(viewName)
                    .append("_id ");
        }

        try (var ps = connection.prepareStatement(queryBuilder.toString())) {
            ps.execute();
        }
    }

    private void createMaterializedViewIndex(
            String indexName, String materializedViewName, String columnName, Connection connection)
            throws SQLException {
        var query = "CREATE INDEX %s on %s (%s)".formatted(indexName, materializedViewName, columnName);
        try (var ps = connection.prepareStatement(query)) {
            ps.execute();
            connection.commit();
        }
    }

    private void createJoinMaterializedViews(
            ViewsProperties.View view, ViewsProperties.View.JoinView joinView, Connection connection)
            throws SQLException {
        var viewTableName = view.name.toLowerCase();
        var joinTable = configuration.joinTables.get(view.name).get(joinView.view).name;
        var joinedTable = configuration.viewTables.get(joinView.view).name.toLowerCase();
        var viewIdColumn = viewTableName + "_id";
        var joinIdColumn = joinedTable + "_id";
        var joinLabelColumn = joinedTable + "_label";
        var queryBuilder = new StringBuilder()
                .append("create materialized view mv_")
                .append(viewTableName)
                .append("_join_")
                .append(joinedTable)
                .append(" as ")
                .append("with numbered_rows AS (select v.id  AS ")
                .append(viewIdColumn)
                .append(", ")
                .append("jt.")
                .append(joinIdColumn)
                .append(", ")
                .append("jt_0.label AS ")
                .append(joinLabelColumn)
                .append(", ");

        var columns = new ArrayList<>(List.of(viewIdColumn, joinIdColumn, joinLabelColumn));

        var tableAliases = new HashMap<String, String>();
        for (int i = 0; i < joinView.include.size(); i++) {
            var attr = joinView.include.get(i);
            if ("id".equalsIgnoreCase(attr)) {
                continue;
            }
            var propTable = configuration.propertyTables.get(joinView.view);
            var isOfSetType = propTable != null && propTable.containsKey(attr);
            tableAliases.put(joinedTable + "_" + attr.toLowerCase(), isOfSetType ? "jt_" + (i + 1) : "jt_0");
        }
        for (int i = 0; i < joinView.include.size(); i++) {
            var attr = joinView.include.get(i).toLowerCase();
            if ("id".equalsIgnoreCase(attr)) {
                continue;
            }
            var columnName = joinedTable + "_" + attr;
            queryBuilder
                    .append(tableAliases.get(columnName))
                    .append(".")
                    .append(attr)
                    .append(" AS ")
                    .append(columnName)
                    .append(", ");
            columns.add(columnName);
        }

        queryBuilder
                .append("row_number() over (partition by v.id) as rn ")
                .append("from ")
                .append(viewTableName)
                .append(" v ");

        queryBuilder
                .append("left join ")
                .append(joinTable)
                .append(" jt on v.id = jt.")
                .append(viewTableName)
                .append("_id ")
                .append("left join ")
                .append(joinedTable)
                .append(" jt_0 on jt_0.id = jt.")
                .append(joinedTable)
                .append("_id ");

        for (int i = 0; i < joinView.include.size(); i++) {
            var attr = joinView.include.get(i);
            var propTable = configuration.propertyTables.get(joinView.view);
            var isOfSetType = propTable != null && propTable.containsKey(attr);
            if (!isOfSetType) {
                continue;
            }
            var columnName = joinedTable + "_" + attr.toLowerCase();
            queryBuilder
                    .append("left join ")
                    .append(columnName)
                    .append(" ")
                    .append(tableAliases.get(columnName))
                    .append(" on ")
                    .append("jt.")
                    .append(joinedTable)
                    .append("_id = ")
                    .append(tableAliases.get(columnName))
                    .append(".")
                    .append(joinedTable)
                    .append("_id ");
        }

        queryBuilder.append(") ");

        queryBuilder
                .append("select ")
                .append(String.join(", ", columns))
                .append(" from numbered_rows where rn <= ")
                .append(maxJoinItems);

        try (var ps = connection.prepareStatement(queryBuilder.toString())) {
            ps.execute();
            connection.commit();
        }
    }

    private List<String> collectViewColumns(ViewsProperties.View view) {
        List<String> columns = view.columns.stream()
                .filter(column -> column.type.isSet())
                .map(column -> column.name.toLowerCase())
                .toList();
        columns = new ArrayList<>(columns);
        columns.add(view.name.toLowerCase() + "id");
        return columns;
    }

    private List<String> collectJoinColumns(ViewsProperties.View view, ViewsProperties.View.JoinView joinView) {
        var viewTableName = view.name.toLowerCase();
        var joinedTable = configuration.viewTables.get(joinView.view).name.toLowerCase();
        var viewIdColumn = viewTableName + "_id";
        var joinIdColumn = joinedTable + "_id";
        var joinLabelColumn = joinedTable + "_label";
        var columns = new ArrayList<>(List.of(viewIdColumn, joinIdColumn, joinLabelColumn));
        for (int i = 0; i < joinView.include.size(); i++) {
            var attr = joinView.include.get(i).toLowerCase();
            if (!"id".equalsIgnoreCase(attr)) {
                columns.add(joinedTable + "_" + attr);
            }
        }
        return columns;
    }
}

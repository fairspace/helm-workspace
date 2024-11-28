package io.fairspace.saturn.services.views;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.services.views.Table.idColumn;
import static io.fairspace.saturn.services.views.Table.valueColumn;
import static io.fairspace.saturn.services.views.ViewStoreClientFactory.protectedResources;

@Slf4j
public class ViewUpdater implements AutoCloseable {

    private final ViewsProperties viewsProperties;
    private final ViewStoreClient viewStoreClient;
    private final DatasetGraph dsg;
    private final Graph graph;
    private final String publicUrl;

    public ViewUpdater(
            ViewsProperties viewsProperties, ViewStoreClient viewStoreClient, DatasetGraph dsg, String publicUrl) {
        this.viewsProperties = viewsProperties;
        this.viewStoreClient = viewStoreClient;
        this.dsg = dsg;
        this.graph = dsg.getDefaultGraph();
        this.publicUrl = publicUrl;
    }

    @Override
    public void close() throws SQLException {
        viewStoreClient.close();
    }

    public void commit() throws SQLException {
        viewStoreClient.commit();
    }

    private List<Node> retrieveValues(Graph graph, Node subject, String source) {
        var predicates = source.split("\\s+");
        var nodes = List.of(subject);
        for (var predicate : predicates) {
            var next = new ArrayList<Node>();
            var predicateNode = NodeFactory.createURI(predicate);
            for (var node : nodes) {
                if (node.isLiteral()) {
                    continue;
                }
                next.addAll(graph.find(node, predicateNode, Node.ANY)
                        .mapWith(Triple::getObject)
                        .toList());
            }
            nodes = next;
        }
        return nodes;
    }

    private static String getLabel(Graph graph, Node subject) {
        var labelNode = graph.find(subject, RDFS.label.asNode(), Node.ANY)
                .mapWith(Triple::getObject)
                .nextOptional()
                .orElse(null);
        if (labelNode == null) {
            return null;
        }
        return labelNode.getLiteral().toString(false);
    }

    public Object getValue(ViewsProperties.View.Column column, Node node) throws SQLException {
        return switch (column.type) {
            case Boolean, Number -> node.getLiteralValue();
            case Date -> {
                try {
                    if (node.getLiteralDatatypeURI().equals(XSD.dateTime.getURI())) {
                        yield Instant.parse(node.getLiteralValue().toString());
                    } else {
                        yield LocalDate.parse(node.getLiteralValue().toString());
                    }
                } catch (DateTimeException e) {
                    throw new SQLException("Failed to parse date value.", e);
                }
            }
            case Term, TermSet -> {
                var label = getLabel(graph, node);
                viewStoreClient.addLabel(node.getURI(), column.rdfType, label);
                yield label;
            }
            default -> {
                if (node.isLiteral()) {
                    yield node.getLiteralValue().toString();
                } else {
                    yield getLabel(graph, node);
                }
            }
        };
    }

    /**
     * If the subject is a protected resource, add the collection name to the row.
     * @param type The type IRI
     * @param subject The subject node
     * @param row the map to add the collection name to with key 'collection'.
     */
    private void addCollectionToProtectedResourceRow(String type, Node subject, Map<String, Object> row) {
        if (protectedResources.contains(type)) {
            // set collection name
            var rootLocation = publicUrl + "/api/webdav" + "/";
            if (subject.getURI().startsWith(rootLocation)) {
                var location = subject.getURI().substring(rootLocation.length());
                var collection = URLDecoder.decode(location.split("/")[0], StandardCharsets.UTF_8);
                row.put("collection", collection);
            }
        }
    }

    public void updateSubject(Node subject) {
        if (!subject.isURI()) {
            return;
        }
        var typeNode = graph.find(subject, RDF.type.asNode(), Node.ANY).nextOptional();
        if (typeNode.isEmpty()) {
            log.debug("Subject {} has no type!", subject.getURI());
            return;
        }
        var start = new Date().getTime();
        var type = typeNode.get().getObject();
        log.debug("Subject {} of type {}", subject.getURI(), type.getLocalName());
        viewsProperties.views.stream()
                .filter(view -> view.types.contains(type.getURI()))
                .forEach(view -> {
                    if (graph.find(subject, FS.dateDeleted.asNode(), Node.ANY).hasNext()) {
                        log.debug(
                                "Deleting entity {} of type {} from view {}",
                                subject.getURI(),
                                type.getLocalName(),
                                view.name);
                        try {
                            viewStoreClient.deleteRow(view.name, subject.getURI());
                        } catch (SQLException e) {
                            log.error("Failed to delete row from view", e);
                        }
                    } else {
                        log.debug(
                                "Updating entity {} of type {} in view {}",
                                subject.getURI(),
                                type.getLocalName(),
                                view.name);
                        var row = new HashMap<String, Object>();
                        row.put("id", subject.getURI());
                        row.put("label", getLabel(graph, subject));
                        addCollectionToProtectedResourceRow(type.getURI(), subject, row);
                        // Update subject value columns
                        try {
                            for (var column : view.columns) {
                                var objects = retrieveValues(graph, subject, column.source);
                                row.put(column.name, objects.isEmpty() ? null : getValue(column, objects.getFirst()));
                            }
                            viewStoreClient.updateRows(view.name, List.of(row), false);
                        } catch (SQLException e) {
                            log.error("Failed to update view row", e);
                        }
                        // Update subject value sets
                        for (ViewsProperties.View.Column column : view.columns) {
                            if (!column.type.isSet()) {
                                continue;
                            }
                            var objects = retrieveValues(graph, subject, column.source);
                            if (objects.isEmpty()) {
                                // continue;
                            }
                            try {
                                var values = new HashSet<String>();
                                for (var term : objects) {
                                    if (column.type == ViewsProperties.ColumnType.TermSet) {
                                        var label = getLabel(graph, term);
                                        viewStoreClient.addLabel(term.getURI(), column.rdfType, label);
                                        values.add(label);
                                    } else {
                                        values.add(term.getLiteralValue().toString());
                                    }
                                }
                                viewStoreClient.updateValues(view.name, subject.getURI(), column.name, values);
                            } catch (SQLException e) {
                                log.error("Failed to update view value sets", e);
                            }
                        }
                        // Update subject links
                        if (view.join != null) {
                            for (var joinView : view.join) {
                                var relation = NodeFactory.createURI(joinView.on);
                                var objects = joinView.reverse
                                        ? graph.find(Node.ANY, relation, subject)
                                                .mapWith(Triple::getSubject)
                                                .toList()
                                        : graph.find(subject, relation, Node.ANY)
                                                .mapWith(Triple::getObject)
                                                .toList();
                                if (objects.isEmpty()) {
                                    continue;
                                }
                                try {
                                    viewStoreClient.updateLinks(
                                            view.name,
                                            subject.getURI(),
                                            joinView.view,
                                            objects.stream().map(Node::getURI).collect(Collectors.toSet()));
                                } catch (SQLException e) {
                                    log.error("Failed to update view links", e);
                                }
                            }
                        }
                    }
                });
        log.debug("Updating subject of type {} took {}ms", type.getLocalName(), new Date().getTime() - start);
    }

    /**
     * Only use this method in a secure and synchonisized way, see 'MaintenanceService.recreateIndex()'
     */
    public void recreateIndexForView(ViewStoreClient viewStoreClient, ViewsProperties.View view) throws SQLException {
        // Clear database tables for view
        log.info("Recreating index for view {} started", view.name);
        viewStoreClient.truncateViewTables(view.name);
        for (String type : view.types) {
            copyValuesForType(view, type);
            var valueSetColumns =
                    view.columns.stream().filter(column -> column.type.isSet()).toList();
            for (var valueSetColumn : valueSetColumns) {
                copyValueSetsForColumn(view, type, valueSetColumn);
            }
            for (var join : view.join) {
                if (!join.reverse) {
                    copyLinks(view, type, join);
                }
            }
        }
        log.info("Recreating index for view {} finished", view.name);
    }

    private Map<String, Object> transformResult(
            String type, List<ViewsProperties.View.Column> columns, QuerySolution result) throws SQLException {
        var values = new HashMap<String, Object>();
        var subject = result.getResource("id");
        values.put("id", subject.getURI());
        values.put("label", result.getLiteral("label").toString());
        addCollectionToProtectedResourceRow(type, subject.asNode(), values);
        for (var column : columns) {
            var resultNode = result.get(column.name);
            if (resultNode != null) {
                values.put(column.name.toLowerCase(), getValue(column, resultNode.asNode()));
            }
        }
        return values;
    }

    /**
     * Copy rows of values for a specified type to the view database in bulk.
     *
     * All simple values (no value sets) for the type are queried and the rows are inserted
     * into the view database in batches of 1000 rows.
     *
     * @param view The view for which to update the values.
     * @param type The subject type (for when the view includes multiple types)
     */
    public void copyValuesForType(ViewsProperties.View view, String type) throws SQLException {
        var columns =
                view.columns.stream().filter(column -> !column.type.isSet()).collect(Collectors.toList());
        var attributes = columns.stream()
                .map(column -> "OPTIONAL {?id <" + column.source + "> " + "?" + column.name + " . }\n")
                .collect(Collectors.joining());
        var attributeNames = columns.stream().map(c -> "?" + c.name).collect(Collectors.joining(" "));

        var query =
                """
                    PREFIX rdfs: <%s>
                    SELECT ?id ?label %s
                    WHERE {
                        ?id a <%s> .
                        %s
                        ?id rdfs:label ?label .
                    }
                """
                        .formatted(RDFS.getURI(), attributeNames, type, attributes);

        var rows = new ArrayList<Map<String, Object>>();
        var error = new AtomicReference<SQLException>();
        final int[] updateCount = new int[1];
        SparqlUtils.querySelect(dsg, query, (QuerySolution q) -> {
            // read query results
            try {
                rows.add(transformResult(type, columns, q));
                // copy in chunks to the view database
                if (rows.size() == 1000) {
                    updateCount[0] += viewStoreClient.updateRows(view.name, rows, true);
                    rows.clear();
                }
            } catch (SQLException e) {
                error.set(e);
                throw new RuntimeException("Failed to bulk insert rows", e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        // copy any remaining items to the view database
        updateCount[0] += viewStoreClient.updateRows(view.name, rows, true);
        log.debug("Inserted {} rows for view {}", updateCount[0], view.name);
    }

    /**
     * Copy value sets for a specified type and property to the view database in bulk.
     *
     * All values for the type and property are queried and the (subject, value) tuples are inserted
     * into the view database in batches of 1000 tuples.
     *
     * @param view The view for which to update the value set property.
     * @param type The subject type (for when the view includes multiple types)
     * @param column The view column of value set property.
     */
    public void copyValueSetsForColumn(ViewsProperties.View view, String type, ViewsProperties.View.Column column)
            throws SQLException {
        var property = column.name;
        var propertyTable =
                viewStoreClient.getConfiguration().propertyTables.get(view.name).get(property);
        var idColumn = idColumn(view.name);
        var propertyColumn = valueColumn(column.name, ViewsProperties.ColumnType.Identifier);
        var predicate = Arrays.stream(column.source.split("\\s+"))
                .map("<%s>"::formatted)
                .collect(Collectors.joining("/"));
        var query =
                """
                    SELECT ?id ?%s
                    WHERE {
                        ?id a <%s> .
                        ?id %s ?%s .
                    }
                """
                        .formatted(property, type, predicate, property);
        var rows = new ArrayList<Pair<String, String>>();
        var error = new AtomicReference<SQLException>();
        final int[] updateCount = new int[1];
        SparqlUtils.querySelect(dsg, query, (QuerySolution q) -> {
            // read query results
            try {
                var val = getValue(column, q.get(column.name).asNode());
                if (val == null) {
                    throw new RuntimeException(
                            "Error querying view %s for type %s in column %s".formatted(view.name, type, column.name));
                }
                rows.add(Pair.of(q.getResource("id").getURI(), val.toString()));

                // copy in chunks to the view database
                if (rows.size() == 1000) {
                    updateCount[0] += viewStoreClient.insertValues(propertyTable, idColumn, propertyColumn, rows);
                    rows.clear();
                }
            } catch (SQLException e) {
                error.set(e);
                throw new RuntimeException("Failed to bulk insert rows", e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        // copy any remaining items to the view database
        updateCount[0] += viewStoreClient.insertValues(propertyTable, idColumn, propertyColumn, rows);
        log.debug("Inserted {} rows for property {} of view {}", updateCount[0], column.name, view.name);
    }

    /**
     * Copy view join links for a specified type and join relation to the view database in bulk.
     *
     * All join links for the type and join relation are queried and the (source, target) tuples are inserted
     * into the view database in batches of 1000 tuples.
     *
     * @param view The view for which to update the join links.
     * @param type The subject type (for when the view includes multiple types)
     * @param join The join relation.
     */
    public void copyLinks(ViewsProperties.View view, String type, ViewsProperties.View.JoinView join)
            throws SQLException {
        var joinTable =
                viewStoreClient.getConfiguration().joinTables.get(view.name).get(join.view);
        var idColumn = idColumn(view.name);
        var joinColumn = idColumn(join.view);
        var predicate =
                Arrays.stream(join.on.split("\\s+")).map("<%s>"::formatted).collect(Collectors.joining("/"));
        var query =
                """
                    SELECT ?source ?target
                    WHERE {
                        ?source a <%s> .
                        ?source %s ?target .
                    }
                """
                        .formatted(type, predicate);
        var rows = new ArrayList<Pair<String, String>>();
        var error = new AtomicReference<SQLException>();
        final int[] updateCount = new int[1];
        SparqlUtils.querySelect(dsg, query, (QuerySolution q) -> {
            // read query results
            try {
                rows.add(Pair.of(
                        q.getResource("source").getURI(),
                        q.getResource("target").getURI()));
                // copy in chunks to the view database
                if (rows.size() == 1000) {
                    updateCount[0] += viewStoreClient.insertValues(joinTable, idColumn, joinColumn, rows);
                    rows.clear();
                }
            } catch (SQLException e) {
                error.set(e);
                throw new RuntimeException("Failed to bulk insert rows", e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        // copy any remaining items to the view database
        updateCount[0] += viewStoreClient.insertValues(joinTable, idColumn, joinColumn, rows);
        log.debug("Inserted {} rows for join of view {} with view {}", updateCount[0], view.name, join.view);
    }
}

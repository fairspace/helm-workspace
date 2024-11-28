package io.fairspace.saturn.services.views;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import lombok.extern.log4j.Log4j2;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.sparql.expr.E_Equals;
import org.apache.jena.sparql.expr.E_GreaterThanOrEqual;
import org.apache.jena.sparql.expr.E_LessThanOrEqual;
import org.apache.jena.sparql.expr.E_LogicalAnd;
import org.apache.jena.sparql.expr.E_OneOf;
import org.apache.jena.sparql.expr.E_StrLowerCase;
import org.apache.jena.sparql.expr.E_StrStartsWith;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.config.properties.SearchProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.config.properties.ViewsProperties.ColumnType;
import io.fairspace.saturn.config.properties.ViewsProperties.View;
import io.fairspace.saturn.controller.dto.CountDto;
import io.fairspace.saturn.controller.dto.ValueDto;
import io.fairspace.saturn.controller.dto.ViewPageDto;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.rdf.ModelUtils.getResourceProperties;
import static io.fairspace.saturn.util.ValidationUtils.validateIRI;

import static java.time.Instant.ofEpochMilli;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.sparql.expr.NodeValue.makeBoolean;
import static org.apache.jena.sparql.expr.NodeValue.makeDate;
import static org.apache.jena.sparql.expr.NodeValue.makeDecimal;
import static org.apache.jena.sparql.expr.NodeValue.makeNode;
import static org.apache.jena.sparql.expr.NodeValue.makeString;
import static org.apache.jena.system.Txn.calculateRead;

@Log4j2
@Service
@Qualifier("sparqlQueryService")
public class SparqlQueryService implements QueryService {

    private static final String RESOURCES_VIEW = "Resource";

    private final SearchProperties searchProperties;
    private final JenaProperties jenaProperties;
    private final ViewsProperties viewsProperties;
    private final Dataset ds;
    private final Transactions transactions;

    public SparqlQueryService(
            SearchProperties searchProperties,
            JenaProperties jenaProperties,
            ViewsProperties viewsProperties,
            @Qualifier("filteredDataset") Dataset ds,
            Transactions transactions) {
        this.searchProperties = searchProperties;
        this.jenaProperties = jenaProperties;
        this.viewsProperties = viewsProperties;
        this.ds = ds;
        this.transactions = transactions;
    }

    /**
     * Execute a SPARQL query and return the results as a JSON string.
     */
    public String executeQuery(String sparqlQuery) {
        return transactions.calculateRead(model -> {
            Query query = QueryFactory.create(sparqlQuery, Syntax.syntaxARQ);
            try (QueryExecution queryExecution = QueryExecution.create()
                    .query(query)
                    .dataset(ds)
                    .timeout(jenaProperties.getSparqlQueryTimeout(), TimeUnit.MILLISECONDS)
                    .build()) {
                ResultSet resultSet = queryExecution.execSelect();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ResultSetFormatter.outputAsJSON(outputStream, resultSet);
                return outputStream.toString();
            } catch (Exception e) {
                log.error("Error executing query: \n{}", sparqlQuery, e);
                throw new RuntimeException(e);
            }
        });
    }

    public ViewPageDto retrieveViewPage(ViewRequest request) {
        var query = getQuery(request, false);

        log.debug("Executing query:\n{}", query);

        var page = (request.getPage() != null && request.getPage() >= 1) ? request.getPage() : 1;
        var size = (request.getSize() != null && request.getSize() >= 1) ? request.getSize() : 20;
        query.setLimit(size + 1);
        query.setOffset((page - 1) * size);

        log.debug("Query with filters and pagination applied: \n{}", query);

        try (var selectExecution = QueryExecution.create()
                .dataset(ds)
                .query(query)
                .timeout(searchProperties.getCountRequestTimeout())
                .build()) {
            return calculateRead(ds, () -> {
                var iris = new ArrayList<Resource>();
                var timeout = false;
                var hasNext = false;
                try (selectExecution) {
                    var rs = selectExecution.execSelect();
                    rs.forEachRemaining(row -> iris.add(row.getResource(request.getView())));
                } catch (QueryCancelledException e) {
                    timeout = true;
                }
                while (iris.size() > size) {
                    iris.remove(iris.size() - 1);
                    hasNext = true;
                }

                var rows = iris.stream()
                        .map(resource -> fetch(resource, request.getView()))
                        .collect(toList());

                return ViewPageDto.builder()
                        .rows(rows)
                        .hasNext(hasNext)
                        .timeout(timeout)
                        .build();
            });
        }
    }

    private Map<String, Set<ValueDto>> fetch(Resource resource, String viewName) {
        var view = getView(viewName);

        var result = new HashMap<String, Set<ValueDto>>();
        result.put(view.name, Set.of(toValueDTO(resource)));

        for (var c : view.columns) {
            result.put(viewName + "_" + c.name, getValues(resource, c));
        }
        for (var j : view.join) {
            var joinView = getView(j.view);

            var prop = createProperty(j.on);
            var refs = j.reverse
                    ? resource.getModel()
                            .listResourcesWithProperty(prop, resource)
                            .toList()
                    : getResourceProperties(resource, prop);

            for (var colName : j.include) {
                if (colName.equals("id")) {
                    result.put(
                            joinView.name, refs.stream().map(this::toValueDTO).collect(toSet()));
                } else {
                    var col = joinView.columns.stream()
                            .filter(c -> c.name.equals(colName))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Unknown column: " + colName));

                    var values = refs.stream()
                            .flatMap(ref -> getValues(ref, col).stream())
                            .collect(toCollection(TreeSet::new));

                    result.put(joinView.name + "_" + colName, values);
                }
            }
        }

        return result;
    }

    private Set<ValueDto> getValues(Resource resource, View.Column column) {
        return new TreeSet<>(resource.listProperties(createProperty(column.source))
                .mapWith(Statement::getObject)
                .mapWith(this::toValueDTO)
                .toSet());
    }

    private View getView(String viewName) {
        return viewsProperties
                .getViewConfig(viewName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown view: " + viewName));
    }

    private ValueDto toValueDTO(RDFNode node) {
        if (node.isLiteral()) {
            var value = node.asLiteral().getValue();
            if (value instanceof XSDDateTime) {
                value = ofEpochMilli(((XSDDateTime) value).asCalendar().getTimeInMillis());
            }
            return new ValueDto(value.toString(), value);
        }
        var resource = node.asResource();
        var label = resource.listProperties(RDFS.label)
                .nextOptional()
                .map(Statement::getString)
                .orElseGet(resource::getLocalName);

        return new ValueDto(label, resource.getURI());
    }

    private Query getQuery(CountRequest request, boolean isCount) {
        var view = getView(request.getView());

        var builder = new StringBuilder().append("SELECT %s \nWHERE {\n"); // prefix to be added later

        if (request.getFilters() != null) {
            buildAndAddFilterToQuery(request, view, builder);
        }

        // add type condition and check if the entity is not deleted
        builder.append("?")
                .append(view.name)
                .append(" a ?type .\nFILTER (?type IN (")
                .append(view.types.stream().map(t -> "<" + t + ">").collect(joining(", ")))
                .append("))\nFILTER NOT EXISTS { ?")
                .append(view.name)
                .append(" fs:dateDeleted ?any }\n}");

        var query = isCount
                ? transformToCountQuery(builder.toString(), view)
                : builder.toString().formatted("?" + view.name);

        // adding prefix at the top of the query
        // prefix cannot be added earlier due to transformation for the count query
        query = "PREFIX fs: <%s>\n\n".formatted(FS.NS) + query;

        return QueryFactory.create(query);
    }

    private void buildAndAddFilterToQuery(CountRequest request, View view, StringBuilder builder) {
        var filters = new ArrayList<>(request.getFilters());
        filters.stream().filter(f -> f.field.equals("location")).findFirst().ifPresent(locationFilter -> {
            filters.remove(locationFilter);

            if (locationFilter.values != null && !locationFilter.values.isEmpty()) {
                locationFilter.values.forEach(v -> validateIRI(v.toString()));
                var fileLink = view.join.stream()
                        .filter(v -> v.view.equals(RESOURCES_VIEW))
                        .findFirst()
                        .orElse(null);
                if (fileLink != null) {
                    builder.append("FILTER EXISTS {\n")
                            .append("?file fs:belongsTo* ?location .\n FILTER (?location IN (")
                            .append(locationFilter.values.stream()
                                    .map(v -> "<" + v + ">")
                                    .collect(joining(", ")))
                            .append("))\n ?file <")
                            .append(fileLink.on)
                            .append("> ?")
                            .append(view.name)
                            .append(" . \n")
                            .append("}\n");
                } else {
                    builder.append("?")
                            .append(view.name)
                            .append(" fs:belongsTo* ?location .\n FILTER (?location IN (")
                            .append(locationFilter.values.stream()
                                    .map(v -> "<" + v + ">")
                                    .collect(joining(", ")))
                            .append("))\n");
                }
            }
        });

        filters.stream()
                .map(f -> f.field)
                .sorted(comparing(field -> field.contains("_") ? getColumn(field).priority : 0))
                .map(field -> field.split("_")[0])
                .distinct()
                .forEach(entity -> {
                    if (!entity.equals(view.name)) {
                        builder.append("FILTER EXISTS {\n");

                        var join = view.join.stream()
                                .filter(j -> j.view.equals(entity))
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Unknown view: " + entity));

                        builder.append("?")
                                .append(view.name)
                                .append(join.reverse ? " ^<" : " <")
                                .append(join.on)
                                .append("> ?")
                                .append(join.view)
                                .append(" .\n");
                    }

                    request.getFilters().stream()
                            .filter(f -> f.getField().startsWith(entity))
                            .sorted(comparing(f -> f.field.contains("_") ? getColumn(f.field).priority : 0))
                            .forEach(f -> {
                                String condition, property, field;
                                if (f.getField().equals(entity)) {
                                    field = f.field + "_id";
                                    property = RDFS.label.toString();
                                    condition = toFilterString(f, ColumnType.Identifier, field);
                                } else {
                                    field = f.field;
                                    property = getColumn(f.field).source;
                                    condition = toFilterString(f, getColumn(f.field).type, f.field);
                                }
                                if (condition != null) {
                                    builder.append("?")
                                            .append(entity)
                                            .append(" <")
                                            .append(property)
                                            .append("> ?")
                                            .append(field)
                                            .append(" .\n")
                                            .append(condition)
                                            .append(" \n");
                                }
                            });
                    if (!entity.equals(view.name)) {
                        builder.append("}");
                    }
                    builder.append("\n");
                });
    }

    /**
     * The way count is calculated depends on the view configuration. If the view has a maxDisplayCount set,
     * the count query will be limited to that number of results. Otherwise, the count query will return the total
     */
    private String transformToCountQuery(String queryTemplate, View view) {
        String query;
        if (view.maxDisplayCount == null) {
            query = queryTemplate.formatted("(COUNT(?%s) AS ?count)".formatted(view.name));
        } else {
            query = new StringBuilder()
                    .append("SELECT (COUNT(?%s) AS ?count)".formatted(view.name))
                    .append("\nWHERE {\n{\n")
                    .append(queryTemplate.formatted("?" + view.name))
                    .append("\nLIMIT %d\n}\n}".formatted(view.maxDisplayCount))
                    .toString();
        }
        return query;
    }

    private String toFilterString(ViewFilter filter, ColumnType type, String field) {
        var variable = new ExprVar(field);

        Expr expr;
        if (filter.min != null && filter.max != null) {
            expr = new E_LogicalAnd(
                    new E_GreaterThanOrEqual(variable, toNodeValue(filter.min, type)),
                    new E_LessThanOrEqual(variable, toNodeValue(filter.max, type)));
        } else if (filter.min != null) {
            expr = new E_GreaterThanOrEqual(variable, toNodeValue(filter.min, type));
        } else if (filter.max != null) {
            expr = new E_LessThanOrEqual(variable, toNodeValue(filter.max, type));
        } else if (filter.values != null && !filter.values.isEmpty()) {
            List<Expr> values =
                    filter.values.stream().map(o -> toNodeValue(o, type)).collect(toList());
            expr = new E_OneOf(variable, new ExprList(values));
        } else if (filter.prefix != null && !filter.prefix.isBlank()) {
            expr = new E_StrStartsWith(
                    new E_StrLowerCase(variable),
                    makeString(filter.prefix.trim().toLowerCase()));
        } else if (filter.booleanValue != null) {
            expr = new E_Equals(variable, makeBoolean(filter.booleanValue));
        } else {
            return null;
        }

        return new ElementFilter(expr).toString();
    }

    private View.Column getColumn(String name) {
        var fieldNameParts = name.split("_");
        if (fieldNameParts.length != 2) {
            throw new IllegalArgumentException("Invalid field: " + name);
        }
        var viewConfig = getView(fieldNameParts[0]);
        return viewConfig.columns.stream()
                .filter(column -> column.name.equalsIgnoreCase(fieldNameParts[1]))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Unknown column for view {}: {}", fieldNameParts[0], fieldNameParts[1]);
                    log.error(
                            "Expected one of {}",
                            viewConfig.columns.stream()
                                    .map(column -> column.name)
                                    .collect(joining(", ")));
                    throw new IllegalArgumentException(
                            "Unknown column for view " + fieldNameParts[0] + ": " + fieldNameParts[1]);
                });
    }

    private static Calendar convertDateValue(String value) {
        var calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Instant.parse(value).toEpochMilli());
        return calendar;
    }

    private static NodeValue toNodeValue(Object o, ColumnType type) {
        return switch (type) {
            case Identifier, Term, TermSet -> makeNode(createURI(o.toString()));
            case Text, Set -> makeString(o.toString());
            case Number -> makeDecimal(o.toString());
                // Extract date only as it comes in format "yyyy-MM-ddTHH:mm:ss.SSSX"
                // Leave it as is in case of adding new DateTime filter format
            case Date -> makeDate(o.toString().split("T")[0]);
            case Boolean -> makeBoolean(convertBooleanValue(o.toString()));
        };
    }

    private static boolean convertBooleanValue(String value) {
        return Boolean.getBoolean(value);
    }

    public CountDto count(CountRequest request) {
        var query = getQuery(request, true);

        log.debug("Querying the total number of matches: \n{}", query);

        try (var execution = QueryExecution.create()
                .dataset(ds)
                .query(query)
                .timeout(searchProperties.getCountRequestTimeout())
                .build()) {

            return calculateRead(ds, () -> {
                var queryResult = execution.execSelect();
                if (queryResult.hasNext()) {
                    var row = queryResult.next();
                    var count = row.getLiteral("count").getLong();
                    return new CountDto(count, false);
                } else {
                    return new CountDto(0, false);
                }
            });
        } catch (QueryCancelledException e) {
            return new CountDto(0, true);
        }
    }
}

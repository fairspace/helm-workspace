package io.fairspace.saturn.rdf;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.log4j.Log4j2;
import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.update.UpdateExecutionFactory;
import org.apache.jena.update.UpdateFactory;

import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.controller.dto.SearchResultDto;

import static java.util.Optional.ofNullable;
import static java.util.UUID.randomUUID;
import static org.apache.jena.graph.NodeFactory.createURI;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;
import static org.apache.jena.system.Txn.calculateRead;
import static org.apache.jena.system.Txn.executeRead;
import static org.apache.jena.system.Txn.executeWrite;

@Log4j2
public class SparqlUtils {

    public static Node generateMetadataIri() {
        return generateMetadataIriFromId(randomUUID().toString());
    }

    public static Node generateMetadataIriFromId(String id) {
        return createURI(JenaProperties.getMetadataBaseIri() + id);
    }

    public static Node generateMetadataIriFromUri(String uri) {
        return createURI(uri);
    }

    public static Instant parseXSDDateTimeLiteral(Literal literal) {
        return Instant.ofEpochMilli(
                ((XSDDateTime) literal.getValue()).asCalendar().getTimeInMillis());
    }

    public static Literal toXSDDateTimeLiteral(Instant instant) {
        return createTypedLiteral(GregorianCalendar.from(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())));
    }

    // TODO: it should be a part of data layer, not utils
    /**
     * Execute a SELECT query and process the rows of the results with the handler code.
     */
    public static void querySelect(DatasetGraph dsg, String query, Consumer<QuerySolution> rowAction) {
        executeRead(dsg, () -> {
            try (var qExec = query(dsg, query)) {
                qExec.execSelect().forEachRemaining(rowAction);
            } catch (Exception e) {
                log.error("Error executing select query: \n %s".formatted(query), e);
                throw new RuntimeException(e);
            }
        });
    }

    public static void update(Dataset dataset, String updateString) {
        executeWrite(dataset, () -> UpdateExecutionFactory.create(UpdateFactory.create(updateString), dataset)
                .execute());
    }

    public static String getQueryRegex(String query) {
        return ("(^|\\s|\\.|\\-|\\,|\\;|\\(|\\[|\\{|\\?|\\!|\\\\|\\/|_)" + query.replaceAll("[^a-zA-Z0-9]", "\\\\$0"))
                .replace("/\\/g", "\\\\");
    }

    public static List<SearchResultDto> getByQuery(Query query, QuerySolutionMap binding, Dataset dataset) {
        log.debug("Executing query:\n{}", query);
        try (var selectExecution = QueryExecutionFactory.create(query, dataset, binding)) {
            var results = new ArrayList<SearchResultDto>();

            return calculateRead(dataset, () -> {
                try (selectExecution) {
                    for (var row : (Iterable<QuerySolution>) selectExecution::execSelect) {
                        var id = row.getResource("id").getURI();
                        var label = row.getLiteral("label").getString();
                        var type = ofNullable(row.getResource("type"))
                                .map(Resource::getURI)
                                .orElse(null);
                        var comment = ofNullable(row.getLiteral("comment"))
                                .map(Literal::getString)
                                .orElse(null);

                        var dto = SearchResultDto.builder()
                                .id(id)
                                .label(label)
                                .type(type)
                                .comment(comment)
                                .build();
                        results.add(dto);
                    }
                } catch (Exception e) {
                    String message = "Error executing select query: \n %s".formatted(query.toString());
                    log.error(message, e);
                    throw new RuntimeException(message, e);
                }
                return results;
            });
        }
    }

    private static QueryExecution query(DatasetGraph dsg, String query) {
        return QueryExecutionFactory.create(QueryFactory.create(query), dsg);
    }
}

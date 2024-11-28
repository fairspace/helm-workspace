package io.fairspace.saturn.services.search;

import java.util.List;

import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.SearchResultsDto;
import io.fairspace.saturn.controller.dto.request.LookupSearchRequest;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.vocabulary.FS;

import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;

@Log4j2
@Service
public class SearchService {
    private static final Query RESOURCE_BY_TEXT_QUERY = QueryFactory.create(String.format(
            """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX fs: <%1$s>

            SELECT ?id ?label ?comment
            WHERE {
                ?id a ?type ; rdfs:label ?label .
                OPTIONAL { ?id rdfs:comment ?comment }
                FILTER NOT EXISTS { ?id fs:dateDeleted ?anydate }
                FILTER (regex(?label, ?regexQuery, "i") || regex(?comment, ?regexQuery, "i"))
            } ORDER BY ?label
            LIMIT 20
            """,
            FS.NS));

    private static final Query RESOURCE_BY_TEXT_EXACT_MATCH_QUERY = QueryFactory.create(String.format(
            """
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX fs: <%1$s>

            SELECT ?id ?label ?comment
            WHERE {
                BIND(?query AS ?label)
                ?id rdfs:label ?label; a ?type .
                OPTIONAL { ?id rdfs:comment ?comment }
                FILTER NOT EXISTS { ?id fs:dateDeleted ?anydate }
            } ORDER BY ?label
            """,
            FS.NS));

    private final Dataset filteredDataset;

    public SearchService(@Qualifier("filteredDataset") Dataset filteredDataset) {
        this.filteredDataset = filteredDataset;
    }

    public SearchResultsDto getLookupSearchResults(LookupSearchRequest request) {
        return SearchResultsDto.builder()
                .results(getResourceByText(request))
                .query(request.getQuery())
                .build();
    }

    private List<SearchResultDto> getResourceByText(LookupSearchRequest request) {
        var binding = new QuerySolutionMap();
        binding.add("query", createStringLiteral(request.getQuery()));
        binding.add("type", createResource(request.getResourceType()));

        var results = SparqlUtils.getByQuery(RESOURCE_BY_TEXT_EXACT_MATCH_QUERY, binding, filteredDataset);
        if (!results.isEmpty()) {
            return results;
        }

        binding.add("regexQuery", createStringLiteral(SparqlUtils.getQueryRegex(request.getQuery())));
        return SparqlUtils.getByQuery(RESOURCE_BY_TEXT_QUERY, binding, filteredDataset);
    }
}

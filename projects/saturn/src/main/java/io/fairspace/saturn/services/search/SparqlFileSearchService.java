package io.fairspace.saturn.services.search;

import java.util.List;

import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolutionMap;

import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.rdf.SparqlUtils;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.util.ValidationUtils.validateIRI;

import static org.apache.jena.rdf.model.ResourceFactory.createStringLiteral;

@Log4j2
public class SparqlFileSearchService implements FileSearchService {
    private final Dataset ds;

    public SparqlFileSearchService(Dataset ds) {
        this.ds = ds;
    }

    public List<SearchResultDto> searchFiles(FileSearchRequest request) {
        var query = getSearchForFilesQuery(request.getParentIRI());
        var binding = new QuerySolutionMap();
        binding.add("regexQuery", createStringLiteral(SparqlUtils.getQueryRegex(request.getQuery())));
        return SparqlUtils.getByQuery(query, binding, ds);
    }

    private Query getSearchForFilesQuery(String parentIRI) {
        var builder = new StringBuilder("PREFIX fs: <")
                .append(FS.NS)
                .append(">\nPREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n\n")
                .append("SELECT ?id ?label ?comment ?type\n")
                .append("WHERE {\n");

        if (parentIRI != null && !parentIRI.trim().isEmpty()) {
            validateIRI(parentIRI);
            builder.append("?id fs:belongsTo* <").append(parentIRI).append("> .\n");
        }

        builder.append("?id rdfs:label ?label ; a ?type .\n")
                .append("FILTER (?type in (fs:File, fs:Directory, fs:Collection))\n")
                .append("OPTIONAL { ?id rdfs:comment ?comment }\n")
                .append("FILTER NOT EXISTS { ?id fs:dateDeleted ?anydate }\n")
                .append("FILTER (regex(?label, ?regexQuery, \"i\") || regex(?comment, ?regexQuery, \"i\"))\n")
                .append("}\nLIMIT 10000");

        return QueryFactory.create(builder.toString());
    }
}

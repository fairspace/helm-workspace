package io.fairspace.saturn.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.controller.validation.ValidSparqlReadQuery;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.views.SparqlQueryService;

import static io.fairspace.saturn.controller.enums.CustomMediaType.APPLICATION_SPARQL_QUERY;

@RestController
@RequestMapping("/rdf")
@Validated
@RequiredArgsConstructor
public class SparqlController {

    private final SparqlQueryService sparqlQueryService;

    private final MetadataPermissions metadataPermissions;

    /**
     * Execute a read-only SPARQL query.
     *
     * @param sparqlQuery the SPARQL query
     * @return the result of the query (JSON)
     */
    @PostMapping(value = "/query", consumes = APPLICATION_SPARQL_QUERY)
    // todo: uncomment the line below and remove the metadataPermissions.hasMetadataQueryPermission() call once
    //  the MetadataPermissions is available in the IoC container
    //  @PreAuthorize("@metadataPermissions.hasMetadataQueryPermission()")
    public ResponseEntity<String> executeSparqlQuery(@ValidSparqlReadQuery @RequestBody String sparqlQuery) {
        if (!metadataPermissions.hasMetadataQueryPermission()) {
            throw new AccessDeniedException("You do not have permission to execute SPARQL queries.");
        }
        var json = sparqlQueryService.executeQuery(sparqlQuery);
        return ResponseEntity.ok(json);
    }
}

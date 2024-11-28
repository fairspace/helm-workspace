package io.fairspace.saturn.services.views;

import io.fairspace.saturn.controller.dto.CountDto;
import io.fairspace.saturn.controller.dto.ViewPageDto;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;

/**
 * High-level interface for fetching metadata view pages and counts.
 * Implemented using Sparql queries on the RDF database directly
 * in {@link SparqlQueryService} and using a JDBC database connection
 * in {@link JdbcQueryService}.
 *
 * The implementing services are also responsible for restricting
 * the results to only those entities that the user has access to.
 * This means that query filters that refer to collections, and
 * queries on the <code>Resource</code> view (representing collections,
 * directories and files), need to be restricted to only those
 * collections the user has access to.
 */
public interface QueryService {
    ViewPageDto retrieveViewPage(ViewRequest request);

    CountDto count(CountRequest request);
}

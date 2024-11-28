package io.fairspace.saturn.auth;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.EqualsAndHashCode;
import org.apache.jena.graph.Node;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.fairspace.saturn.rdf.SparqlUtils;

public class RequestContext {

    private static final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

    private static final ThreadLocal<String> currentUserUri = new ThreadLocal<>();

    public static HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElseGet(currentRequest::get);
    }

    public static void setCurrentRequest(HttpServletRequest request) {
        currentRequest.set(request);
    }

    public static Optional<String> getCurrentUserStringUri() {
        if (currentUserUri.get() == null) {
            var uri = getUserURI();
            setCurrentUserStringUri(uri == null ? null : uri.getURI());
        }
        return Optional.ofNullable(currentUserUri.get());
    }

    public static void setCurrentUserStringUri(String uri) {
        currentUserUri.set(uri);
    }

    public static Node getUserURI() {
        return getJwt().map(JwtClaimAccessor::getSubject)
                .map(SparqlUtils::generateMetadataIriFromId)
                .or(() -> Optional.ofNullable(currentUserUri.get()).map(SparqlUtils::generateMetadataIriFromUri))
                .orElse(null);
    }

    public static SaturnClaims getClaims() {
        return getJwt().map(Jwt::getClaims).map(SaturnClaims::from).orElseGet(SaturnClaims::emptyClaims);
    }

    private static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication());
    }

    private static Optional<Jwt> getJwt() {
        return getAuthentication().map(Authentication::getPrincipal).map(Jwt.class::cast);
    }

    @EqualsAndHashCode
    public static class SaturnClaims {

        private static final String PREFERRED_USERNAME = "preferred_username";
        private static final String SUBJECT = "sub";
        private static final String EMAIL = "email";
        private static final String NAME = "name";

        private final Map<String, Object> claims;

        private SaturnClaims(Map<String, Object> claims) {
            this.claims = claims;
        }

        public static SaturnClaims from(Map<String, Object> claims) {
            return new SaturnClaims(claims);
        }

        public static SaturnClaims emptyClaims() {
            return new SaturnClaims(Map.of());
        }

        public String getPreferredUsername() {
            return (String) claims.get(PREFERRED_USERNAME);
        }

        public String getSubject() {
            return (String) claims.get(SUBJECT);
        }

        public String getEmail() {
            return (String) claims.get(EMAIL);
        }

        public String getName() {
            return (String) claims.get(NAME);
        }
    }
}

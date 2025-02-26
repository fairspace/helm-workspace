package io.fairspace.saturn.auth;

import java.util.Optional;

import org.apache.jena.graph.Node;
import org.junit.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import io.fairspace.saturn.TestUtils;

import static org.junit.Assert.*;

public class RequestContextTest {

    @Test
    public void getCurrentUserStringUri_shouldReturnUserUri() {
        String userUri = "http://example.com/user";
        RequestContext.setCurrentUserStringUri(userUri);
        assertEquals(Optional.of(userUri), RequestContext.getCurrentUserStringUri());
    }

    @Test
    public void getCurrentUserStringUri_shouldReturnEmptyWhenNoUriSet() {
        RequestContext.clear();
        assertEquals(Optional.empty(), RequestContext.getCurrentUserStringUri());
    }

    @Test
    public void getUserURI_shouldReturnUserUriFromJwt() {
        TestUtils.mockAuthentication("testUser");
        Node userUri = RequestContext.getUserURI();
        assertNotNull(userUri);
    }

    @Test
    public void getUserURI_shouldReturnNullWhenNoJwt() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
        assertNull(RequestContext.getUserURI());
    }

    @Test
    public void getClaims_shouldReturnClaimsFromJwt() {
        TestUtils.mockAuthentication("testUser");
        RequestContext.SaturnClaims claims = RequestContext.getClaims();
        assertEquals("testUser", claims.getSubject());
    }

    @Test
    public void getClaims_shouldReturnEmptyClaimsWhenNoJwt() {
        SecurityContextHolder.clearContext();
        RequestContext.SaturnClaims claims = RequestContext.getClaims();
        assertNull(claims.getSubject());
    }
}

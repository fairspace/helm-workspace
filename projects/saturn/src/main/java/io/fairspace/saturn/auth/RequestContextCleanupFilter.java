package io.fairspace.saturn.auth;

import java.io.IOException;

import jakarta.servlet.*;

public class RequestContextCleanupFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clear();
        }
    }
}

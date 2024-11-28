package io.fairspace.saturn.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String LOGIN_PATH = "/login";

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            if (request.getCookies() == null || request.getCookies().length == 0) {
                response.addHeader("WWW-Authenticate", "Basic");
            }
        } else {
            response.sendRedirect(LOGIN_PATH);
        }
    }
}

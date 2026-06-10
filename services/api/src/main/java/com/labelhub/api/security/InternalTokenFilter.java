package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;

    public InternalTokenFilter(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(request.getContextPath() + "/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        String actual = request.getHeader("X-Internal-Token");
        String configured = properties.getInternalToken();
        if (configured == null || configured.isBlank() || actual == null
            || !MessageDigest.isEqual(
                   configured.getBytes(StandardCharsets.UTF_8),
                   actual.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

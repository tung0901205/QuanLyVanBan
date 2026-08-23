package com.qlda.aiservice.security;

import com.qlda.aiservice.config.InternalAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private final InternalAuthProperties properties;

    public InternalServiceAuthenticationFilter(InternalAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String serviceName = request.getHeader("X-Service-Name");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing internal service token");
            return;
        }
        String expected = properties.getServiceToken();
        String actual = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(expected) || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token");
            return;
        }
        if (!StringUtils.hasText(serviceName) || !properties.getAllowedServices().contains(serviceName)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Calling service is not allowed");
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
            serviceName, null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}

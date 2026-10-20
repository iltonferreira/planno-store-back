package com.planno.store.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Keeps Cloudflare Pages branch deployments read-only when they call the Store API. */
@Component
public class CloudflarePreviewWriteGuardFilter extends OncePerRequestFilter {
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String PAGES_SUFFIX = ".pages.dev";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if ("OPTIONS".equals(method)) {
            String requestedMethod = request.getHeader("Access-Control-Request-Method");
            if (requestedMethod != null && !requestedMethod.isBlank()) method = requestedMethod.toUpperCase(Locale.ROOT);
        }
        if (!READ_METHODS.contains(method) && isPagesPreviewOrigin(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cloudflare Pages previews are read-only.");
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean isPagesPreviewOrigin(String origin) {
        if (origin == null || origin.isBlank()) return false;
        try {
            URI uri = URI.create(origin);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.endsWith(PAGES_SUFFIX)) return false;
            String projectAndBranch = host.substring(0, host.length() - PAGES_SUFFIX.length());
            // Production is <project>.pages.dev; branch previews add a branch or deployment label.
            return projectAndBranch.contains(".");
        } catch (IllegalArgumentException malformedOrigin) {
            return false;
        }
    }
}

package com.planno.store.core.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final com.planno.store.store.service.CurrentStoreProvider stores;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService, com.planno.store.store.service.CurrentStoreProvider stores) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.stores = stores;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    if (userDetails instanceof AuthenticatedUser authenticated
                            && authenticated.mustChangePassword()
                            && !request.getRequestURI().equals("/api/v1/auth/change-password")
                            && !request.getRequestURI().equals("/api/v1/auth/me")) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED");
                        return;
                    }
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    if (request.getRequestURI().startsWith("/api/v1/admin/") && userDetails instanceof AuthenticatedUser principal) {
                        var scoped = principal.forStore(stores.getCurrentStoreId());
                        var scopedAuthentication = new UsernamePasswordAuthenticationToken(scoped, null, scoped.getAuthorities());
                        scopedAuthentication.setDetails(authentication.getDetails());
                        SecurityContextHolder.getContext().setAuthentication(scopedAuthentication);
                    }
                }
            }
        } catch (org.springframework.security.access.AccessDeniedException denied) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        } catch (com.planno.store.core.exception.NotFoundException missing) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        } catch (JwtException | org.springframework.security.core.userdetails.UsernameNotFoundException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

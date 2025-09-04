package com.mabawa.triviacrave.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            String token = authHeader.substring(7);
            if (jwtTokenService.isTokenValid(token)) {
                Long userId = jwtTokenService.extractUserId(token);
                String email = jwtTokenService.extractEmail(token);
                
                // Create custom authentication token with user info
                JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    email, 
                    null, 
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    userId
                );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Invalid token - continue without authentication
        }
        
        filterChain.doFilter(request, response);
    }
}
package com.cloudbite.security;

import com.cloudbite.enums.Role;
import com.cloudbite.model.User;
import com.cloudbite.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;
        String role = null;
        Long userId = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
                userId = jwtUtil.extractClaim(jwt, claims -> claims.get("userId", Long.class));
                
                // Get role from JWT claims - with null safety
                try {
                    role = jwtUtil.extractClaim(jwt, claims -> {
                        Object r = claims.get("role");
                        return r != null ? r.toString() : null;
                    });
                } catch (Exception e) {
                    log.debug("No role in JWT, will use DB");
                }
                
                log.info("JWT parsed - username: {}, role: {}, userId: {}", username, role, userId);
            } catch (Exception e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
            }
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                User user = userRepository.findByEmail(username).orElse(null);
                if (user == null) {
                    log.warn("User not found: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                log.info("Found user: {} with role: {}", user.getEmail(), user.getRole());
                
                List<SimpleGrantedAuthority> authorities;
                if (role != null) {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    log.info("Using role from JWT: ROLE_{}", role);
                } else {
                    authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                    log.info("Using role from DB: ROLE_{}", user.getRole().name());
                }

                UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPassword())
                    .authorities(authorities)
                    .build();

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Authentication successful for: {}", username);
                } else {
                    log.warn("Token validation failed for: {}", username);
                }
            } catch (Exception e) {
                log.error("Error in JWT filter: {}", e.getMessage(), e);
            }
        }

        filterChain.doFilter(request, response);
    }
}

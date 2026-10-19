package com.securevault.security;

import com.securevault.repository.RevokedTokenRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService customUserDetailsService,
                                   RevokedTokenRepository revokedTokenRepository) {
        this.jwtUtil = jwtUtil;
        this.customUserDetailsService = customUserDetailsService;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String token = null;

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7).trim();
        }

        if (token != null && !token.isEmpty()) {
            try {
                if (revokedTokenRepository.existsByToken(token)) {
                    request.setAttribute("auth_error_message", "Token has been revoked");
                } else if (jwtUtil.isTokenExpired(token)) {
                    request.setAttribute("auth_error_message", "Token has expired");
                } else {
                    String email = jwtUtil.getEmailFromToken(token);
                    if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
                        if (jwtUtil.validateToken(token) && userDetails.isEnabled()) {
                            java.util.List<String> tokenRoles = jwtUtil.getRolesFromToken(token);
                            java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;
                            if (tokenRoles != null && !tokenRoles.isEmpty()) {
                                authorities = tokenRoles.stream()
                                        .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                                        .collect(java.util.stream.Collectors.toList());
                            } else {
                                authorities = userDetails.getAuthorities();
                            }
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                }
            } catch (ExpiredJwtException e) {
                log.warn("Expired JWT token: {}", e.getMessage());
                request.setAttribute("auth_error_message", "Token has expired");
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Invalid JWT token: {}", e.getMessage());
                request.setAttribute("auth_error_message", "Invalid or malformed JWT token");
            } catch (Exception e) {
                log.error("Authentication error processing token: {}", e.getMessage());
                request.setAttribute("auth_error_message", "Authentication failed");
            }
        }

        chain.doFilter(request, response);
    }
}

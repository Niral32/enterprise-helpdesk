package com.helpdesk.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Per-service JWT validator. Defense-in-depth: even if a request reaches this
 * service without going through the API Gateway (e.g. someone exec's into the
 * Docker network), it still needs a valid JWT to access protected endpoints.
 *
 * What it does on every request:
 *   1. Pulls the `Authorization: Bearer …` header.
 *   2. Verifies the JWT signature with the shared HS256 secret.
 *   3. Populates Spring's SecurityContext with a UsernamePasswordAuthenticationToken
 *      carrying the role as a `ROLE_<NAME>` authority, so `@PreAuthorize`
 *      and `hasRole(...)` rules just work.
 *
 * If the header is missing or the JWT is invalid we silently skip — the
 * downstream authorization rules (`anyRequest().authenticated()` or
 * `@PreAuthorize`) will then deny. We do not 401 here ourselves because some
 * endpoints (`/actuator/health`, public file routes) are deliberately
 * unauthenticated.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(header.substring(7))
                    .getBody();

                Object roleClaim = claims.get("role");
                String role = roleClaim == null ? null : roleClaim.toString();
                String email = claims.getSubject();

                // Spring's hasRole("ADMIN") matches an authority named ROLE_ADMIN.
                List<SimpleGrantedAuthority> authorities = role == null
                    ? List.of()
                    : List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // Malformed / expired / forged token — leave the context empty
                // so the request hits an authenticated() rule and returns 401.
                log.debug("Rejecting JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

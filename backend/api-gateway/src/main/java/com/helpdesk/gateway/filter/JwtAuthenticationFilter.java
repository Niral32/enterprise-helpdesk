package com.helpdesk.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Gateway filter that validates the JWT on every request routed through it.
 *
 * Wired via application.yml as `- name: JwtAuthenticationFilter` on each
 * protected route. Auth routes (`/api/auth/**`) intentionally do NOT use this
 * filter so users can log in / register without a token.
 *
 * On success the user id and role are forwarded to downstream services as
 * `X-User-Id` and `X-User-Role` headers, so downstream services can authorize
 * without re-parsing the token.
 */
@Component
public class JwtAuthenticationFilter
        extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final SecretKey signingKey;

    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange.getResponse(), "Missing or malformed Authorization header");
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(signingKey)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();

                String userId = String.valueOf(claims.get("userId", Object.class));
                String role = String.valueOf(claims.get("role", Object.class));

                ServerHttpRequest mutated = request.mutate()
                        .header("X-User-Id", userId == null ? "" : userId)
                        .header("X-User-Role", role == null ? "" : role)
                        .header("X-User-Email", claims.getSubject() == null ? "" : claims.getSubject())
                        .build();

                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (Exception ex) {
                return unauthorized(exchange.getResponse(), "Invalid or expired token");
            }
        };
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        byte[] body = ("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    /**
     * Optional per-route config (placeholder for future role checks).
     */
    public static class Config {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}

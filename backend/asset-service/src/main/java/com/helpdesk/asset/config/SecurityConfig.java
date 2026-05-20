package com.helpdesk.asset.config;

import com.helpdesk.asset.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the asset service.
 *
 * Pragmatic split that keeps internal service-to-service traffic working:
 *   - GETs on /api/assets/** are permitted because ticket-service calls
 *     them for ticket-detail enrichment without a JWT (the internal Docker
 *     network is treated as trusted for read-only enrichment lookups). A
 *     future iteration would add /internal endpoints with X-Internal-Secret
 *     and tighten this further.
 *   - All mutations (POST/PUT/DELETE/PATCH) require a valid JWT.
 *   - @EnableMethodSecurity lets controllers use @PreAuthorize for finer
 *     role-based rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Read-only asset lookups are reachable from any internal
                // service for ticket-detail enrichment (no JWT available).
                .requestMatchers(HttpMethod.GET, "/api/assets/**").permitAll()

                // All writes require a valid JWT.
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

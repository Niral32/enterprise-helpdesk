package com.helpdesk.ticket.config;

import com.helpdesk.ticket.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the ticket service.
 *
 * Defense-in-depth: even though the gateway already validated the JWT,
 * we revalidate here so internal network access still requires a token.
 * Method-level security is enabled so admin/role checks can be expressed
 * as `@PreAuthorize("hasRole('ADMIN')")` instead of manual if-blocks.
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

                // Attachment downloads must work in plain <img> tags / direct
                // links — no Authorization header available. IDs are
                // surrogate numerics, comparable to avatar URLs.
                .requestMatchers("/api/tickets/attachments/*/download").permitAll()

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

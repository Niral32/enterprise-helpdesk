package com.helpdesk.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the ticket service.
 *
 * The API Gateway is the JWT gatekeeper, so this internal service trusts
 * requests routed over the Docker network. We:
 *   - disable CSRF (REST, no cookies)
 *   - run stateless (no HTTP session)
 *   - permit all requests (gateway already validated the JWT and forwarded
 *     X-User-Id / X-User-Role headers we can read inside controllers)
 *   - drop Spring's default form login + basic-auth pages
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        return http.build();
    }
}

package com.helpdesk.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the user service.
 *
 * The API Gateway is the JWT gatekeeper, so this service trusts requests that
 * arrive over the internal Docker network. We:
 *   - disable CSRF (REST, no cookies)
 *   - go stateless (no HTTP session)
 *   - permit all requests (gateway already validated JWT and forwarded
 *     X-User-Id / X-User-Role headers)
 *   - drop Spring's default form login + basic auth pages
 *
 * The {@link PasswordEncoder} bean is required by {@code UserService} for
 * hashing user passwords on create/update.
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

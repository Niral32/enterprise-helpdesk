package com.helpdesk.auth.config;

import com.helpdesk.auth.security.LoginRateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security configuration for the authentication service.
 *
 *   - All /api/auth/** endpoints are publicly reachable (you can't sign in
 *     to get a JWT if you need a JWT to call the sign-in endpoint).
 *   - The login endpoint is protected by a per-IP Bucket4j rate-limiter
 *     (see {@link LoginRateLimitFilter}) to slow brute-force attempts.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LoginRateLimitFilter loginRateLimitFilter;

    public SecurityConfig(LoginRateLimitFilter loginRateLimitFilter) {
        this.loginRateLimitFilter = loginRateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz ->
                authz
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/", "/error").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
            )
            // Rate-limit /api/auth/login BEFORE Spring's usual filter chain
            // gets a chance to do real work — keeps brute-force traffic
            // off the password encoder and the database.
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration.
     *
     * In production, requests should reach this service via the API Gateway,
     * but we still allow direct browser access from the dev frontend on
     * localhost:5173 (Vite) and localhost:3000 (CRA fallback).
     *
     * Wildcard origins are NOT used because we send JWTs in headers and want
     * the option of cookie-based auth in the future.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://frontend:5173"
        ));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        corsConfig.setExposedHeaders(Arrays.asList("Authorization"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }

    /**
     * Password encoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

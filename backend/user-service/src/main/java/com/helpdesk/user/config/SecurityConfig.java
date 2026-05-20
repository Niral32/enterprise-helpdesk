package com.helpdesk.user.config;

import com.helpdesk.user.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the user service.
 *
 * Design notes (the interview-y bit):
 *   - Stateless: no HTTP sessions, every request carries a JWT.
 *   - Defense-in-depth: the gateway validates the JWT once, then this
 *     service re-validates it (see JwtAuthenticationFilter). If anyone
 *     reaches this service bypassing the gateway, they still need a valid
 *     JWT to do anything beyond the explicitly-permitted endpoints.
 *   - Method security enabled so we can write `@PreAuthorize("hasRole('ADMIN')")`
 *     on controller methods instead of repeating manual role checks.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // turns on @PreAuthorize / @PostAuthorize support
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
                // Health checks, error page, OpenAPI docs — public.
                .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Public avatar files — the browser fetches these in <img>
                // tags, which can't carry an Authorization header. The
                // filename embeds a UUID so paths aren't guessable.
                .requestMatchers("/api/users/avatars/**").permitAll()

                // Service-to-service: validated with X-Internal-Secret in the
                // controller, not JWT. These never reach a browser through
                // the gateway because the gateway routes them differently.
                .requestMatchers("/api/users/internal/**").permitAll()
                .requestMatchers("/api/users/bootstrap").permitAll()

                // Everything else needs a valid JWT.
                .anyRequest().authenticated()
            )
            // Slot our filter just before the default username/password
            // filter so SecurityContext is populated before authz runs.
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

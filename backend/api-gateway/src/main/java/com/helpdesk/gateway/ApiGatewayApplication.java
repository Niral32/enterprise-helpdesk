package com.helpdesk.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application — Spring Cloud Gateway.
 *
 * Routes, CORS, and the global JwtAuthenticationFilter are configured
 * declaratively in {@code application.yml} (under {@code spring.cloud.gateway})
 * to keep this class minimal and configuration-driven.
 *
 * Per-route filters:
 *  - /api/auth/**          → no JWT filter (login/register must be public)
 *  - /api/users/**         → JwtAuthenticationFilter
 *  - /api/tickets/**       → JwtAuthenticationFilter
 *  - /api/assets/**        → JwtAuthenticationFilter
 *  - /api/notifications/** → JwtAuthenticationFilter
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

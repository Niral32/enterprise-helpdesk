package com.helpdesk.auth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Authentication Service - Central authentication and authorization service
 * Handles user registration, login, and JWT token management
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Authentication Service API",
        version = "1.0.0",
        description = "User authentication, registration, and JWT token management"
    ),
    servers = {
        @Server(url = "http://localhost:8001", description = "Development Server"),
        @Server(url = "http://api-gateway:8000", description = "Gateway Server")
    }
)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

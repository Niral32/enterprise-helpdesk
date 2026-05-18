package com.helpdesk.user;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User Service Application
 * Manages user profiles, departments, and user-related operations
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "User Service API",
        version = "1.0.0",
        description = "User profile management and administration"
    ),
    servers = {
        @Server(url = "http://localhost:8002", description = "Development Server"),
        @Server(url = "http://user-service:8002", description = "Docker Server")
    }
)
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

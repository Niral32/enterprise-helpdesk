package com.helpdesk.ticket;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ticket Service Application
 * Manages support tickets and comments
 */
@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Ticket Service API",
        version = "1.0.0",
        description = "Support ticket management and tracking"
    ),
    servers = {
        @Server(url = "http://localhost:8003", description = "Development Server"),
        @Server(url = "http://ticket-service:8003", description = "Docker Server")
    }
)
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}

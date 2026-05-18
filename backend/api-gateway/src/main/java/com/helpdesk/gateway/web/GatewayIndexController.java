package com.helpdesk.gateway.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avoids a bare 404 / Whitelabel page when someone opens the gateway root URL in a browser.
 */
@RestController
public class GatewayIndexController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> index() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "Enterprise Helpdesk API Gateway");
        body.put("frontend", "http://localhost:5173");
        body.put("message", "REST APIs are under /api/... — this is not the SPA.");
        body.put("examples", Map.of(
                "authHealth", "GET /api/auth/health",
                "swaggerUi", "/swagger-ui.html"
        ));
        return Mono.just(body);
    }
}

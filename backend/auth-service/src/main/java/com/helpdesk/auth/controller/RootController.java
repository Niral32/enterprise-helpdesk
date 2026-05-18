package com.helpdesk.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Friendly JSON at {@code /} so visiting the auth service port directly does not show a Whitelabel 404.
 */
@RestController
public class RootController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "Auth Service");
        body.put("message", "Use /api/auth/login, /api/auth/register, or /swagger-ui.html");
        body.put("preferredEntry", "Via API Gateway: http://localhost:8000/api/auth/...");
        return body;
    }
}

package com.helpdesk.auth.config;

import com.helpdesk.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures exactly one system administrator exists after a fresh install by creating the default
 * account when {@code helpdesk.bootstrap-admin.enabled} is true and no ADMIN row is present.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AuthService authService;

    @Value("${helpdesk.bootstrap-admin.enabled:true}")
    private boolean enabled;

    @Value("${helpdesk.bootstrap-admin.email}")
    private String email;

    @Value("${helpdesk.bootstrap-admin.password}")
    private String password;

    @Value("${helpdesk.bootstrap-admin.first-name:System}")
    private String firstName;

    @Value("${helpdesk.bootstrap-admin.last-name:Administrator}")
    private String lastName;

    @Value("${helpdesk.bootstrap-admin.department:IT}")
    private String department;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Default administrator bootstrap is disabled (helpdesk.bootstrap-admin.enabled=false)");
            return;
        }
        authService.ensureBootstrapAdmin(email, password, firstName, lastName, department);
    }
}

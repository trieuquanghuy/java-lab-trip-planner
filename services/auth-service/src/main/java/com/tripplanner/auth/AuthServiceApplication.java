// Phase 0 scaffold + Phase 2 wiring: Spring Boot servlet application — auth-service.
//
// Package per D-28 / Convention C1: com.tripplanner.auth.
// Phase 2 annotations:
//   - @EnableAsync — D-01 (async email send) + D-22 (MDC propagation via AsyncConfig's TaskDecorator)
//   - @EnableScheduling — D-17 (TokenCleanupJob in Plan 04)
//   - @EnableConfigurationProperties(AuthProperties.class) — binds the app.* tree
package com.tripplanner.auth;

import com.tripplanner.auth.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync                                             // Phase 2 D-01 / D-22
@EnableScheduling                                        // Phase 2 D-17 (TokenCleanupJob in Plan 04)
@EnableConfigurationProperties(AuthProperties.class)     // Binds app.* tree
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

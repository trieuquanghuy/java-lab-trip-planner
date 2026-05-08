// Phase 0 scaffold: empty Spring Boot servlet application — auth-service.
//
// Package per D-28 / Convention C1: com.tripplanner.auth.
// Phase 2 lands the real auth domain (User entity, JwtService, /api/auth/* controllers, V2+ Flyway).
package com.tripplanner.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

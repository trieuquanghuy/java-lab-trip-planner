// Source: 00-CONTEXT.md D-28 / Convention C1 — package com.tripplanner.gateway
// Source: 00-PATTERNS.md Bucket D (lines 463-475)
package com.tripplanner.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}

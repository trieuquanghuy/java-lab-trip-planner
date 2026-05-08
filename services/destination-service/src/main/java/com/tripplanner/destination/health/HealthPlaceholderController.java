// Phase 0 scaffold-only health placeholder for destination-service (D-01 / Convention C10).
//
// Source: 00-RESEARCH.md lines 1011-1033, with the per-service substitution table from
// 00-PATTERNS.md Bucket E applied (service="destination"). The api-gateway forwards
// /__health/destination to http://destination-service:8083/__health (D-02 static-URI routing);
// the gateway strips the /<svc> suffix via SetPath=/__health, so this controller responds at
// /__health here.
//
// Phase 1 ships /api/destinations/_ping (single underscore) as the application-level probe. The
// double-underscore /__health stays as the Phase 0 scaffold probe forever (Convention C10).
package com.tripplanner.destination.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthPlaceholderController {

    @GetMapping("/__health")
    public Map<String, Object> health() {
        return Map.of(
            "service", "destination-service",
            "status", "UP",
            "phase", 0
        );
    }
}

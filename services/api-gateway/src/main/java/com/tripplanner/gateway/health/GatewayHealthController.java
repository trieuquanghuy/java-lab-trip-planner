// Phase 0 scaffold-only health placeholder for the gateway itself (D-01 / Convention C10).
//
// Source: adapted from 00-RESEARCH.md lines 1011-1033 (the per-service health controller template),
// substituting "api-gateway" for the service name.
//
// Reactive controller in a WebFlux app: returning Map<String, Object> is fine — Spring Cloud Gateway
// auto-wires the reactive Jackson encoder. No Mono wrapping is required for a static payload.
//
// Routing note: this controller responds at /__health (NOT /__health/gateway). The gateway's own
// route table (application.yml) declares a /__health/gateway → SetPath=/__health alias so external
// callers can address every service uniformly via /__health/<svc> through the gateway. Per D-02,
// downstream-service routes use STATIC URIs (e.g. http://auth-service:8081), not lb://.
//
// Phase 1 ships /api/<svc>/_ping (single underscore) for application-level probes. The
// double-underscore /__health stays as the Phase 0 scaffold-only probe forever (Convention C10).
package com.tripplanner.gateway.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GatewayHealthController {

    @GetMapping("/__health")
    public Map<String, Object> health() {
        return Map.of(
            "service", "api-gateway",
            "status", "UP",
            "phase", 0
        );
    }
}

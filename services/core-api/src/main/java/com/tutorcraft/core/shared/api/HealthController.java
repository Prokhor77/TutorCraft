package com.tutorcraft.core.shared.api;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** /health/live и /health/ready (NFR-OBS-03) поверх групп liveness/readiness Actuator. */
@RestController
class HealthController {

    private final HealthEndpoint healthEndpoint;

    HealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health/live")
    ResponseEntity<Status> live() {
        return toResponse(healthEndpoint.healthForPath("liveness"));
    }

    @GetMapping("/health/ready")
    ResponseEntity<Status> ready() {
        return toResponse(healthEndpoint.healthForPath("readiness"));
    }

    private static ResponseEntity<Status> toResponse(HealthComponent health) {
        Status status = health == null ? Status.UNKNOWN : health.getStatus();
        HttpStatus http = Status.UP.equals(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(http).body(status);
    }
}

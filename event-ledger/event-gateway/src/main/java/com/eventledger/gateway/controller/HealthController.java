package com.eventledger.gateway.controller;

import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final EntityManager entityManager;

    public HealthController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "db", "H2",
                    "service", "event-gateway"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            ));
        }
    }
}

package com.ledgerline.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of(
            "application", "LedgerLine",
            "status", "UP",
            "message", "Backend is running successfully"
        );
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "LedgerLine Backend"
        );
    }
}

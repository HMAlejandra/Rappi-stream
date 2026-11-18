package com.rappistream.dashboardbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;

/**
 * Sirve el dashboard HTML y el healthcheck.
 * SRP: solo gestiona la capa HTTP de navegación.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() { return "forward:/index.html"; }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "dashboard_backend"));
    }
}

package com.rappistream.dashboardbackend.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * Sirve el dashboard.html cuando el navegador accede a la raíz.
 * Spring Boot lo busca en src/main/resources/static/index.html.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String dashboard() {
        return "forward:/index.html";
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "dashboard_backend"));
    }
}

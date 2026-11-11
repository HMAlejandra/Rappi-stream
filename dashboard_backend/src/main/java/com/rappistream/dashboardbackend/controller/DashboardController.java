package com.rappistream.dashboardbackend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> health() {
        return org.springframework.http.ResponseEntity.ok(
                java.util.Map.of("status", "UP", "service", "dashboard_backend"));
    }
}

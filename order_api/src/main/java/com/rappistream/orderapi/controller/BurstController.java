package com.rappistream.orderapi.controller;

import com.rappistream.common.dto.BurstTriggeredDTO;
import com.rappistream.orderapi.dto.request.BurstRequest;
import com.rappistream.orderapi.service.burst.BurstOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para el Modo Ráfaga.
 *
 * POST /api/orders/burst — dispara N pedidos de forma asíncrona y acotada.
 * SRP: solo la capa HTTP; la orquestación vive en BurstOrchestrator y la
 * validación la realiza @Valid sobre el record Fail-Fast BurstRequest.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
public class BurstController {

    private final BurstOrchestrator orchestrator;

    public BurstController(BurstOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/burst")
    public ResponseEntity<BurstTriggeredDTO> disparar(@Valid @RequestBody BurstRequest request) {
        BurstTriggeredDTO dto = orchestrator.disparar(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }
}

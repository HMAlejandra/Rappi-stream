package com.rappistream.orderapi.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.orderapi.application.OrderPublisherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de capa web para OrderController.
 *
 * @WebMvcTest: levanta solo el contexto MVC (sin RabbitMQ real).
 * OrderPublisherService se mockea para aislar la capa HTTP.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderPublisherService publisherService;

    @Test
    @DisplayName("POST /api/orders con datos válidos → 202 Accepted con orderId")
    void postOrdenValida_retorna202ConOrderId() throws Exception {
        String orderId = "uuid-generado-123";
        when(publisherService.publicarComando(any())).thenReturn(orderId);

        CreateOrderRequest request = new CreateOrderRequest(
                "María García", "Hamburguesa doble", 2, "Calle 100 #15-30, Bogotá"
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("ACEPTADO"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(publisherService, times(1)).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders válido → llama al servicio exactamente una vez")
    void postOrdenValida_llamaAlServicioUnaVez() throws Exception {
        when(publisherService.publicarComando(any())).thenReturn("orden-abc");

        String body = """
                {
                  "customerName": "Juan Pérez",
                  "product": "Pizza margarita",
                  "quantity": 1,
                  "address": "Carrera 7 #32-10"
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        verify(publisherService, times(1)).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders sin customerName → 400 Bad Request")
    void postSinCustomerName_retorna400() throws Exception {
        String body = """
                {"product":"Sushi","quantity":2,"address":"Av. 68 #5-20"}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders con quantity = 0 → 400 Bad Request")
    void postConQuantityCero_retorna400() throws Exception {
        String body = """
                {"customerName":"Laura","product":"Tacos","quantity":0,"address":"Calle 50 #10-5"}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders con quantity negativa → 400 Bad Request")
    void postConQuantityNegativa_retorna400() throws Exception {
        String body = """
                {"customerName":"Pedro","product":"Empanadas","quantity":-3,"address":"Transversal 9 #12-1"}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders con product vacío → 400 Bad Request")
    void postConProductVacio_retorna400() throws Exception {
        String body = """
                {"customerName":"Ana","product":"","quantity":1,"address":"Calle 1 #1-1"}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders con address vacía → 400 Bad Request")
    void postConAddressVacia_retorna400() throws Exception {
        String body = """
                {"customerName":"Carlos","product":"Burrito","quantity":2,"address":"   "}
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("POST /api/orders con body vacío → 400 Bad Request")
    void postConBodyVacio_retorna400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test
    @DisplayName("GET /api/orders/health → 200 OK con status UP")
    void health_retorna200() throws Exception {
        mockMvc.perform(get("/api/orders/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("order_api"));
    }
}

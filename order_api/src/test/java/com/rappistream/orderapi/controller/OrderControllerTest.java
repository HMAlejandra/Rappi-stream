package com.rappistream.orderapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rappistream.orderapi.dto.request.CreateOrderRequest;
import com.rappistream.orderapi.service.OrderPublisherService;
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private OrderPublisherService publisherService;

    @Test @DisplayName("POST /api/orders con datos válidos → 202 Accepted con orderId")
    void postOrdenValida_retorna202() throws Exception {
        when(publisherService.publicarComando(any())).thenReturn("uuid-123");
        CreateOrderRequest request = new CreateOrderRequest(
                "María García", "Hamburguesa doble", 2, "Calle 100 #15-30, Bogotá");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value("uuid-123"))
                .andExpect(jsonPath("$.status").value("ACEPTADO"));

        verify(publisherService, times(1)).publicarComando(any());
    }

    @Test @DisplayName("POST /api/orders sin customerName → 400 Bad Request")
    void postSinCustomerName_retorna400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product\":\"Sushi\",\"quantity\":2,\"address\":\"Av. 68\"}"))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test @DisplayName("POST /api/orders con quantity = 0 → 400 Bad Request")
    void postConQuantityCero_retorna400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Laura\",\"product\":\"Tacos\",\"quantity\":0,\"address\":\"Calle 50\"}"))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test @DisplayName("POST /api/orders con body vacío → 400 Bad Request")
    void postConBodyVacio_retorna400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(publisherService, never()).publicarComando(any());
    }

    @Test @DisplayName("GET /api/orders/health → 200 OK con status UP")
    void health_retorna200() throws Exception {
        mockMvc.perform(get("/api/orders/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}

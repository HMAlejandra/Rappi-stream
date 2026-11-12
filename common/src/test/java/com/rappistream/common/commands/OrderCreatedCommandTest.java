package com.rappistream.common.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del record OrderCreatedCommand.
 *
 * POO — Pruebas de Dominio Puro:
 * No requieren Spring Boot, Kafka, RabbitMQ ni base de datos.
 * Se ejecutan en memoria RAM en milisegundos.
 *
 * Patrón AAA (Arrange, Act, Assert):
 * - Arrange: preparar los datos de entrada
 * - Act: ejecutar la operación bajo prueba
 * - Assert: verificar el resultado esperado
 */
@DisplayName("OrderCreatedCommand — Validación Fail-Fast")
class OrderCreatedCommandTest {

    // =========================================================
    // Datos válidos reutilizables (Arrange compartido)
    // =========================================================
    private final String validCommandId = UUID.randomUUID().toString();
    private final String validOrderId   = UUID.randomUUID().toString();
    private final String validName      = "María García";
    private final String validProduct   = "Hamburguesa doble";
    private final int    validQuantity  = 2;
    private final String validAddress   = "Calle 100 #15-30, Bogotá";
    private final Instant validInstant  = Instant.now();

    @Test
    @DisplayName("Debe crear el comando correctamente con datos válidos")
    void debeCrearComandoConDatosValidos() {
        // Arrange — datos válidos preparados arriba

        // Act
        var command = new OrderCreatedCommand(
                validCommandId, validOrderId, validName,
                validProduct, validQuantity, validAddress, validInstant
        );

        // Assert
        assertEquals(validCommandId, command.commandId());
        assertEquals(validOrderId,   command.orderId());
        assertEquals(validName,      command.customerName());
        assertEquals(validProduct,   command.product());
        assertEquals(validQuantity,  command.quantity());
        assertEquals(validAddress,   command.address());
        assertEquals(validInstant,   command.issuedAt());
    }

    @Test
    @DisplayName("Debe rechazar commandId nulo")
    void debeRechazarCommandIdNulo() {
        // Act & Assert
        var ex = assertThrows(NullPointerException.class, () ->
                new OrderCreatedCommand(null, validOrderId, validName,
                        validProduct, validQuantity, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("commandId"));
    }

    @Test
    @DisplayName("Debe rechazar commandId vacío")
    void debeRechazarCommandIdVacio() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderCreatedCommand("  ", validOrderId, validName,
                        validProduct, validQuantity, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("commandId"));
    }

    @Test
    @DisplayName("Debe rechazar orderId nulo")
    void debeRechazarOrderIdNulo() {
        var ex = assertThrows(NullPointerException.class, () ->
                new OrderCreatedCommand(validCommandId, null, validName,
                        validProduct, validQuantity, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("orderId"));
    }

    @Test
    @DisplayName("Debe rechazar customerName vacío")
    void debeRechazarCustomerNameVacio() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderCreatedCommand(validCommandId, validOrderId, "   ",
                        validProduct, validQuantity, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("customerName"));
    }

    @Test
    @DisplayName("Debe rechazar quantity igual a cero")
    void debeRechazarQuantityCero() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderCreatedCommand(validCommandId, validOrderId, validName,
                        validProduct, 0, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("quantity"));
    }

    @Test
    @DisplayName("Debe rechazar quantity negativa")
    void debeRechazarQuantityNegativa() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderCreatedCommand(validCommandId, validOrderId, validName,
                        validProduct, -5, validAddress, validInstant));

        assertTrue(ex.getMessage().contains("quantity"));
    }

    @Test
    @DisplayName("Debe rechazar address vacía")
    void debeRechazarAddressVacia() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new OrderCreatedCommand(validCommandId, validOrderId, validName,
                        validProduct, validQuantity, "", validInstant));

        assertTrue(ex.getMessage().contains("address"));
    }

    @Test
    @DisplayName("Debe rechazar issuedAt nulo")
    void debeRechazarIssuedAtNulo() {
        var ex = assertThrows(NullPointerException.class, () ->
                new OrderCreatedCommand(validCommandId, validOrderId, validName,
                        validProduct, validQuantity, validAddress, null));

        assertTrue(ex.getMessage().contains("issuedAt"));
    }

    @Test
    @DisplayName("El record debe ser inmutable — igualdad por valor")
    void debeSerInmutableEIgualPorValor() {
        // Dos records con los mismos datos deben ser iguales
        var cmd1 = new OrderCreatedCommand(validCommandId, validOrderId, validName,
                validProduct, validQuantity, validAddress, validInstant);
        var cmd2 = new OrderCreatedCommand(validCommandId, validOrderId, validName,
                validProduct, validQuantity, validAddress, validInstant);

        assertEquals(cmd1, cmd2);
        assertEquals(cmd1.hashCode(), cmd2.hashCode());
    }
}

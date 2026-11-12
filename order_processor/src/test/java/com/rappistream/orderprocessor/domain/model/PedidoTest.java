package com.rappistream.orderprocessor.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias del dominio — entidad Pedido.
 *
 * Estrategia:
 * Pruebas puras en memoria RAM. Sin Spring, sin Kafka, sin BD.
 * Solo instanciamos Pedido directamente y verificamos su comportamiento.
 * Patrón AAA: Arrange / Act / Assert.
 *
 * Cobertura:
 * - Creación válida
 * - Validaciones Fail-Fast del constructor
 * - Ciclo de vida completo (todas las transiciones válidas)
 * - Rechazo de transiciones inválidas (Patrón State)
 */
@DisplayName("Pedido — Dominio Rico y Patrón State")
class PedidoTest {

    // =========================================================
    // Helper: datos válidos reutilizables
    // =========================================================

    private Pedido pedidoValido() {
        return new Pedido("order-001", "María García", "Hamburguesa", 2, "Calle 100 #15-30");
    }

    // =========================================================
    // CREACIÓN — Fail-Fast
    // =========================================================

    @Test
    @DisplayName("Debe crear pedido correctamente con datos válidos")
    void debeCrearPedidoConDatosValidos() {
        // Arrange & Act
        Pedido pedido = pedidoValido();

        // Assert
        assertEquals("order-001",    pedido.getId());
        assertEquals("María García", pedido.getCustomerName());
        assertEquals("Hamburguesa",  pedido.getProduct());
        assertEquals(2,              pedido.getQuantity());
        assertEquals("Calle 100 #15-30", pedido.getAddress());
        assertEquals("CREADO",       pedido.getNombreEstado());
        assertNull(pedido.getDriverId(),   "driverId debe ser null inicialmente");
        assertNull(pedido.getDriverName(), "driverName debe ser null inicialmente");
        assertNotNull(pedido.getCreadoEn());
    }

    @Test
    @DisplayName("Debe rechazar id nulo")
    void debeRechazarIdNulo() {
        assertThrows(NullPointerException.class, () ->
                new Pedido(null, "Cliente", "Producto", 1, "Dirección"));
    }

    @Test
    @DisplayName("Debe rechazar id vacío")
    void debeRechazarIdVacio() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new Pedido("  ", "Cliente", "Producto", 1, "Dirección"));
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    @DisplayName("Debe rechazar quantity igual a cero")
    void debeRechazarQuantityCero() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                new Pedido("id-1", "Cliente", "Producto", 0, "Dirección"));
        assertTrue(ex.getMessage().contains("quantity"));
    }

    @Test
    @DisplayName("Debe rechazar quantity negativa")
    void debeRechazarQuantityNegativa() {
        assertThrows(IllegalArgumentException.class, () ->
                new Pedido("id-1", "Cliente", "Producto", -3, "Dirección"));
    }

    @Test
    @DisplayName("Debe rechazar customerName vacío")
    void debeRechazarCustomerNameVacio() {
        assertThrows(IllegalArgumentException.class, () ->
                new Pedido("id-1", "   ", "Producto", 1, "Dirección"));
    }

    // =========================================================
    // PATRÓN STATE — Ciclo de vida completo
    // =========================================================

    @Test
    @DisplayName("Ciclo completo: CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO")
    void cicloDeVidaCompleto() {
        // Arrange
        Pedido pedido = pedidoValido();
        assertEquals("CREADO", pedido.getNombreEstado());

        // Act & Assert — cada transición
        pedido.asignarRepartidor("D-001", "Carlos Mendoza");
        assertEquals("ASIGNADO", pedido.getNombreEstado());
        assertEquals("D-001",         pedido.getDriverId());
        assertEquals("Carlos Mendoza", pedido.getDriverName());

        pedido.iniciarEntrega();
        assertEquals("EN_CAMINO", pedido.getNombreEstado());

        pedido.registrarLlegada();
        assertEquals("LLEGANDO", pedido.getNombreEstado());

        pedido.completarEntrega();
        assertEquals("ENTREGADO", pedido.getNombreEstado());
    }

    // =========================================================
    // PATRÓN STATE — Transiciones inválidas
    // =========================================================

    @Test
    @DisplayName("No debe iniciar entrega desde estado CREADO")
    void noDebeIniciarEntregaDesdeCreado() {
        Pedido pedido = pedidoValido();
        var ex = assertThrows(IllegalStateException.class, pedido::iniciarEntrega);
        assertTrue(ex.getMessage().contains("CREADO"));
    }

    @Test
    @DisplayName("No debe registrar llegada desde estado CREADO")
    void noDebeRegistrarLlegadaDesdeCreado() {
        Pedido pedido = pedidoValido();
        assertThrows(IllegalStateException.class, pedido::registrarLlegada);
    }

    @Test
    @DisplayName("No debe completar entrega desde estado CREADO")
    void noDebeCompletarEntregaDesdeCreado() {
        Pedido pedido = pedidoValido();
        assertThrows(IllegalStateException.class, pedido::completarEntrega);
    }

    @Test
    @DisplayName("No debe reasignar repartidor en estado ASIGNADO")
    void noDebeReasignarEnEstadoAsignado() {
        Pedido pedido = pedidoValido();
        pedido.asignarRepartidor("D-001", "Carlos");

        var ex = assertThrows(IllegalStateException.class, () ->
                pedido.asignarRepartidor("D-002", "Laura"));
        assertTrue(ex.getMessage().contains("ASIGNADO"));
    }

    @Test
    @DisplayName("No debe completar entrega directamente desde EN_CAMINO")
    void noDebeCompletarEntregaDesdeEnCamino() {
        Pedido pedido = pedidoValido();
        pedido.asignarRepartidor("D-001", "Carlos");
        pedido.iniciarEntrega();

        var ex = assertThrows(IllegalStateException.class, pedido::completarEntrega);
        assertTrue(ex.getMessage().contains("EN_CAMINO"));
    }

    @Test
    @DisplayName("No debe operar sobre pedido en estado ENTREGADO (terminal)")
    void noDebeOperarSobreEntregado() {
        // Arrange: llevar al estado terminal
        Pedido pedido = pedidoValido();
        pedido.asignarRepartidor("D-001", "Carlos");
        pedido.iniciarEntrega();
        pedido.registrarLlegada();
        pedido.completarEntrega();

        // Assert: cualquier operación falla
        assertThrows(IllegalStateException.class, () ->
                pedido.asignarRepartidor("D-002", "Otro"));
        assertThrows(IllegalStateException.class, pedido::iniciarEntrega);
        assertThrows(IllegalStateException.class, pedido::registrarLlegada);
        assertThrows(IllegalStateException.class, pedido::completarEntrega);
    }

    @Test
    @DisplayName("No debe aceptar driverId vacío en asignarRepartidor")
    void noDebeAceptarDriverIdVacio() {
        Pedido pedido = pedidoValido();
        var ex = assertThrows(IllegalArgumentException.class, () ->
                pedido.asignarRepartidor("  ", "Carlos"));
        assertTrue(ex.getMessage().contains("driverId"));
    }
}

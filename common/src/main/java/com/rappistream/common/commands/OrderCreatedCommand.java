package com.rappistream.common.commands;

import java.time.Instant;
import java.util.Objects;

/**
 * Comando inmutable que representa la intención de crear un pedido.
 *
 * POO — Inmutabilidad y Value Objects:
 * Implementado como Java Record (JDK 17+). Los records son inmutables
 * por diseño: todos los campos son final y no existe setter.
 *
 * POO — Fail-Fast:
 * El constructor compacto valida los invariantes inmediatamente.
 * Si los datos son inválidos, se lanza IllegalArgumentException
 * antes de que el objeto exista. Esto evita propagar objetos
 * en estado inconsistente por el sistema.
 *
 * Flujo:
 * order_api → serializa a JSON → RabbitMQ (new_orders_queue)
 * order_processor → deserializa → procesa
 */
public record OrderCreatedCommand(

        /**
         * Identificador único del comando (UUID).
         * Permite trazar el comando en logs y detectar duplicados.
         */
        String commandId,

        /**
         * Identificador único del pedido (UUID).
         * Generado por order_api antes de publicar.
         * Será la clave de partición en Kafka.
         */
        String orderId,

        /** Nombre completo del cliente que realizó el pedido. */
        String customerName,

        /** Nombre del producto solicitado. */
        String product,

        /** Cantidad de unidades del producto. Debe ser mayor que cero. */
        int quantity,

        /** Dirección de entrega del pedido. */
        String address,

        /** Momento en que se emitió el comando (UTC). */
        Instant issuedAt

) {

    /**
     * Constructor compacto — Validación Fail-Fast.
     *
     * Se ejecuta automáticamente al crear el record.
     * Rechaza inmediatamente cualquier dato inválido.
     */
    public OrderCreatedCommand {
        Objects.requireNonNull(commandId, "commandId no puede ser nulo");
        Objects.requireNonNull(orderId, "orderId no puede ser nulo");
        Objects.requireNonNull(issuedAt, "issuedAt no puede ser nulo");

        if (commandId.isBlank()) {
            throw new IllegalArgumentException("commandId no puede estar vacío");
        }
        if (orderId.isBlank()) {
            throw new IllegalArgumentException("orderId no puede estar vacío");
        }
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("customerName no puede ser nulo o vacío");
        }
        if (product == null || product.isBlank()) {
            throw new IllegalArgumentException("product no puede ser nulo o vacío");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity debe ser mayor que cero, recibido: " + quantity);
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address no puede ser nulo o vacío");
        }
    }
}

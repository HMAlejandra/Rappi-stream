# Diagrama de Base de Datos — PostgreSQL rappi_stream_db

```mermaid
erDiagram
    drivers {
        VARCHAR(36)   id           PK  "D-001..D-005"
        VARCHAR(100)  name             "Carlos Mendoza, etc."
        BOOLEAN       available        "true por defecto"
        TIMESTAMPTZ   created_at       "auto"
    }

    orders {
        VARCHAR(36)   id           PK  "UUID generado por order_api"
        VARCHAR(150)  customer_name    "NOT NULL"
        VARCHAR(200)  product          "NOT NULL"
        INTEGER       quantity         "CHECK quantity > 0"
        TEXT          address          "NOT NULL"
        VARCHAR(36)   driver_id    FK  "REFERENCES drivers(id)"
        VARCHAR(30)   status           "CREADO|ASIGNADO|EN_CAMINO|LLEGANDO|ENTREGADO"
        TIMESTAMPTZ   created_at       "auto"
        TIMESTAMPTZ   updated_at       "actualizado en cada transición"
    }

    order_events {
        BIGSERIAL     id           PK  "auto-increment"
        VARCHAR(36)   event_id         "UNIQUE — clave idempotencia"
        VARCHAR(36)   order_id     FK  "REFERENCES orders(id)"
        VARCHAR(50)   event_type       "ORDER_CREATED|DRIVER_ASSIGNED|..."
        TIMESTAMPTZ   event_timestamp  "momento del evento en Kafka"
        TEXT          payload          "JSON del payload específico"
        TIMESTAMPTZ   recorded_at      "momento de inserción en PG"
    }

    drivers ||--o{ orders : "asignado_a"
    orders ||--o{ order_events : "tiene_eventos"
```

## Decisiones de diseño

### Por qué `event_id` es UNIQUE en `order_events`

El `dashboard_aggregator` está configurado con `auto.offset.reset=earliest`. Esto significa que al arrancar por primera vez (sin offset guardado), reprocesa **todos** los eventos históricos del topic de Kafka. Sin la constraint UNIQUE, insertaría filas duplicadas en cada reinicio.

La constraint actúa como segunda barrera de idempotencia:
1. Primera barrera: `existsByEventId()` antes de procesar → evita trabajo innecesario
2. Segunda barrera: `UNIQUE constraint` en la BD → evita duplicados si dos hilos procesan el mismo evento simultáneamente

### Por qué `driver_id` es nullable en `orders`

El pedido se crea en estado `CREADO` antes de tener un repartidor asignado. `order_processor` primero hace el INSERT con `driver_id = NULL`, luego aplica la Strategy y hace el UPDATE con el driver. Esto refleja fielmente la secuencia del negocio.

### Separación entre `order_processor` y `dashboard_aggregator`

Ambos escriben en PostgreSQL pero con responsabilidades distintas:
- `order_processor`: INSERT inicial + UPDATE a ASIGNADO (dentro del flujo de procesamiento del comando)
- `dashboard_aggregator`: actualiza el status en cada evento Kafka subsiguiente (EN_CAMINO, LLEGANDO, ENTREGADO)

No hay conflicto porque trabajan en fases temporales diferentes.

### Índices creados por `init.sql`

```sql
-- Consultar el historial de eventos de un pedido específico
CREATE INDEX idx_order_events_order_id ON order_events(order_id);

-- Filtrar eventos por tipo (para análisis y dashboard)
CREATE INDEX idx_order_events_event_type ON order_events(event_type);
```

# Sistemas Distribuidos — Rappi-Stream

## Event Sourcing con Kafka

### Concepto

Event Sourcing es un patrón donde el estado actual de un sistema se reconstruye procesando la secuencia completa de eventos inmutables que han ocurrido. En lugar de almacenar el estado actual directamente, se almacena la secuencia de eventos y el estado se deriva procesándolos en orden.

### Implementación en Rappi-Stream

Kafka actúa como el Event Store (System of Record). Cada hecho del ciclo de vida de un pedido se registra como un evento en el topic `order_events`:

```
ORDER_CREATED → DRIVER_ASSIGNED → DELIVERY_STARTED → DRIVER_ARRIVING → ORDER_DELIVERED
```

Cada evento es un `OrderEventEnvelope` (Java Record inmutable) con:
- `eventId` — UUID único (clave de idempotencia)
- `orderId` — clave de partición en Kafka
- `eventType` — enum que determina cómo deserializar el payload
- `timestamp` — momento del evento (Instant)
- `payload` — JSON con los datos específicos del evento

El `dashboard_aggregator` es el consumidor que reconstruye el estado: lee el topic desde el inicio y aplica cada evento para derivar el estado actual de cada pedido, que persiste en PostgreSQL.

### Ventajas

- **Auditabilidad completa:** el log de eventos es la fuente de verdad. Se puede reconstruir cualquier estado histórico.
- **Replay:** si el agregador pierde su estado, puede reprocesar todo el topic desde el principio.
- **Desacoplamiento:** los productores no saben quién consumirá sus eventos. Múltiples consumidores independientes (`driver_simulator`, `notification_router`, `dashboard_aggregator`) procesan el mismo stream sin coordinación.

## Orden dentro de una partición

### Qué garantiza Kafka

Kafka garantiza que los mensajes dentro de una misma partición se entregan a los consumidores en el mismo orden en que fueron producidos. Esto es una propiedad fundamental del modelo de partición ordenada de Kafka.

### Qué NO garantiza Kafka

Kafka **no** garantiza orden entre particiones distintas. Si dos mensajes van a particiones diferentes, pueden llegar en cualquier orden relativo, sin importar el orden de producción.

### Por qué `orderId` como clave de partición es la decisión correcta

La clave de partición determina a qué partición va un mensaje. Kafka calcula `partición = hash(key) % num_particiones`. Al usar `orderId` como clave:

- Todos los eventos de un mismo pedido van a la misma partición.
- Por lo tanto, se procesan en el orden exacto en que fueron publicados.
- Esto garantiza que el `dashboard_aggregator` vea `DRIVER_ASSIGNED` antes que `DELIVERY_STARTED`, y este antes que `DRIVER_ARRIVING`, etc.

Si usáramos una clave aleatoria o nula, los eventos de un mismo pedido podrían ir a particiones distintas y procesarse en orden incorrecto, rompiendo la consistencia del estado reconstruido.

### Configuración

El topic `order_events` tiene 3 particiones (creado por `infrastructure/kafka/create-topics.sh`). Los productores (`order_processor`, `driver_simulator`) envían el `orderId` como clave del mensaje:

```java
kafkaTemplate.send("order_events", orderId, envelopeJson);
```

## Consumidor stateful: dashboard_aggregator

### Qué es un consumidor stateful

A diferencia de `driver_simulator` y `notification_router` que son consumidores stateless (procesan cada evento de forma independiente), el `dashboard_aggregator` mantiene estado: reconstruye y actualiza el estado consolidado de cada pedido a medida que procesa eventos.

### Por qué usa `earliest`

La configuración `auto.offset.reset=earliest` significa que cuando el consumidor arranca por primera vez (sin offset guardado), lee desde el inicio del topic. Esto permite:

- **Reconstrucción completa:** si el agregador se reinicia después de un crash donde perdió su estado en PostgreSQL, puede reprocesar todos los eventos desde el principio y reconstruir el estado completo.
- **Cold start:** al desplegar por primera vez, procesa todos los eventos históricos.

Si usara `latest`, los eventos publicados antes del primer arranque se perderían y el estado nunca se reconstruiría completamente.

### Group ID dedicado

El `dashboard_aggregator` usa el group ID `dashboard-aggregator-group`, independiente de `driver-simulator-group` y `notification-router-group`. Cada grupo de consumidores mantiene su propio offset, por lo que los tres consumidores avanzan de forma independiente.

## Idempotencia

### El problema

Si el `dashboard_aggregator` reinicia y reprocesa desde el inicio del topic, procesará eventos que ya procesó antes. Sin idempotencia, esto causaría duplicados: inserts duplicados en PostgreSQL, estados incorrectos, etc.

### Solución: doble barrera

**Barrera 1 — Aplicación (`existsByEventId`):** antes de insertar un evento en la tabla `order_events`, el servicio verifica si ya existe un registro con el mismo `eventId`:

```java
if (eventRepository.existsByEventId(envelope.eventId())) {
    // Ya procesado: actualizar estado y salir
}
```

**Barrera 2 — Base de datos (constraint UNIQUE):** la tabla `order_events` tiene una constraint `UNIQUE` en la columna `event_id`:

```sql
CREATE TABLE order_events (
    id          BIGSERIAL    PRIMARY KEY,
    event_id    VARCHAR(36)  NOT NULL UNIQUE,  -- constraint de idempotencia
    ...
);
```

Si dos hilos intentan insertar el mismo `event_id` simultáneamente, la constraint UNIQUE rechaza la segunda inserción con una `DataIntegrityViolationException`.

### Por qué doble barrera

La verificación en aplicación (`existsByEventId`) evita el overhead de una excepción de base de datos en el caso común (evento duplicado detectado en aplicación). La constraint UNIQUE protege contra la condición de carrera donde dos hilos pasan la verificación de aplicación casi simultáneamente. Juntas, las dos barreras garantizan idempotencia exacta.

## Escalabilidad horizontal de los workers con RabbitMQ Work Queue

### Patrón Work Queue (Competing Consumers)

Los workers de SMS y Push escalan horizontalmente con 2 réplicas cada uno. Todas las réplicas de un worker comparten la misma cola:
- Las 2 réplicas de `sms_worker` comparten `client_sms_queue`.
- Las 2 réplicas de `push_notification_worker` comparten `driver_push_queue`.

RabbitMQ distribuye los mensajes en round-robin entre los consumidores activos de la cola. Si hay 4 mensajes en `client_sms_queue` y 2 réplicas de `sms_worker`, cada réplica recibe 2 mensajes.

### Ventajas

- **Balanceo de carga automático:** sin coordinación central. RabbitMQ reparte los mensajes.
- **Tolerancia a fallos:** si una réplica cae, RabbitMQ redistribuye sus mensajes a la réplica restante (con `ack` manual, los mensajes no confirmados se reencolan).
- **Escalado dinámico:** se puede cambiar el número de réplicas sin reconfigurar:

```bash
docker compose up -d --scale sms_worker=3 --scale push_notification_worker=3
```

### Configuración en docker-compose.yml

```yaml
sms_worker:
  deploy:
    replicas: 2
    resources:
      limits:
        cpus: '0.25'
        memory: 256M
```

Nota: con `replicas > 1` no se puede usar `container_name` fijo ni port mapping fijo (causaría conflicto). Docker Compose asigna nombres y puertos automáticamente.

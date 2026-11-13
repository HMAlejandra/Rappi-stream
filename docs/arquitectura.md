# Arquitectura — Rappi-Stream

## Visión general

Rappi-Stream es una plataforma de logística en tiempo real con 8 microservicios Spring Boot que se comunican mediante dos brokers de mensajería (Kafka y RabbitMQ) y una base de datos relacional (PostgreSQL). El sistema sigue un diseño basado en eventos donde Kafka actúa como Event Store inmutable y RabbitMQ transporta comandos y notificaciones.

## Microservicios

### order_api (puerto 8080)

Puerta de entrada del sistema. Expone `POST /api/orders` que acepta un JSON con los datos del pedido. Valida la entrada con Bean Validation (`@NotBlank`, `@Min`), genera un `orderId` UUID, construye un `OrderCreatedCommand` inmutable y lo publica en la cola `new_orders_queue` de RabbitMQ. Responde `202 Accepted` con el `orderId` generado.

**Tecnologías:** Spring Web, Spring AMQP, Bean Validation, Jackson.

### order_processor (puerto 8081)

Es el corazón del sistema. Consume `OrderCreatedCommand` de RabbitMQ, crea una entidad `Pedido` (modelo de dominio rico), la persiste en PostgreSQL, publica `ORDER_CREATED` en Kafka, aplica la estrategia de asignación (RoundRobin) que transiciona el pedido a `ASIGNADO` vía Patrón State, actualiza PostgreSQL y publica `DRIVER_ASSIGNED` en Kafka.

**Diseño de dominio:**
- **State:** 5 estados concretos (`EstadoCreado`, `EstadoAsignado`, `EstadoEnCamino`, `EstadoLlegando`, `EstadoEntregado`) que implementan la interfaz `OrderState`. Sin if-else.
- **Strategy:** `RoundRobinAssignmentStrategy` implementa `DriverAssignmentStrategy`.
- **DIP:** Puertos `OrderRepositoryPort` y `OrderEventPublisherPort` definidos en el dominio, implementados por adaptadores JPA y Kafka en la capa de infraestructura.

**Tecnologías:** Spring AMQP (consumer), Spring Kafka (producer), Spring Data JPA, PostgreSQL.

### driver_simulator (puerto 8082)

Consume `DRIVER_ASSIGNED` del topic `order_events` de Kafka. Simula el recorrido del repartidor publicando secuencialmente tres eventos con delays configurables:
1. `DELIVERY_STARTED` (después de 5s)
2. `DRIVER_ARRIVING` (después de 8s)
3. `ORDER_DELIVERED` (después de 5s)

El procesamiento es asíncrono (`@Async`) para no bloquear el hilo del consumidor de Kafka durante los delays.

**Tecnologías:** Spring Kafka (consumer + producer), Spring Web (healthcheck).

### notification_router (puerto 8083)

Puente (Patrón Bridge) entre Kafka y RabbitMQ. Consume todos los eventos del topic `order_events` y aplica Patrón Strategy para decidir si un evento requiere notificación:

- `DRIVER_ARRIVING` → construye `NotificationCommand` tipo SMS con el nombre del repartidor y tiempo estimado → publica en el Topic Exchange `notifications` con routing key `notify.client.sms`.
- `ORDER_DELIVERED` → construye `NotificationCommand` tipo PUSH confirmando la entrega → publica con routing key `notify.driver.push`.
- Los demás eventos (`ORDER_CREATED`, `DRIVER_ASSIGNED`, `DELIVERY_STARTED`) no generan notificación.

**Patrón Strategy:** La interfaz `NotificationStrategy` declara `aplica(eventType)` y `construir(envelope)`. Dos implementaciones concretas (`DriverArrivingSmsStrategy`, `OrderDeliveredPushStrategy`) encapsulan la lógica de cada tipo. `NotificationRouterService` itera sobre todas las estrategias inyectadas y delega en la que aplica. Esto respeta Open/Closed: añadir una nueva notificación solo requiere crear una nueva implementación de `NotificationStrategy`.

**Tecnologías:** Spring Kafka (consumer), Spring AMQP (producer), Jackson.

### sms_worker (puerto 8084, 2 réplicas)

Consume `NotificationCommand` de la cola `client_sms_queue` (binding: `notify.client.*`) y simula el envío de SMS imprimiendo un log estructurado. Escala horizontalmente con 2 réplicas — RabbitMQ distribuye los mensajes en round-robin entre las instancias (Work Queue pattern).

**Tecnologías:** Spring AMQP (consumer), Jackson.

### push_notification_worker (puerto 8085, 2 réplicas)

Consume `NotificationCommand` de la cola `driver_push_queue` (binding: `notify.driver.*`) y simula el envío de notificación Push al repartidor. Misma estrategia de escalado que `sms_worker`.

**Tecnologías:** Spring AMQP (consumer), Jackson.

### dashboard_aggregator (puerto 8086)

Consumidor stateful de Kafka. Lee el topic `order_events` desde el offset más antiguo (`earliest`) y reconstruye el estado de cada pedido procesando la secuencia de eventos en orden. Persiste el estado consolidado en PostgreSQL (tabla `orders`) y registra cada evento en la tabla `order_events` para auditoría. Tras procesar cada evento, envía un `OrderStatusDTO` vía HTTP POST al `dashboard_backend` para que lo retransmita por WebSocket.

**Idempotencia:** Usa `event_id` (UUID único por evento) como constraint UNIQUE en la tabla `order_events`. Si el consumidor reinicia y reprocesa desde el inicio, los eventos duplicados se rechazan por la constraint sin afectar el estado.

**Tecnologías:** Spring Kafka (consumer), Spring Data JPA, PostgreSQL, Spring Web (HTTP client).

### dashboard_backend (puerto 8087)

Sirve el dashboard HTML estático y mantiene conexiones WebSocket abiertas con los navegadores conectados. Recibe `OrderStatusDTO` del `dashboard_aggregator` vía `POST /internal/update` y lo retransmite a todos los clientes WebSocket conectados, actualizando el dashboard en tiempo real.

**Tecnologías:** Spring Web, Spring WebSocket, HTML/JavaScript/CSS estático.

## Justificación del diseño híbrido: Kafka + RabbitMQ

El sistema usa dos brokers de mensajería porque los patrones de comunicación tienen requisitos fundamentalmente distintos:

### Kafka para eventos (Event Sourcing)

Los eventos del ciclo de vida del pedido necesitan:
- **Persistencia duradera:** los eventos deben sobrevivir a reinicios del broker.
- **Replay:** el `dashboard_aggregator` puede reprocesar desde el inicio del topic para reconstruir el estado completo.
- **Orden garantizado por partición:** al usar `orderId` como clave de partición, todos los eventos de un mismo pedido llegan a la misma partición en el orden exacto en que fueron publicados.
- **Múltiples consumidores independientes:** `driver_simulator`, `notification_router` y `dashboard_aggregator` consumen el mismo topic con group IDs distintos, cada uno avanza su offset de forma independiente.

### RabbitMQ para comandos y notificaciones

Los comandos y notificaciones necesitan:
- **Routing por tipo:** el Topic Exchange `notifications` enruta mensajes a colas específicas con routing keys (`notify.client.sms` → `client_sms_queue`, `notify.driver.push` → `driver_push_queue`).
- **Baja latencia:** los comandos y notificaciones deben entregarse rápido, sin el overhead de un log persistente.
- **Work Queue con balanceo:** múltiples réplicas de workers comparten una cola y RabbitMQ distribuye en round-robin, demostrando escalabilidad horizontal.
- **Consumo destructivo:** una vez que un worker procesa una notificación, el mensaje se elimina de la cola. No hay necesidad de replay.

### Por qué no usar un solo broker

Usar solo Kafka para notificaciones sería innecesariamente complejo: no necesitamos replay ni persistencia duradera para un SMS. Usar solo RabbitMQ para eventos perdería la capacidad de replay y el orden garantizado por partición. El diseño híbrido usa cada herramienta para lo que hace mejor.

## Apache Kafka en modo KRaft (sin ZooKeeper)

### Qué es KRaft

KRaft (Kafka Raft) es el modo de gestión de metadatos introducido en Kafka 3.3+ que elimina la dependencia de ZooKeeper. En el modo tradicional, Kafka dependía de ZooKeeper para:
- Elegir el controller del clúster.
- Almacenar la configuración de topics y particiones.
- Gestionar la pertenencia de los brokers.

KRaft reemplaza esto con un protocolo de consenso Raft interno donde un nodo actúa simultáneamente como broker (maneja mensajes) y controller (gestiona metadatos).

### Configuración en docker-compose.yml

```yaml
KAFKA_NODE_ID: 1
KAFKA_PROCESS_ROLES: broker,controller
KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
```

- `KAFKA_NODE_ID: 1` — identificador único del nodo.
- `KAFKA_PROCESS_ROLES: broker,controller` — nodo combinado que actúa como broker y controller.
- `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093` — lista de votantes del quórum Raft (un solo nodo para el MVP).
- `KAFKA_LISTENERS` — dos listeners: `PLAINTEXT` para clientes (9092) y `CONTROLLER` para el protocolo Raft interno (9093).
- `CLUSTER_ID` — UUID base64 de 22 caracteres, idéntico en todos los nodos.

### Por qué KRaft en lugar de ZooKeeper

- **Menos componentes:** un contenedor menos que levantar y monitorear.
- **Menor consumo de RAM:** ZooKeeper típicamente necesita 128-256 MB adicionales.
- **Simplicidad operacional:** un solo proceso, un solo punto de configuración.
- **Futuro de Kafka:** ZooKeeper está en proceso de deprecación oficial. KRaft es el modo recomendado para nuevas instalaciones desde Kafka 3.3.

## Nota sobre atomicidad: Kafka + PostgreSQL

El `order_processor` escribe en PostgreSQL y luego publica en Kafka en dos pasos separados. No hay una transacción atómica que abarque ambos sistemas. Esto significa que si el `order_processor` cae después del `INSERT` en PostgreSQL pero antes de publicar en Kafka, el evento se pierde.

### Qué implica para el MVP

- El pedido queda registrado en PostgreSQL pero sin su evento en Kafka.
- El `dashboard_aggregator` no verá ese pedido porque solo reconstruye desde Kafka.
- Los consumidores reactivos (`driver_simulator`, `notification_router`) no reaccionarán.

### Por qué es aceptable para el contexto universitario

- El sistema es un MVP que demuestra patrones, no un sistema de producción.
- Para solucionar esto en producción se usaría el **Patrón Transactional Outbox**: escribir el evento en una tabla `outbox_events` dentro de la misma transacción de PostgreSQL, y un proceso separado (Debezium/relay) leería esa tabla y publicaría en Kafka. Esto garantiza que el evento se publique exactamente una vez.
- Documentar esta limitación es parte del análisis crítico del proyecto.

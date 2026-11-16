# Rappi-Stream — Guía Completa de Evaluación

> **Para la presentación de 15 minutos.** Cubre arquitectura, código, patrones, Kafka, RabbitMQ, Docker y base de datos con ejemplos reales del proyecto.

---

## Tabla de contenidos

1. [Resumen del sistema](#1-resumen-del-sistema)
2. [Flujo completo de datos paso a paso](#2-flujo-completo-de-datos-paso-a-paso)
3. [Por qué Kafka Y RabbitMQ al mismo tiempo](#3-por-qué-kafka-y-rabbitmq-al-mismo-tiempo)
4. [Apache Kafka — conceptos y uso en el proyecto](#4-apache-kafka--conceptos-y-uso-en-el-proyecto)
5. [RabbitMQ — conceptos y uso en el proyecto](#5-rabbitmq--conceptos-y-uso-en-el-proyecto)
6. [PostgreSQL — esquema y persistencia](#6-postgresql--esquema-y-persistencia)
7. [Módulo common — contratos compartidos](#7-módulo-common--contratos-compartidos)
8. [Microservicio order_api](#8-microservicio-order_api)
9. [Microservicio order_processor](#9-microservicio-order_processor)
10. [Microservicio driver_simulator](#10-microservicio-driver_simulator)
11. [Microservicio notification_router](#11-microservicio-notification_router)
12. [Workers SMS y Push](#12-workers-sms-y-push)
13. [Microservicio dashboard_aggregator](#13-microservicio-dashboard_aggregator)
14. [Microservicio dashboard_backend](#14-microservicio-dashboard_backend)
15. [WebSocket y el dashboard HTML](#15-websocket-y-el-dashboard-html)
16. [POO Avanzada — patrones de diseño](#16-poo-avanzada--patrones-de-diseño)
17. [Principios SOLID](#17-principios-solid)
18. [Pruebas unitarias](#18-pruebas-unitarias)
19. [Docker — Dockerfiles multietapa](#19-docker--dockerfiles-multietapa)
20. [Docker Compose — orquestación completa](#20-docker-compose--orquestación-completa)
21. [Términos técnicos clave para la evaluación](#21-términos-técnicos-clave-para-la-evaluación)
22. [Preguntas frecuentes de evaluación y respuestas](#22-preguntas-frecuentes-de-evaluación-y-respuestas)

---

## 1. Resumen del sistema

Rappi-Stream es una plataforma de logística en tiempo real que maneja el ciclo de vida completo de un pedido de comida: desde que el cliente lo crea hasta que el repartidor lo entrega, con notificaciones y seguimiento visual en vivo.

### Los 8 microservicios y su rol

| Servicio | Puerto | Tecnología de mensajería | Rol |
|---|---|---|---|
| `order_api` | 8080 | Produce → RabbitMQ | Recibe pedidos del browser |
| `order_processor` | 8081 | Consume RabbitMQ, Produce → Kafka | Procesa y asigna repartidor |
| `driver_simulator` | 8082 | Consume y Produce Kafka | Simula el viaje de entrega |
| `notification_router` | 8083 | Consume Kafka, Produce → RabbitMQ | Puente de notificaciones |
| `sms_worker` | — | Consume RabbitMQ | Envía SMS al cliente (×2) |
| `push_notification_worker` | — | Consume RabbitMQ | Envía Push al repartidor (×2) |
| `dashboard_aggregator` | 8086 | Consume Kafka | Reconstruye estado + llama backend |
| `dashboard_backend` | 8087 | Sirve WebSocket | Dashboard HTML en tiempo real |

### Infraestructura de soporte

| Componente | Versión | Uso |
|---|---|---|
| Apache Kafka (KRaft) | 7.6.0 | Log de eventos inmutable — System of Record |
| RabbitMQ | 3.13 | Work Queue (comandos) + Topic Exchange (notificaciones) |
| PostgreSQL | 16.2 | Persistencia transaccional y auditoría |

---

## 2. Flujo completo de datos paso a paso

Este es el camino que recorre **un solo pedido** desde el click del usuario hasta la entrega:

```
Browser
  │
  ├─ POST /api/orders ─────────────────────────────────────────────────────────┐
  │                                                                              ↓
  │                                                                    dashboard_backend :8087
  │                                                                    (proxy al order_api)
  │                                                                              │
  │                                                              POST /api/orders → order_api :8080
  │                                                                              │
  │                                                              [1] Valida CreateOrderRequest (@Valid)
  │                                                              [2] Genera orderId (UUID)
  │                                                              [3] Crea OrderCreatedCommand (record)
  │                                                              [4] rabbitTemplate.convertAndSend
  │                                                                  → new_orders_queue (RabbitMQ)
  │
  │  ◄── [RabbitMQ Work Queue] ──────────────────────────────────────────────────
  │
  │                                                                    order_processor :8081
  │                                                                    @RabbitListener("new_orders_queue")
  │                                                              [5]  new Pedido(...)   [Estado: CREADO]
  │                                                              [6]  repositorio.guardar(pedido)
  │                                                                   → INSERT INTO orders
  │                                                              [7]  publicarOrderCreated(pedido)
  │                                                                   → Kafka "order_events" / key=orderId
  │                                                              [8]  Thread.sleep(3000ms) "buscando..."
  │                                                              [9]  RoundRobinStrategy.asignar(pedido)
  │                                                                   → pedido transiciona CREADO→ASIGNADO
  │                                                              [10] repositorio.actualizar(pedido)
  │                                                                   → UPDATE orders SET status='ASIGNADO'
  │                                                              [11] publicarDriverAssigned(pedido)
  │                                                                   → Kafka "order_events" / key=orderId
  │
  │  ◄── [Kafka topic: order_events, clave=orderId] ─────────────────────────────
  │       (3 consumidores independientes reciben TODOS los eventos)
  │
  │  ┌─────────────────────────────┬────────────────────────────┐
  │  ↓                             ↓                            ↓
  │
  │ driver_simulator :8082     notification_router :8083    dashboard_aggregator :8086
  │ (grupo: driver-sim-group)  (grupo: notif-router-group)  (grupo: dash-agg-group)
  │
  │ Filtra: solo DRIVER_ASSIGNED   Filtra: DRIVER_ARRIVING    Procesa TODOS los eventos
  │                                y ORDER_DELIVERED           → actualiza ConcurrentHashMap
  │ @Async simularCicloEntrega()   → Strategy pattern          → persiste order_events tabla
  │                                → NotificationCommand       → actualiza orders tabla
  │ sleep(5s)                      → rabbitTemplate.send       → POST /internal/update
  │ → DELIVERY_STARTED (Kafka)      "notifications" exchange     → dashboard_backend
  │ sleep(8s)                      con routing key              → messagingTemplate.convertAndSend
  │ → DRIVER_ARRIVING  (Kafka)      "notify.client.sms"           "/topic/orders" (WebSocket)
  │ sleep(5s)                      o "notify.driver.push"
  │ → ORDER_DELIVERED  (Kafka)
  │
  │                              [RabbitMQ Topic Exchange]
  │                              ┌──────────────────────────┐
  │                              │  "notify.client.*"        │──→ sms_worker (×2 replicas)
  │                              │  "notify.driver.*"        │──→ push_notification_worker (×2)
  │                              └──────────────────────────┘
  │
  └─ WebSocket /topic/orders ◄──── dashboard_backend ◄──── dashboard_aggregator
       (STOMP broadcast)           (InternalUpdateController)   (5 veces: por cada evento)

Browser recibe 5 DTOs:
  CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO
  Cada uno actualiza: tabla, métricas, stepper, animación de la moto
```

---

## 3. Por qué Kafka Y RabbitMQ al mismo tiempo

Esta es la pregunta más importante de la evaluación. La respuesta está en el **propósito** de cada tecnología:

### RabbitMQ — para comandos que se consumen una sola vez

**Work Queue (`new_orders_queue`):** El cliente pide una hamburguesa. Ese comando debe procesarse **exactamente una vez**. Si hay 10 servidores de `order_processor`, solo uno debe tomar el pedido. RabbitMQ es perfecto para esto: es un broker de mensajes que enruta y elimina el mensaje una vez consumido.

```
Browser ──POST──→ order_api ──→ [new_orders_queue] ──→ order_processor (uno de varios)
                                   RabbitMQ                (Work Queue: 1 consumidor toma el mensaje)
```

**Topic Exchange (`notifications`):** Las notificaciones son comandos dirigidos a destinatarios específicos. SMS va al `sms_worker`, Push va al `push_notification_worker`. El exchange enruta basado en routing keys.

### Kafka — para eventos que múltiples sistemas necesitan

**Topic `order_events`:** El estado del pedido debe ser conocido por **tres sistemas distintos al mismo tiempo**: el simulador de repartidor necesita saber cuándo fue asignado, el router de notificaciones necesita disparar alertas, y el aggregator necesita actualizar el dashboard. Con RabbitMQ un mensaje solo llega a uno de los consumidores. Con Kafka **cada grupo de consumidores recibe todos los mensajes de forma independiente**.

Además, Kafka es **inmutable**: los eventos no se borran. Si el `dashboard_aggregator` se reinicia, puede leer desde el principio (`auto-offset-reset=earliest`) y reconstruir el estado completo.

```
order_processor ──→ [order_events] ──→ driver_simulator     (cada uno recibe todos los eventos)
                      Kafka              notification_router  (grupos de consumidores independientes)
                                         dashboard_aggregator
```

### Resumen en una tabla

| Criterio | RabbitMQ | Kafka |
|---|---|---|
| ¿Cuántos consumen? | Uno (Work Queue) o por routing key | Todos los grupos independientemente |
| ¿Se borra el mensaje? | Sí, al ser consumido | No, es inmutable (log) |
| ¿Reintento al reiniciar? | No (perdido) | Sí (desde el offset guardado) |
| Uso en este proyecto | Comandos de entrada + notificaciones | Log de estado del pedido |
| Patrón | Work Queue + Topic Exchange | Event Sourcing |

---

## 4. Apache Kafka — conceptos y uso en el proyecto

### Conceptos fundamentales

**Topic:** Canal de mensajes. En este proyecto hay un solo topic: `order_events`.

**Partición:** Un topic se divide en particiones para paralelismo. `order_events` tiene **3 particiones**. Los mensajes con la misma clave van siempre a la misma partición — esto garantiza **orden** para un pedido específico.

**Clave de partición (`orderId`):** Todos los eventos de un pedido (CREADO, ASIGNADO, EN_CAMINO, LLEGANDO, ENTREGADO) usan el mismo `orderId` como clave. Kafka los envía a la misma partición, garantizando que se procesen en orden cronológico.

```java
// En KafkaOrderEventPublisher.java y DeliverySimulatorService.java:
kafkaTemplate.send(TOPIC, orderId, envelopeJson);
//                        ↑ clave de partición
```

**Offset:** Número de posición de un mensaje en una partición. Kafka recuerda hasta dónde llegó cada grupo de consumidores.

**Grupo de consumidores (Consumer Group):** Cada microservicio tiene su propio `groupId`. Kafka envía todos los mensajes a cada grupo independientemente.

| Microservicio | groupId | auto-offset-reset |
|---|---|---|
| `driver_simulator` | `driver-simulator-group` | `earliest` |
| `notification_router` | `notification-router-group` | `earliest` |
| `dashboard_aggregator` | `dashboard-aggregator-group` | `earliest` |

**`auto-offset-reset=earliest`:** Si el grupo no tiene offset guardado (primera vez que arranca), lee desde el primer mensaje del topic. Esto permite que el `dashboard_aggregator` reconstruya el estado completo al reiniciarse.

**KRaft mode (sin ZooKeeper):** Kafka 7.6 puede funcionar sin ZooKeeper. Este proyecto lo usa en modo KRaft donde el propio broker gestiona su metadatos. Se configura con:

```yaml
KAFKA_PROCESS_ROLES: broker,controller
KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
```

**`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`:** Los topics no se crean automáticamente. Se crea manualmente con `create-topics.sh` para tener control total sobre particiones y replicación.

### El envelope de eventos

Todos los eventos viajan envueltos en `OrderEventEnvelope`:

```java
// common/src/main/java/com/rappistream/common/events/OrderEventEnvelope.java
public record OrderEventEnvelope(
    String eventId,          // UUID único — clave de idempotencia
    String orderId,          // ID del pedido — clave de partición Kafka
    OrderEventType eventType,// ORDER_CREATED, DRIVER_ASSIGNED, etc.
    Instant timestamp,       // cuándo ocurrió
    String payload           // JSON del payload específico del evento
) { ... }
```

El `payload` es un String JSON que varía según el tipo de evento:

```java
// DRIVER_ASSIGNED payload:
public record DriverAssignedPayload(String driverId, String driverName) {}

// ORDER_DELIVERED payload:
public record OrderDeliveredPayload(String driverId, String driverName, Instant deliveredAt) {}
```

### Idempotencia en Kafka

El `dashboard_aggregator` puede procesar el mismo evento dos veces (si se reinicia y lee desde `earliest`). Se previene con dos capas:

```java
// 1. Verificación previa (rápida):
if (eventRepo.existsByEventId(envelope.eventId())) return null;

// 2. Restricción UNIQUE en la tabla (doble barrera para concurrencia):
// CREATE TABLE order_events ( event_id VARCHAR(36) NOT NULL UNIQUE ... )
// Si dos hilos procesan el mismo evento simultáneamente, el segundo falla con
// DataIntegrityViolationException → se captura y retorna null silenciosamente.
```

### Crear el topic (create-topics.sh)

```bash
#!/bin/bash
echo 'Esperando que Kafka este disponible...'
sleep 15
echo 'Creando topic order_events (3 particiones)...'
/usr/bin/kafka-topics \
  --bootstrap-server kafka:9092 \
  --create --if-not-exists \
  --topic order_events \
  --partitions 3 \
  --replication-factor 1
```

- `--partitions 3`: 3 particiones para paralelismo (pueden procesar 3 mensajes simultáneamente)
- `--replication-factor 1`: sin réplicas (solo hay un broker en desarrollo)
- `--if-not-exists`: idempotente, no falla si el topic ya existe
- Se ejecuta como init container que depende de que Kafka esté healthy

---

## 5. RabbitMQ — conceptos y uso en el proyecto

### Conceptos fundamentales

**Exchange:** Punto de entrada de mensajes. Los productores envían al exchange, no directamente a las colas.

**Queue:** Cola donde se almacenan los mensajes hasta ser consumidos.

**Binding:** Regla que conecta un exchange con una queue usando una routing key o patrón.

**Routing Key:** Etiqueta del mensaje que el exchange usa para decidir a qué queues enviarlo.

### Work Queue: `new_orders_queue`

La pattern más simple. El producer envía directamente a la queue (usando el default exchange). Múltiples consumers compiten por los mensajes — cada mensaje va a **uno solo** (round-robin).

```java
// RabbitMqConfig.java (order_api)
@Bean public Queue newOrdersQueue() { return new Queue("new_orders_queue", true); }
//                                                                          ↑ durable=true
// (true = la queue sobrevive reinicios del broker)

// OrderPublisherService.java
rabbitTemplate.convertAndSend(RabbitMqConfig.NEW_ORDERS_QUEUE, comando);
// Envía al default exchange "" con routing key = nombre de la queue
```

### Topic Exchange: `notifications`

Exchange de tipo `topic` que enruta por patrones. Se usa para las notificaciones porque el router no sabe de antemano si habrá 1 o 100 workers de cada tipo.

```java
// RabbitMqConfig.java (notification_router)
@Bean public TopicExchange notificationsExchange() {
    return new TopicExchange("notifications", true, false);
}

// Bindings con patrones wildcard:
// notify.client.*  →  client_sms_queue    (cualquier notificación para cliente)
// notify.driver.*  →  driver_push_queue   (cualquier notificación para repartidor)
```

**Wildcard `*`**: reemplaza exactamente una palabra separada por punto.
- `notify.client.sms` coincide con `notify.client.*` ✓
- `notify.driver.push` coincide con `notify.driver.*` ✓

```java
// NotificationRouterService.java — cómo se elige la routing key:
String routingKey = switch(cmd.type()) {
    case SMS  -> "notify.client.sms";
    case PUSH -> "notify.driver.push";
};
rabbitTemplate.convertAndSend("notifications", routingKey, cmd);
```

### Serialización en RabbitMQ

RabbitMQ transmite bytes. Para enviar/recibir objetos Java se usa `Jackson2JsonMessageConverter`:

```java
// RabbitMqConfig.java
@Bean
public Jackson2JsonMessageConverter messageConverter(ObjectMapper om) {
    return new Jackson2JsonMessageConverter(om);
}
// El ObjectMapper tiene JavaTimeModule → serializa Instant como ISO-8601
// sin él, Instant se serializa como array [2026,11,12,19,57,45,...] → error al deserializar
```

### Réplicas de workers (escalabilidad horizontal)

```yaml
# docker-compose.yml
sms_worker:
  deploy:
    replicas: 2    # 2 instancias del mismo worker

push_notification_worker:
  deploy:
    replicas: 2    # 2 instancias del mismo worker
```

Con `replicas: 2`, hay dos containers del mismo servicio consumiendo de la misma queue. RabbitMQ distribuye los mensajes en **round-robin** entre ellos. Esto demuestra balanceo de carga: si llegan 4 notificaciones SMS, cada worker procesa 2.

> **¿Por qué no tienen `container_name` fijo?** Porque Docker no puede tener dos containers con el mismo nombre. Con `replicas`, Docker Compose asigna nombres automáticamente (`rappi-stream-sms_worker-1`, `rappi-stream-sms_worker-2`).

---

## 6. PostgreSQL — esquema y persistencia

### Las tres tablas

```sql
-- Tabla 1: drivers — repartidores disponibles
CREATE TABLE drivers (
    id        VARCHAR(36)  PRIMARY KEY,  -- "D-001", "D-002", etc.
    name      VARCHAR(100) NOT NULL,
    available BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Tabla 2: orders — estado actual de cada pedido
CREATE TABLE orders (
    id            VARCHAR(36)  PRIMARY KEY,  -- UUID del pedido
    customer_name VARCHAR(150) NOT NULL,
    product       VARCHAR(200) NOT NULL,
    quantity      INTEGER      NOT NULL CHECK (quantity > 0),
    address       TEXT         NOT NULL,
    driver_id     VARCHAR(36)  REFERENCES drivers(id),  -- FK nullable
    status        VARCHAR(30)  NOT NULL DEFAULT 'CREADO',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Tabla 3: order_events — log de auditoría de eventos
CREATE TABLE order_events (
    id              BIGSERIAL    PRIMARY KEY,  -- autoincremental
    event_id        VARCHAR(36)  NOT NULL UNIQUE,  -- idempotencia
    order_id        VARCHAR(36)  NOT NULL REFERENCES orders(id),
    event_type      VARCHAR(50)  NOT NULL,  -- ORDER_CREATED, DRIVER_ASSIGNED, etc.
    event_timestamp TIMESTAMPTZ  NOT NULL,
    payload         TEXT         NOT NULL,  -- JSON del payload
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

### Quién escribe qué

| Tabla | INSERT | UPDATE |
|---|---|---|
| `drivers` | Solo `init.sql` al iniciar | Nadie (en este MVP) |
| `orders` | `order_processor` (cuando crea el pedido) | `dashboard_aggregator` (en cada evento) |
| `order_events` | `dashboard_aggregator` (en cada evento) | Nadie (es inmutable) |

### JPA — mapeo de entidades

**`spring.jpa.hibernate.ddl-auto=validate`:** Hibernate NO crea ni modifica tablas. Solo verifica que las entidades coincidan con el esquema creado por `init.sql`. Esto es importante en producción: separación entre el esquema de BD y el código.

```java
// order_processor: PedidoEntity.java
@Entity @Table(name = "orders")
public class PedidoEntity {
    @Id @Column(name = "id", length = 36)        private String id;
    @Column(name = "customer_name", length = 150) private String customerName;
    // ... (todos los campos mapeados al esquema)
}

// dashboard_aggregator: OrderEventEntity.java
@Entity @Table(name = "order_events")
public class OrderEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", length = 36, unique = true)  private String eventId;
    @Column(name = "order_id", length = 36)                 private String orderId;
    // ...
}
```

### Repositorios Spring Data JPA

```java
// Solo una interfaz — Spring genera la implementación automáticamente:
public interface SpringDataPedidoRepository extends JpaRepository<PedidoEntity, String> {}

public interface SpringDataDriverRepository extends JpaRepository<DriverEntity, String> {
    List<DriverEntity> findByAvailableTrue();  // Spring genera: WHERE available = true
}

public interface SpringDataOrderEventRepository extends JpaRepository<OrderEventEntity, Long> {
    boolean existsByEventId(String eventId);  // Spring genera: SELECT COUNT(*) > 0 WHERE event_id = ?
}
```

### Separación del dominio (DIP + Adaptador)

La entidad `Pedido` del dominio no es una entidad JPA. Se mantiene pura. El adaptador hace la traducción:

```
Pedido (dominio, POJO puro)
    ↕ traducción
JpaOrderRepositoryAdapter (infraestructura)
    ↕ JPA
PedidoEntity (@Entity)
    ↕ JDBC
PostgreSQL
```

```java
// JpaOrderRepositoryAdapter.java
@Component
public class JpaOrderRepositoryAdapter implements OrderRepositoryPort {
    @Override
    public void guardar(Pedido pedido) {
        pedidoRepo.save(toEntity(pedido));  // Pedido → PedidoEntity → SQL INSERT
    }

    @Override
    public List<Driver> obtenerRepartidoresDisponibles() {
        return driverRepo.findByAvailableTrue()
               .stream()
               .map(e -> new Driver(e.getId(), e.getName(), e.isAvailable()))
               .toList();  // DriverEntity → Driver (dominio)
    }
}
```

---

## 7. Módulo common — contratos compartidos

El módulo `common` es una librería JAR (no un microservicio) que todos los microservicios incluyen como dependencia. Define los **contratos de mensajería** — el lenguaje que hablan entre sí.

### Enums

```java
// OrderStatus — estados del pedido (para el DTO del dashboard)
public enum OrderStatus { CREADO, ASIGNADO, EN_CAMINO, LLEGANDO, ENTREGADO }

// OrderEventType — tipos de eventos en Kafka
public enum OrderEventType {
    ORDER_CREATED, DRIVER_ASSIGNED, DELIVERY_STARTED, DRIVER_ARRIVING, ORDER_DELIVERED
}

// NotificationType — tipo de canal de notificación
public enum NotificationType { SMS, PUSH }
```

### Java Records — inmutabilidad y Fail-Fast

Los `record` de Java 17 son clases inmutables con constructor, getters, `equals`, `hashCode` y `toString` generados automáticamente. En este proyecto se usan para todos los contratos.

**`OrderCreatedCommand`** — lo que viaja por RabbitMQ de `order_api` a `order_processor`:

```java
public record OrderCreatedCommand(
    String commandId,     // UUID único del comando
    String orderId,       // UUID del pedido (generado en order_api)
    String customerName,
    String product,
    int quantity,
    String address,
    Instant issuedAt      // cuándo se creó
) {
    // Constructor compacto — validación Fail-Fast:
    // Si cualquier campo es inválido, falla INMEDIATAMENTE antes de que el objeto exista.
    // Esto evita que objetos inválidos viajen por el sistema.
    public OrderCreatedCommand {
        Objects.requireNonNull(commandId, "commandId no puede ser nulo");
        if (commandId.isBlank()) throw new IllegalArgumentException("commandId no puede estar vacío");
        Objects.requireNonNull(orderId, "orderId no puede ser nulo");
        if (customerName.isBlank()) throw new IllegalArgumentException(...);
        if (quantity <= 0) throw new IllegalArgumentException("quantity debe ser mayor que cero");
        // etc.
    }
}
```

**`OrderEventEnvelope`** — el wrapper de todos los eventos Kafka:

```java
public record OrderEventEnvelope(
    String eventId,           // UUID — clave de idempotencia en BD
    String orderId,           // clave de partición Kafka
    OrderEventType eventType,
    Instant timestamp,
    String payload            // JSON del payload específico (como String)
) { /* Fail-Fast en constructor compacto */ }
```

**`OrderStatusDTO`** — lo que el `dashboard_aggregator` envía al `dashboard_backend` (y de ahí al browser):

```java
public record OrderStatusDTO(
    String orderId, String customerName, String product, int quantity, String address,
    String driverId, String driverName,  // pueden ser null si aún no hay repartidor
    OrderStatus status, Instant lastUpdated
) {}
```

---

## 8. Microservicio order_api

**Responsabilidad:** Puerta de entrada HTTP. Valida la solicitud y la encola en RabbitMQ.

### Validación con Bean Validation

```java
// CreateOrderRequest.java
public record CreateOrderRequest(
    @NotBlank(message = "customerName no puede estar vacío")  String customerName,
    @NotBlank(message = "product no puede estar vacío")       String product,
    @NotNull @Min(value = 1, message = "quantity debe ser ≥ 1") Integer quantity,
    @NotBlank(message = "address no puede estar vacía")       String address
) {}

// OrderController.java
@PostMapping
public ResponseEntity<Map<String, String>> crearPedido(
        @Valid @RequestBody CreateOrderRequest request) {
    // @Valid activa la validación. Si falla → 400 Bad Request automáticamente.
    String orderId = publisherService.publicarComando(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED)  // 202, no 200
           .body(Map.of("orderId", orderId, "status", "ACEPTADO", "message", "..."));
}
```

**¿Por qué 202 Accepted y no 200 OK?** Porque el pedido fue recibido y encolado, pero aún no procesado. El procesamiento es asíncrono.

### Publicación a RabbitMQ

```java
// OrderPublisherService.java
public String publicarComando(CreateOrderRequest request) {
    String orderId   = UUID.randomUUID().toString();  // genera el ID aquí
    String commandId = UUID.randomUUID().toString();

    OrderCreatedCommand comando = new OrderCreatedCommand(
        commandId, orderId, request.customerName(),
        request.product(), request.quantity(), request.address(), Instant.now()
    );
    // convertAndSend serializa el comando a JSON con Jackson
    rabbitTemplate.convertAndSend(RabbitMqConfig.NEW_ORDERS_QUEUE, comando);
    return orderId;
}
```

---

## 9. Microservicio order_processor

Este es el microservicio más complejo. Implementa todos los patrones de POO del proyecto.

### Patrón State — ciclo de vida del pedido

**Problema:** El pedido tiene estados (CREADO, ASIGNADO, EN_CAMINO, LLEGANDO, ENTREGADO) y reglas de transición estrictas. Sin el patrón State, habría `if-else` gigantes en cada método.

**Solución:** Cada estado es una clase que sabe qué operaciones son válidas desde él.

```
                  asignarRepartidor()
EstadoCreado ─────────────────────────→ EstadoAsignado
                                              │ iniciarEntrega()
                                              ↓
                                       EstadoEnCamino
                                              │ registrarLlegada()
                                              ↓
                                       EstadoLlegando
                                              │ completarEntrega()
                                              ↓
                                       EstadoEntregado (terminal)
```

```java
// OrderState.java — interfaz del patrón State
public interface OrderState {
    void asignarRepartidor(Pedido pedido, String driverId, String driverName);
    void iniciarEntrega(Pedido pedido);
    void registrarLlegada(Pedido pedido);
    void completarEntrega(Pedido pedido);
    String nombre();
}

// EstadoCreado.java — solo asignarRepartidor es válido
public class EstadoCreado implements OrderState {
    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        pedido.aplicarAsignacion(driverId, driverName);    // guarda driverId y driverName
        pedido.setEstado(new EstadoAsignado());             // transición de estado
    }
    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException("No se puede iniciar entrega en estado CREADO");
    }
    // ... todos los otros métodos lanzan IllegalStateException
}

// Pedido.java — el contexto del patrón State
public class Pedido {
    OrderState estadoActual;  // referencia al estado actual

    public void asignarRepartidor(String driverId, String driverName) {
        estadoActual.asignarRepartidor(this, driverId, driverName);
        // No hay if-else. El estado actual decide qué hacer.
    }
}
```

### Patrón Strategy — asignación de repartidores

**Problema:** ¿Cómo asignar repartidores? Podría ser RoundRobin, por cercanía, por calificación... La lógica debe ser intercambiable sin modificar el servicio.

```java
// DriverAssignmentStrategy.java — interfaz del patrón Strategy
public interface DriverAssignmentStrategy {
    void asignar(Pedido pedido);
}

// RoundRobinAssignmentStrategy.java — una implementación concreta
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {
    private final List<Driver> repartidores;
    private final AtomicInteger cursor = new AtomicInteger(0);  // thread-safe

    @Override
    public void asignar(Pedido pedido) {
        List<Driver> disponibles = repartidores.stream()
                                               .filter(Driver::isAvailable)
                                               .toList();
        if (disponibles.isEmpty())
            throw new IllegalStateException("No hay repartidores disponibles");
        int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
        Driver seleccionado = disponibles.get(indice);
        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
        // pedido.asignarRepartidor → EstadoCreado.asignarRepartidor → transición CREADO→ASIGNADO
    }
}
```

**`AtomicInteger`:** Variable entera thread-safe. Si dos pedidos llegan al mismo tiempo, `getAndIncrement()` garantiza que cada hilo obtiene un número diferente sin condiciones de carrera.

### Inversión de Dependencias (DIP) — puertos e interfaces

```java
// OrderProcessorService.java — el servicio NO conoce Kafka ni JPA ni RoundRobin directamente
@Service
public class OrderProcessorService {
    private final OrderRepositoryPort repositorio;      // interfaz, no JpaRepository
    private final OrderEventPublisherPort publicador;   // interfaz, no KafkaTemplate
    private final DriverAssignmentStrategy estrategia;  // interfaz, no RoundRobin

    // Constructor injection — Spring inyecta las implementaciones concretas
    public OrderProcessorService(OrderRepositoryPort r, OrderEventPublisherPort p,
                                  DriverAssignmentStrategy e) {
        this.repositorio = r; this.publicador = p; this.estrategia = e;
    }

    public void procesarComando(OrderCreatedCommand comando) {
        Pedido pedido = new Pedido(comando.orderId(), comando.customerName(),
                                   comando.product(), comando.quantity(), comando.address());
        repositorio.guardar(pedido);            // → JpaOrderRepositoryAdapter → PedidoEntity → SQL
        publicador.publicarOrderCreated(pedido); // → KafkaOrderEventPublisher → Kafka
        Thread.sleep(assignmentDelayMs);         // animación "buscando repartidor"
        estrategia.asignar(pedido);              // → RoundRobinStrategy → State pattern
        repositorio.actualizar(pedido);
        publicador.publicarDriverAssigned(pedido);
    }
}
```

### DomainConfig — creación de beans del dominio

```java
@Configuration
public class DomainConfig {
    @Bean
    public DriverAssignmentStrategy driverAssignmentStrategy(OrderRepositoryPort repositorio) {
        List<Driver> repartidores = repositorio.obtenerRepartidoresDisponibles();
        if (repartidores.isEmpty()) {
            // Si la BD no tiene datos, usa repartidores por defecto (semilla)
            repartidores = List.of(
                new Driver("D-001", "Carlos Mendoza",   true),
                new Driver("D-002", "Laura Rodriguez",  true),
                // ...
            );
        }
        return new RoundRobinAssignmentStrategy(repartidores);
    }
}
```

---

## 10. Microservicio driver_simulator

**Responsabilidad:** Simular el ciclo de vida físico de la entrega.

### Filtrado de eventos en el listener

```java
// DriverAssignedListener.java
@KafkaListener(topics = "order_events", groupId = "driver-simulator-group")
public void onOrderEvent(String mensajeJson) {
    OrderEventEnvelope envelope = objectMapper.readValue(mensajeJson, OrderEventEnvelope.class);

    // Solo procesa DRIVER_ASSIGNED — ignora todos los demás
    if (envelope.eventType() != OrderEventType.DRIVER_ASSIGNED) {
        log.debug("Ignorando evento de tipo {}", envelope.eventType());
        return;
    }

    DriverAssignedPayload payload = objectMapper.readValue(
        envelope.payload(), DriverAssignedPayload.class
    );
    deliverySimulatorService.simularCicloEntrega(envelope, payload);
}
```

### `@Async` — ejecución en segundo plano

```java
// DeliverySimulatorService.java
@Async  // Spring ejecuta este método en un hilo separado (ThreadPoolTaskExecutor)
public void simularCicloEntrega(OrderEventEnvelope envelope, DriverAssignedPayload payload) {
    String orderId = envelope.orderId();

    Thread.sleep(deliveryStartedDelayMs);  // 5 segundos: "el repartidor va al restaurante"
    publicarEvento(orderId, DELIVERY_STARTED, new DeliveryStartedPayload(...));

    Thread.sleep(driverArrivingDelayMs);   // 8 segundos: "está llegando al cliente"
    publicarEvento(orderId, DRIVER_ARRIVING, new DriverArrivingPayload(...));

    Thread.sleep(orderDeliveredDelayMs);   // 5 segundos: "entregó"
    publicarEvento(orderId, ORDER_DELIVERED, new OrderDeliveredPayload(...));
}
```

**¿Por qué `@Async`?** Sin él, el listener de Kafka quedaría bloqueado durante 18 segundos (5+8+5) sin poder procesar más eventos. Con `@Async`, el `simularCicloEntrega` se ejecuta en otro hilo y el listener está libre inmediatamente para el siguiente mensaje.

Los delays son **configurables por variable de entorno** (sin recompilar):

```yaml
# docker-compose.yml
driver_simulator:
  environment:
    SIMULATOR_DELAY_DELIVERY_STARTED_MS: 5000
    SIMULATOR_DELAY_DRIVER_ARRIVING_MS: 8000
    SIMULATOR_DELAY_ORDER_DELIVERED_MS: 5000
```

```java
// application.properties (valores por defecto si no viene env var)
@Value("${simulator.delay.delivery-started-ms:8000}") private long deliveryStartedDelayMs;
```

---

## 11. Microservicio notification_router

**Responsabilidad:** Puente entre Kafka (eventos de estado) y RabbitMQ (comandos de notificación).

### Patrón Strategy para notificaciones

Cada tipo de notificación es una estrategia diferente. Se puede agregar nuevos tipos sin modificar el servicio principal.

```java
// NotificationStrategy.java — interfaz
public interface NotificationStrategy {
    boolean aplica(OrderEventType eventType);
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}

// DriverArrivingSmsStrategy.java — notifica al cliente cuando el repartidor llega
@Component
public class DriverArrivingSmsStrategy implements NotificationStrategy {
    @Override
    public boolean aplica(OrderEventType t) { return t == OrderEventType.DRIVER_ARRIVING; }

    @Override
    public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
        DriverArrivingPayload payload = objectMapper.readValue(envelope.payload(), ...);
        String message = "Tu repartidor " + payload.driverName() +
                         " está llegando. Tiempo estimado: " + payload.estimatedArrival() + ".";
        return Optional.of(new NotificationCommand(
            UUID.randomUUID().toString(), envelope.orderId(), envelope.orderId(),
            NotificationType.SMS, message, Instant.now()
        ));
    }
}

// NotificationRouterService.java — itera estrategias (Strategy + polimorfismo)
@Service
public class NotificationRouterService {
    private final List<NotificationStrategy> estrategias;  // Spring inyecta TODAS las implementaciones

    public void procesarEvento(OrderEventEnvelope envelope) {
        for (NotificationStrategy estrategia : estrategias) {
            if (estrategia.aplica(envelope.eventType())) {
                estrategia.construir(envelope).ifPresent(cmd -> {
                    String routingKey = switch(cmd.type()) {
                        case SMS  -> "notify.client.sms";
                        case PUSH -> "notify.driver.push";
                    };
                    rabbitTemplate.convertAndSend("notifications", routingKey, cmd);
                });
                return;
            }
        }
    }
}
```

**Truco de Spring:** Al declarar `List<NotificationStrategy> estrategias` en el constructor, Spring inyecta **automáticamente todas las clases `@Component` que implementan esa interfaz**. Para agregar un nuevo tipo de notificación, solo se crea una nueva clase `@Component` con `implements NotificationStrategy` — sin modificar `NotificationRouterService`.

---

## 12. Workers SMS y Push

Son los microservicios más simples: solo consumen de RabbitMQ y simulan el envío.

```java
// SmsNotificationListener.java
@Component
public class SmsNotificationListener {
    @RabbitListener(queues = "client_sms_queue")
    public void onSmsNotification(NotificationCommand comando) {
        // Spring deserializa el JSON → NotificationCommand automáticamente
        log.info("📱 SMS ENVIADO → orderId={} destinatario={} mensaje={}",
                 comando.orderId(), comando.recipientId(), comando.message());
    }
}
```

**Con `deploy.replicas: 2`**, hay dos instancias. Si el `notification_router` envía 3 notificaciones SMS seguidas, el primer worker toma la 1ra y 3ra, el segundo toma la 2da. Esto es el **balanceo de carga de Work Queue** de RabbitMQ.

---

## 13. Microservicio dashboard_aggregator

**Responsabilidad:** Consumidor stateful — reconstruye y mantiene el estado de todos los pedidos en memoria.

### Vista materializada en memoria

```java
// ConcurrentHashMap: thread-safe, clave=orderId, valor=snapshot del estado actual
private final Map<String, OrderSnapshot> snapshots = new ConcurrentHashMap<>();
```

**¿Por qué ConcurrentHashMap y no HashMap?** El listener de Kafka corre en hilos separados. Si dos eventos de órdenes diferentes llegan simultáneamente, dos hilos escriben en el mapa al mismo tiempo. `ConcurrentHashMap` es thread-safe para accesos concurrentes.

### Procesamiento de eventos en memoria

```java
// OrderAggregatorService.java — switch por tipo de evento
private OrderSnapshot procesarEnMemoria(OrderEventEnvelope envelope) throws Exception {
    String orderId = envelope.orderId();

    return switch (envelope.eventType()) {
        case ORDER_CREATED -> {
            if (!snapshots.containsKey(orderId)) {
                var node = objectMapper.readTree(envelope.payload());
                String customerName = node.path("customerName").asText("Desconocido");
                String product      = node.path("product").asText("Desconocido");
                int    quantity     = node.path("quantity").asInt(1);
                String address      = node.path("address").asText("");
                snapshots.put(orderId, new OrderSnapshot(orderId, customerName, product,
                                                          quantity, address));
            }
            yield snapshots.get(orderId);
        }
        case DRIVER_ASSIGNED -> {
            OrderSnapshot snap = snapshots.computeIfAbsent(orderId,
                id -> new OrderSnapshot(id, "Desconocido", "Desconocido", 1, ""));
            DriverAssignedPayload payload =
                objectMapper.readValue(envelope.payload(), DriverAssignedPayload.class);
            snap.aplicarDriverAssigned(payload.driverId(), payload.driverName());
            yield snap;
        }
        case DELIVERY_STARTED -> {
            // computeIfAbsent: si no hay snapshot (evento fuera de orden),
            // crea uno de emergencia en lugar de retornar null
            OrderSnapshot snap = snapshots.computeIfAbsent(orderId, ...);
            snap.aplicarDeliveryStarted();  // status → EN_CAMINO
            yield snap;
        }
        // DRIVER_ARRIVING → EN_CAMINO ; ORDER_DELIVERED → ENTREGADO
        default -> { log.debug(...); yield null; }
    };
}
```

### Doble persistencia

Por cada evento, el aggregator hace dos operaciones en PostgreSQL:

```java
// 1. Inserta en order_events (log inmutable de auditoría)
eventRepo.save(new OrderEventEntity(
    envelope.eventId(), envelope.orderId(), envelope.eventType().name(),
    envelope.timestamp(), envelope.payload()
));

// 2. Crea o actualiza en orders (estado actual del pedido)
orderRepo.findById(snap.getOrderId()).ifPresentOrElse(
    entity -> {
        entity.setStatus(snap.getStatus().name());  // actualiza estado
        entity.setDriverId(snap.getDriverId());     // asigna repartidor
        entity.setUpdatedAt(Instant.now());
        orderRepo.save(entity);
    },
    () -> {
        // Primera vez que vemos este pedido → INSERT
        orderRepo.save(new OrderEntity(snap.getOrderId(), snap.getCustomerName(), ...));
    }
);
```

### Notificación al backend

Después de procesar cada evento, el aggregator llama al `dashboard_backend` por HTTP:

```java
// DashboardBackendClient.java
public void notificarActualizacion(OrderStatusDTO dto) {
    String url = backendUrl + "/internal/update";  // http://dashboard-backend:8087/internal/update
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<OrderStatusDTO> request = new HttpEntity<>(dto, headers);
    restTemplate.postForEntity(url, request, Void.class);
    // Si falla (dashboard_backend caído), solo logea warning y continúa
    // No lanza excepción — el processing de Kafka no debe romperse por esto
}
```

---

## 14. Microservicio dashboard_backend

**Responsabilidad:** Servidor WebSocket que hace broadcast de actualizaciones al browser.

### WebSocket con STOMP

```java
// WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");           // broker simple en memoria
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint: ws://localhost:8087/ws
        // Sin SockJS — WebSocket nativo puro (browsers modernos lo soportan)
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }
}
```

**STOMP (Simple Text Oriented Messaging Protocol):** Protocolo de mensajería sobre WebSocket. Permite suscribirse a "destinos" (como `/topic/orders`) y recibir mensajes cuando alguien publica en ese destino.

### Broadcast WebSocket

```java
// InternalUpdateController.java
@RestController @RequestMapping("/internal")
public class InternalUpdateController {
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/update")
    public ResponseEntity<Void> recibirActualizacion(@RequestBody OrderStatusDTO dto) {
        // Envía el DTO a TODOS los browsers conectados suscritos a /topic/orders
        messagingTemplate.convertAndSend("/topic/orders", dto);
        // Spring serializa dto → JSON → WebSocket frame → cada browser conectado
        return ResponseEntity.ok().build();
    }
}
```

### Proxy hacia order_api

El browser no llama directamente a `order_api:8080` (están en redes diferentes). `dashboard_backend` actúa como proxy:

```java
// OrderApiProxyController.java
@PostMapping(value = "/api/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<String> crearPedido(@RequestBody String bodyRaw) {
    // Reenvía el JSON tal cual al order_api
    ResponseEntity<String> response = restTemplate.postForEntity(
        orderApiUrl + "/api/orders", new HttpEntity<>(bodyRaw, headers), String.class
    );
    return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
}
```

---

## 15. WebSocket y el dashboard HTML

### Conexión desde el browser (JavaScript)

```javascript
// index.html — conecta al WebSocket al cargar la página
function conectarWS() {
  const proto  = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const url    = proto + '//' + location.host + '/ws';  // ws://localhost:8087/ws
  const socket = new WebSocket(url);       // WebSocket nativo del browser
  stompCli     = Stomp.over(socket);       // cliente STOMP de la librería stompjs
  stompCli.debug = null;                   // silencia logs de STOMP
  stompCli.connect({}, onConectado, onDesconectado);
}

function onConectado() {
  // Suscripción al destino /topic/orders
  stompCli.subscribe('/topic/orders', msg => {
    const dto = JSON.parse(msg.body);  // OrderStatusDTO como JSON
    procesarDTO(dto);                   // actualiza toda la UI
  });
  cargarEstadoInicial();  // hidratación: carga todos los pedidos existentes
}

function onDesconectado() {
  setTimeout(conectarWS, 4000);  // reconexión automática cada 4 segundos
}
```

### Hidratación del estado inicial

Cuando el browser se conecta por primera vez (o se recarga), no sabe qué pedidos existen. `cargarEstadoInicial` los obtiene del aggregator:

```javascript
function cargarEstadoInicial() {
  fetch('/api/orders/snapshot')  // GET → proxy → dashboard_aggregator /api/snapshots
    .then(r => r.json())
    .then(lista => {
      // Ordena por fecha ascendente para que el último estado prevalezca
      lista.sort((a, b) => new Date(a.lastUpdated) - new Date(b.lastUpdated))
           .forEach(dto => {
             const existing = orders.get(dto.orderId);
             // Si ya tenemos una versión más reciente (llegó por WS antes), la conservamos
             if (!existing || new Date(dto.lastUpdated) >= new Date(existing.lastUpdated))
               orders.set(dto.orderId, dto);
           });
      renderRecent(); renderOrders(); updateMetrics(); updateBadge(); renderTrackList();
    });
}
```

### Animación de seguimiento

La animación reemplazó el mapa de Leaflet. Muestra diferentes escenas según el estado del pedido:

```javascript
const ANIM_CFG = {
  CREADO:    { chef: true,  road: false, delivered: false, pos: 0.00 },
  ASIGNADO:  { chef: false, road: true,  delivered: false, pos: 0.08 },
  EN_CAMINO: { chef: false, road: true,  delivered: false, pos: 0.42 },
  LLEGANDO:  { chef: false, road: true,  delivered: false, pos: 0.78 },
  ENTREGADO: { chef: false, road: false, delivered: true,  pos: 1.00 },
};

function correrMoto() {
  const roadW = scene.clientWidth - 180 - 110;  // ancho usable de la carretera
  if (motoPos < motoTarget) motoPos += motoSpeed;
  motoWrap.style.left = (90 + motoPos * roadW) + 'px';  // posición CSS en píxeles
  if (motoPos < motoTarget - 0.001)
    motoAnimFrame = requestAnimationFrame(correrMoto);  // bucle de animación
}
```

`requestAnimationFrame`: función del browser que ejecuta el callback antes del siguiente frame de pantalla (~60 veces por segundo). Es la forma correcta de hacer animaciones en JS — más eficiente que `setInterval`.

---

## 16. POO Avanzada — patrones de diseño

### Patrón State (en order_processor)

**Problema que resuelve:** Validar transiciones de estado sin `if-else` anidados.

**Implementación:** `Pedido` tiene una referencia a `OrderState`. Cada llamada a un método de negocio se delega al estado actual.

```
Pedido.asignarRepartidor() → estadoActual.asignarRepartidor()
                                    ↓
                             EstadoCreado (si estado = CREADO) → aplica transición → EstadoAsignado
                             EstadoAsignado (si estado = ASIGNADO) → lanza IllegalStateException
```

**Sin el patrón:**
```java
// ❌ Código anémico con if-else (lo que NO hacemos):
public void asignarRepartidor(String driverId, String driverName) {
    if (this.status.equals("CREADO")) {
        this.driverId = driverId;
        this.status = "ASIGNADO";
    } else {
        throw new IllegalStateException("...");
    }
}
```

**Con el patrón State:**
```java
// ✅ Código con patrón State (lo que hacemos):
public void asignarRepartidor(String driverId, String driverName) {
    estadoActual.asignarRepartidor(this, driverId, driverName);
    // No hay if. El estado actual sabe qué hacer.
}
```

### Patrón Strategy (en order_processor y notification_router)

**Problema que resuelve:** Intercambiar algoritmos sin modificar el código cliente.

**En `order_processor`:** `RoundRobinAssignmentStrategy` es una implementación de `DriverAssignmentStrategy`. Se podría crear `ClosestDriverStrategy` sin tocar `OrderProcessorService`.

**En `notification_router`:** `DriverArrivingSmsStrategy` y `OrderDeliveredPushStrategy` implementan `NotificationStrategy`. Agregar una nueva notificación = crear nueva clase `@Component`.

### Modelo Rico vs. Anémico

**Modelo Anémico** (lo que NO se hace): clase con solo getters/setters y lógica en el servicio.

**Modelo Rico** (lo que SÍ se hace): la entidad encapsula su estado Y su comportamiento:

```java
// Pedido es un modelo rico:
// - Estado privado (no hay setters públicos indiscriminados)
// - Métodos semánticos que validan invariantes
// - Protege las reglas de negocio adentro del objeto
pedido.asignarRepartidor("D-001", "Carlos");  // válido (estado=CREADO)
pedido.asignarRepartidor("D-002", "Laura");   // lanza excepción (estado=ASIGNADO)
// No se puede hacer pedido.setStatus("ASIGNADO") desde afuera
```

### Fail-Fast con Java Records

Los constructores compactos de `record` validan inmediatamente:

```java
public record OrderCreatedCommand(String commandId, String orderId, ...) {
    public OrderCreatedCommand {  // constructor compacto
        Objects.requireNonNull(orderId, "orderId no puede ser nulo");
        if (orderId.isBlank()) throw new IllegalArgumentException("orderId no puede estar vacío");
        if (quantity <= 0)     throw new IllegalArgumentException("quantity debe ser > 0");
    }
}
// Si fallas aquí, el objeto NUNCA EXISTE. No puede propagarse un estado inválido.
```

---

## 17. Principios SOLID

### S — Single Responsibility Principle (Responsabilidad Única)

Cada clase tiene una sola razón para cambiar:

- `OrderController`: solo maneja HTTP
- `OrderPublisherService`: solo publica a RabbitMQ
- `OrderProcessorService`: solo orquesta el flujo de negocio
- `KafkaOrderEventPublisher`: solo publica a Kafka
- `JpaOrderRepositoryAdapter`: solo maneja persistencia JPA

### O — Open/Closed Principle (Abierto/Cerrado)

`NotificationRouterService` está abierto para extensión (agregar estrategias) y cerrado para modificación. Para agregar SMS cuando se crea el pedido, solo se agrega:

```java
@Component
public class OrderCreatedSmsStrategy implements NotificationStrategy {
    @Override public boolean aplica(OrderEventType t) { return t == ORDER_CREATED; }
    @Override public Optional<NotificationCommand> construir(...) { ... }
}
// NotificationRouterService NO necesita cambiar.
```

### L — Liskov Substitution Principle

Cualquier implementación de `DriverAssignmentStrategy` puede reemplazar a `RoundRobinAssignmentStrategy` sin que `OrderProcessorService` se rompa.

### I — Interface Segregation Principle

Interfaces pequeñas y específicas:
- `OrderRepositoryPort`: solo operaciones de persistencia de pedidos
- `OrderEventPublisherPort`: solo publicación de eventos
- `DriverAssignmentStrategy`: solo asignación de repartidor

### D — Dependency Inversion Principle (Inversión de Dependencias)

Los módulos de alto nivel (servicios de dominio) no dependen de módulos de bajo nivel (Kafka, JPA):

```java
// OrderProcessorService depende de interfaces (abstracciones):
private final OrderRepositoryPort repositorio;      // no JpaRepository
private final OrderEventPublisherPort publicador;   // no KafkaTemplate
private final DriverAssignmentStrategy estrategia;  // no RoundRobin

// Las implementaciones concretas (Kafka, JPA) dependen del dominio, no al revés.
```

---

## 18. Pruebas unitarias

Las pruebas están diseñadas para ejecutarse **sin Spring Boot, sin Docker, sin BD, sin Kafka** — solo en memoria RAM. Son instantáneas.

### Pruebas de dominio puro (patrón AAA)

```java
// PedidoTest.java — Arrange, Act, Assert
@Test
void pedidoTransicionaCorrectamenteHastaEntregado() {
    // Arrange — crear el objeto en un estado conocido
    Pedido pedido = new Pedido("ord-1", "Juan", "Pizza", 2, "Calle 100");

    // Act — ejecutar la operación
    pedido.asignarRepartidor("D-001", "Carlos");
    pedido.iniciarEntrega();
    pedido.registrarLlegada();
    pedido.completarEntrega();

    // Assert — verificar el resultado
    assertEquals("ENTREGADO", pedido.getNombreEstado());
}

@Test
void noPermiteIniciarEntregaDesdeEstadoCreado() {
    // Arrange
    Pedido pedido = new Pedido("ord-1", "Juan", "Pizza", 2, "Calle 100");

    // Act + Assert — verifica que lanza excepción
    assertThrows(IllegalStateException.class, () -> pedido.iniciarEntrega());
}
```

### Pruebas con Mockito (servicios)

```java
// OrderProcessorServiceTest.java — mockea las dependencias
@ExtendWith(MockitoExtension.class)
class OrderProcessorServiceTest {

    @Mock OrderRepositoryPort repositorio;
    @Mock OrderEventPublisherPort publicador;
    @Mock DriverAssignmentStrategy estrategia;
    @InjectMocks OrderProcessorService service;  // inyecta los mocks automáticamente

    @Test
    void procesarComandoLlamaTodasLasOperacionesEnOrden() {
        // Arrange
        OrderCreatedCommand comando = new OrderCreatedCommand(
            "cmd-1", "ord-1", "Juan", "Pizza", 2, "Calle 100", Instant.now()
        );

        // Act
        service.procesarComando(comando);

        // Assert — verifica que se llamaron en orden exacto
        InOrder inOrder = inOrder(repositorio, publicador, estrategia);
        inOrder.verify(repositorio).guardar(any(Pedido.class));
        inOrder.verify(publicador).publicarOrderCreated(any(Pedido.class));
        inOrder.verify(estrategia).asignar(any(Pedido.class));
        inOrder.verify(repositorio).actualizar(any(Pedido.class));
        inOrder.verify(publicador).publicarDriverAssigned(any(Pedido.class));
    }
}
```

**`@Mock`:** Crea un "doble" de la clase — un objeto que no hace nada por defecto pero recuerda cuáles métodos fueron llamados.

**`@InjectMocks`:** Crea la instancia real del servicio e inyecta los mocks en su constructor.

**`verify(...)`:** Afirma que un método fue llamado con ciertos argumentos. Si no fue llamado, la prueba falla.

**`InOrder`:** Verifica que las llamadas ocurrieron en un orden específico.

### Pruebas del notification_router

```java
// NotificationRouterServiceTest.java
@Test
void driverArrivingGeneraNotificacionSmsConRoutingKeyCorrecto() {
    // Arrange
    OrderEventEnvelope envelope = crearEnvelopeDriverArriving();

    // Act
    routerService.procesarEvento(envelope);

    // Assert
    verify(rabbitTemplate).convertAndSend(
        eq("notifications"),          // exchange correcto
        eq("notify.client.sms"),      // routing key correcto
        any(NotificationCommand.class) // algún comando
    );
}

@Test
void driverAssignedNoGeneraNotificacion() {
    OrderEventEnvelope envelope = crearEnvelope(DRIVER_ASSIGNED);
    routerService.procesarEvento(envelope);
    verifyNoInteractions(rabbitTemplate);  // nunca llamó a rabbitTemplate
}
```

---

## 19. Docker — Dockerfiles multietapa

### Concepto de build multietapa

**Problema:** Para compilar Java necesitas JDK + Maven (~500MB de imagen). Para ejecutar Java solo necesitas JRE (~100MB). Con un solo stage el contenedor de producción arrastraría todas las herramientas de compilación innecesariamente.

**Solución:** Dos stages. El primero compila, el segundo solo copia el JAR resultante.

```dockerfile
# dashboard_aggregator/Dockerfile

# ══════════════════════════════════════════
# STAGE 1: BUILD — solo para compilar
# Esta imagen nunca llega a producción
# ══════════════════════════════════════════
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# CAPA 1: copiar SOLO los pom.xml (sin código fuente)
# Docker cachea esta capa si los pom.xml no cambian.
# Si solo cambias código fuente, Maven no re-descarga dependencias.
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY dashboard_aggregator/pom.xml dashboard_aggregator/pom.xml
# ... (todos los módulos para que Maven resuelva el multi-módulo)

# CAPA 2: descargar dependencias (cacheada si pom.xml no cambia)
RUN mvn dependency:go-offline -pl common,dashboard_aggregator -am -q

# CAPA 3: copiar código fuente (invalida caché al cambiar código)
COPY common/src             common/src
COPY dashboard_aggregator/src dashboard_aggregator/src

# CAPA 4: compilar (rápido si las capas anteriores están en caché)
RUN mvn clean package -pl common,dashboard_aggregator -am -DskipTests -q

# ══════════════════════════════════════════
# STAGE 2: RUNTIME — imagen final ligera
# Solo tiene JRE + el JAR compilado
# ══════════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario no-root (seguridad del sistema operativo / cgroups)
# Si la aplicación es comprometida, el atacante no tiene root en el host
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copia SOLO el JAR del stage anterior (no Maven, no código fuente, no JDK)
COPY --from=builder /workspace/dashboard_aggregator/target/dashboard_aggregator-*.jar app.jar

# Flags ergonómicos de JVM para contenedores:
# UseContainerSupport: JVM lee los límites de cgroups del contenedor
#                      (sin esto, ve la RAM total del host y hace GC incorrectamente)
# MaxRAMPercentage=75.0: usa máximo 75% de la RAM del contenedor para heap
# ExitOnOutOfMemoryError: si se acaba la memoria, el proceso termina
#                          (Docker lo reiniciará) en lugar de degradarse
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Por qué `-DskipTests` en el build

Las pruebas unitarias se ejecutan en CI/CD con un comando separado (`mvn test`). En el Dockerfile se omiten para que el build de imagen sea rápido y no requiera levantar BD ni brokers.

---

## 20. Docker Compose — orquestación completa

### Variables de entorno y `.env`

```bash
# .env — valores que varían por ambiente (dev, prod)
POSTGRES_DB=rappi_stream_db
POSTGRES_USER=rappi_user
POSTGRES_PASSWORD=rappi_pass_2024
RABBITMQ_DEFAULT_USER=rappi_admin
RABBITMQ_DEFAULT_PASS=rappi_rmq_2024
KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qg
```

```yaml
# docker-compose.yml usa las variables con ${VARIABLE:default}
postgres:
  environment:
    POSTGRES_DB:       ${POSTGRES_DB}
    POSTGRES_USER:     ${POSTGRES_USER}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
```

### Healthchecks — comprobación de salud

```yaml
postgres:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
    interval: 10s      # verificar cada 10 segundos
    timeout: 5s        # máximo 5 segundos para responder
    retries: 5         # intentar 5 veces antes de marcar como unhealthy
    start_period: 15s  # esperar 15s antes de empezar a verificar (startup time)

rabbitmq:
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "ping"]
    # rabbitmq-diagnostics ping: verifica que el broker responde internamente

kafka:
  healthcheck:
    test: ["CMD-SHELL", "kafka-topics --bootstrap-server kafka:9092 --list || exit 1"]
    interval: 15s
    retries: 10
    start_period: 30s  # Kafka tarda más en arrancar (JVM + KRaft initialization)
```

### Dependencias con `depends_on`

```yaml
order_processor:
  depends_on:
    rabbitmq:
      condition: service_healthy    # espera a que RabbitMQ responda correctamente
    kafka:
      condition: service_healthy    # espera a que Kafka responda
    postgres:
      condition: service_healthy    # espera a que PostgreSQL acepte conexiones
    kafka-init:
      condition: service_completed_successfully  # espera a que el topic sea creado
```

**`service_healthy` vs `service_completed_successfully`:**
- `service_healthy`: el contenedor está corriendo Y su healthcheck pasa
- `service_completed_successfully`: el contenedor terminó con exit code 0 (para init containers)

**`kafka-init`:** Un init container que corre una vez, crea el topic y termina. Los microservicios esperan a que termine exitosamente antes de empezar.

### Límites de recursos (cgroups)

```yaml
order_processor:
  deploy:
    resources:
      limits:
        cpus: '0.50'    # máximo 50% de un núcleo de CPU
        memory: 384M    # máximo 384 MB de RAM
```

**¿Cómo funciona?** Docker usa **cgroups** (control groups) del kernel Linux para aplicar estas restricciones. El contenedor no puede usar más recursos de los especificados, independientemente de la carga del sistema.

**Tabla de límites del proyecto:**

| Servicio | CPU | RAM |
|---|---|---|
| `postgres` | 0.50 | 512M |
| `rabbitmq` | 0.50 | 384M |
| `kafka` | 0.75 | 512M |
| `order_api` | 0.50 | 384M |
| `order_processor` | 0.50 | 384M |
| `driver_simulator` | 0.25 | 256M |
| `notification_router` | 0.25 | 256M |
| `sms_worker` (×2) | 0.25 | 256M |
| `push_notification_worker` (×2) | 0.25 | 256M |
| `dashboard_aggregator` | 0.50 | 384M |
| `dashboard_backend` | 0.50 | 384M |

### Red interna

```yaml
networks:
  rappi_net:
    driver: bridge
# Todos los servicios están en rappi_net
# Se comunican por nombre: kafka:9092, postgres:5432, rabbitmq:5672
```

**Bridge network:** Red virtual privada en el host Docker. Los contenedores dentro de la misma red pueden comunicarse por nombre de servicio. Desde fuera de la red (browser en el host) se accede por los puertos mapeados (`ports: "8087:8087"`).

### Volúmenes nombrados (persistencia)

```yaml
volumes:
  postgres_data:
    driver: local
  rabbitmq_data:
    driver: local
  kafka_data:
    driver: local
```

Sin volúmenes, los datos se pierden al hacer `docker compose down`. Con volúmenes nombrados, los datos persisten entre reinicios. `docker compose down -v` elimina también los volúmenes.

### Kafka en modo KRaft (sin ZooKeeper)

```yaml
kafka:
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller     # el mismo nodo es broker Y controlador
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"   # creación manual via init container
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1  # sin réplicas (solo 1 broker)
    CLUSTER_ID: ${KAFKA_CLUSTER_ID}            # UUID fijo para arranques deterministas
```

---

## 21. Términos técnicos clave para la evaluación

| Término | Definición en el contexto del proyecto |
|---|---|
| **Event Sourcing** | El estado del sistema se reconstruye a partir de un log de eventos inmutables. `order_events` es ese log. |
| **System of Record** | Kafka es la fuente única de verdad del estado de los pedidos. |
| **Consumer Group** | Conjunto de instancias que consumen un topic. Cada grupo recibe todos los mensajes independientemente. |
| **Partition Key** | Clave usada por Kafka para decidir en qué partición va un mensaje. Usamos `orderId` para garantizar orden. |
| **Offset** | Número de posición de un mensaje en una partición de Kafka. |
| **Dead Letter Queue** | Cola donde van los mensajes que fallaron (no implementado en este MVP, pero es el siguiente paso). |
| **Work Queue** | Patrón de RabbitMQ donde múltiples workers compiten por mensajes de una cola. |
| **Topic Exchange** | Tipo de exchange de RabbitMQ que enruta por patrones de routing keys. |
| **Routing Key** | Etiqueta del mensaje usada por el exchange para enrutarlo a las colas correctas. |
| **Idempotencia** | Propiedad de una operación que puede ejecutarse múltiples veces con el mismo resultado. |
| **Vista Materializada** | Estado calculado y almacenado en memoria (el `ConcurrentHashMap` del aggregator). |
| **Stateful Consumer** | Consumidor que mantiene estado entre mensajes (el `dashboard_aggregator`). |
| **DIP** | Dependency Inversion Principle: módulos de alto nivel dependen de abstracciones, no de implementaciones. |
| **Port** | Interfaz que define cómo el dominio interactúa con el mundo exterior (Kafka, JPA, etc.). |
| **Adapter** | Implementación de un port que conecta con una tecnología específica. |
| **Fail-Fast** | Validar entradas lo antes posible y lanzar excepción inmediatamente si son inválidas. |
| **Rich Domain Model** | Clase que encapsula estado Y comportamiento (vs. modelo anémico con solo getters/setters). |
| **cgroups** | Control groups: mecanismo del kernel Linux para limitar recursos de procesos/contenedores. |
| **Multi-stage build** | Dockerfile con múltiples FROM: uno para compilar, otro para ejecutar. Imagen final ligera. |
| **STOMP** | Protocolo de mensajería sobre WebSocket. Permite suscribirse a destinos y recibir mensajes. |
| **SimpMessagingTemplate** | Bean de Spring para enviar mensajes STOMP a destinos programáticamente. |
| **`@Async`** | Anotación de Spring que ejecuta un método en un hilo separado del ThreadPoolTaskExecutor. |
| **`AtomicInteger`** | Variable entera thread-safe que garantiza operaciones atómicas en entornos concurrentes. |
| **`ConcurrentHashMap`** | HashMap thread-safe para accesos concurrentes desde múltiples hilos. |
| **`auto-offset-reset=earliest`** | Si no hay offset guardado, leer desde el primer mensaje del topic (replay completo). |
| **KRaft** | Modo de Kafka sin ZooKeeper. El broker gestiona sus propios metadatos. |

---

## 22. Preguntas frecuentes de evaluación y respuestas

### "¿Por qué usaron Kafka si RabbitMQ puede hacer lo mismo?"

RabbitMQ y Kafka tienen filosofías distintas. RabbitMQ es un broker de mensajes: enruta mensajes y los elimina al ser consumidos. Kafka es un log distribuido: los mensajes son inmutables y permanentes, múltiples consumidores los leen independientemente. Para el seguimiento en tiempo real, necesitamos que `driver_simulator`, `notification_router` y `dashboard_aggregator` reciban **todos** los eventos del pedido de forma independiente. Con RabbitMQ un mensaje solo llega a uno de los tres. Además, Kafka permite que el `dashboard_aggregator` se reinicie y reconstruya el estado completo leyendo desde el principio.

### "¿Qué garantiza el orden de los eventos de un pedido?"

La clave de partición `orderId`. Kafka garantiza que todos los mensajes con la misma clave van a la misma partición, y dentro de una partición los mensajes se leen en orden FIFO. Así, `ORDER_CREATED → DRIVER_ASSIGNED → DELIVERY_STARTED → DRIVER_ARRIVING → ORDER_DELIVERED` siempre se procesan en ese orden para un pedido específico.

### "¿Qué pasa si el dashboard_aggregator se reinicia?"

Gracias a `auto-offset-reset=earliest`, al reiniciarse sin offset guardado leerá todos los eventos desde el principio del topic `order_events`. La verificación de idempotencia (`existsByEventId`) evita duplicar filas en `order_events`. El `ConcurrentHashMap` de snapshots se reconstruye desde cero en memoria. Al terminar el replay, el estado en memoria es idéntico al que tenía antes de reiniciarse.

### "¿Qué hace el `@Valid` en el controller?"

Activa la validación de Bean Validation. Si alguna anotación de validación falla (`@NotBlank`, `@Min`, etc.), Spring retorna automáticamente `400 Bad Request` con un mensaje de error descriptivo, **sin** que el código del controller se ejecute. El request inválido nunca llega al servicio.

### "¿Por qué el `Pedido` no es una entidad JPA?"

Porque mezclar lógica de dominio con anotaciones de persistencia viola el principio de responsabilidad única y el DIP. `Pedido` es un POJO puro que no sabe nada de JPA. `PedidoEntity` es la representación JPA con anotaciones. `JpaOrderRepositoryAdapter` traduce entre ambas. Esto permite cambiar PostgreSQL por MongoDB sin modificar una sola línea del dominio.

### "¿Qué es el patrón Bridge en este proyecto?"

`notification_router` es el Bridge (Puente). Su trabajo es traducir eventos del ecosistema Kafka a comandos del ecosistema RabbitMQ. Kafka habla de "cosas que pasaron" (eventos). RabbitMQ habla de "cosas que hacer" (comandos). El bridge separa ambos mundos: si cambian los canales de notificación (ej: agregar Telegram), solo se modifica el bridge, no los productores de eventos.

### "¿Por qué `deploy.replicas: 2` en los workers?"

Para demostrar escalabilidad horizontal. Con dos instancias del mismo worker consumiendo de la misma queue de RabbitMQ, la carga se distribuye automáticamente en round-robin. Si llegan 10 notificaciones, cada worker procesa 5. Esto es el patrón Work Queue: múltiples workers compiten por mensajes de una cola compartida.

### "¿Qué hace `-XX:+UseContainerSupport`?"

Sin esta flag, la JVM detecta la RAM total del host (ej: 16 GB) y configura su heap basándose en eso, aunque el contenedor solo tenga 384 MB asignados. Con `UseContainerSupport`, la JVM lee los límites de cgroups del contenedor y ajusta su heap apropiadamente. Esto evita que el proceso sea matado por el OOMKiller del kernel por exceder la memoria del contenedor.

### "¿Cómo se garantiza que no se procesen eventos duplicados?"

Doble barrera:
1. `eventRepo.existsByEventId(eventId)` — verificación antes de procesar (rápida)
2. Restricción `UNIQUE` en la columna `event_id` de la tabla `order_events` — si dos hilos pasan la verificación simultáneamente, el segundo falla con `DataIntegrityViolationException`, que se captura silenciosamente

### "¿Cómo probaron los servicios sin Kafka ni RabbitMQ?"

Con Mockito. `@Mock KafkaTemplate kafkaTemplate` crea un doble que registra llamadas sin enviar nada a ningún broker. `verify(kafkaTemplate).send(eq("order_events"), eq(orderId), anyString())` verifica que el mensaje fue enviado con los argumentos correctos. Las pruebas corren en milisegundos sin infraestructura externa.

---

*Documento generado para la evaluación del proyecto Rappi-Stream.*
*Rama: `feature/juliana` | Repositorio: github.com/HMAlejandra/Rappi-stream*

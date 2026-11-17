# Guión de Presentación — JULIANA
## Turnos: 2 · 4 · 6 · 8 · 10

> **Hilo conductor:** María García pide una Hamburguesa Doble en `http://localhost:8087`.
> En 22 segundos el sistema la procesa completa: le asigna a Carlos Mendoza,
> simula la ruta, envía notificaciones, y el dashboard se actualiza solo.

---

## TURNO 2 — Arquitectura e infraestructura

### Lo que acabas de ver (Helen terminó el demo en vivo)
Helen mostró el pedido creándose y los estados cambiando en el dashboard.
Ahora tú explicas POR QUÉ funciona así por dentro.

---

### 🗣️ Lo que dices

> "El sistema tiene **dos brokers de mensajería con propósitos distintos**.
> Cuando María hizo click en 'Pedir ahora', ese comando fue por **RabbitMQ**
> porque solo un servicio lo debe procesar — el `order_processor`.
> Luego, cada cambio de estado — CREADO, ASIGNADO, EN_CAMINO, LLEGANDO, ENTREGADO —
> se publicó como evento en **Kafka**. Kafka es el System of Record: un log inmutable
> donde quedan registrados todos los eventos. Tres servicios lo leen al mismo tiempo
> e independientemente: el simulador del repartidor, el router de notificaciones,
> y el aggregator del dashboard."

---

### 📦 El pedido de María en este turno

```
María click "Pedir ahora"
        │
        ▼
  RabbitMQ ── new_orders_queue
  "Crear Hamburguesa para María García"
  → un solo order_processor lo toma
        │
        ▼
  Kafka ── topic order_events (clave = UUID del pedido)
  Evento 1: ORDER_CREATED
  Evento 2: DRIVER_ASSIGNED  (Carlos D-001)
  Evento 3: DELIVERY_STARTED
  Evento 4: DRIVER_ARRIVING
  Evento 5: ORDER_DELIVERED

  Los 3 consumen TODOS esos eventos INDEPENDIENTEMENTE:
  ┌── driver_simulator     → simula la ruta física
  ├── notification_router  → SMS a María cuando llega Carlos
  └── dashboard_aggregator → actualiza el dashboard en vivo
```

---

### 🖥️ Comando 1 — abre la terminal y ejecuta

**Escribe exactamente esto y presiona Enter:**
```
docker compose ps
```

**Qué vas a ver y qué dices:**

```
NAME                                      STATUS
rappi_kafka                               Up (healthy)    ← "Kafka corriendo"
rappi_rabbitmq                            Up (healthy)    ← "RabbitMQ corriendo"
rappi_postgres                            Up (healthy)    ← "PostgreSQL corriendo"
rappi_order_api                           Up (healthy)
rappi_order_processor                     Up (healthy)
rappi_driver_simulator                    Up
rappi_notification_router                 Up
rappi_dashboard_aggregator                Up
rappi_dashboard_backend                   Up (healthy)
rappi-stream-sms_worker-1                 Up
rappi-stream-sms_worker-2                 Up
rappi-stream-push_notification_worker-1   Up
rappi-stream-push_notification_worker-2   Up
```

**Señala esto:**

1. **No aparece `zookeeper` en ninguna línea** →
   > *"No hay ZooKeeper. Kafka corre en modo **KRaft**: el broker gestiona
   > sus propios metadatos sin necesitar un coordinador externo.
   > Helen explicará esto en detalle en el turno 3."*

2. **`rappi_kafka_init` NO aparece** →
   > *"El init container que creó el topic `order_events` ya terminó y
   > desapareció — es normal, tiene `restart: no`. Si alguien quiere
   > verificarlo: `docker ps -a --filter name=rappi_kafka_init`
   > aparece `Exited (0)` — exit code 0 significa éxito."*

3. **`sms_worker-1` y `sms_worker-2`** →
   > *"Dos réplicas de cada worker de notificaciones. Lo explico en el turno 4."*

---

### 🖥️ Comando 2 — en la misma terminal

**Escribe exactamente esto y presiona Enter:**
```
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events
```

**Qué significa el comando:**
- `docker exec rappi_kafka` → ejecuta algo dentro del contenedor de Kafka
- `kafka-topics` → herramienta de Kafka para ver topics
- `--bootstrap-server kafka:9092` → dirección interna de Kafka en Docker
- `--describe --topic order_events` → muestra los detalles del topic

**Qué vas a ver:**
```
Topic: order_events   PartitionCount: 3   ReplicationFactor: 1
  Partition: 0   Leader: 1   Replicas: 1
  Partition: 1   Leader: 1   Replicas: 1
  Partition: 2   Leader: 1   Replicas: 1
```

**Señala y di:**

1. **`PartitionCount: 3`** →
   > *"Existen **3 particiones**. La clave de partición es el `orderId`
   > — el UUID del pedido de María. Kafka calcula `hash(orderId) % 3`
   > y todos sus eventos van siempre a la misma partición.
   > Eso garantiza el orden cronológico estricto:
   > CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO.
   > Nunca puede llegar ENTREGADO antes que CREADO para el mismo pedido."*

2. **`ReplicationFactor: 1`** →
   > *"Un solo broker en desarrollo. En producción usaríamos 3 réplicas
   > para alta disponibilidad."*

---

### ❓ Preguntas y respuestas

**"¿Por qué Kafka Y RabbitMQ al mismo tiempo?"**
> "RabbitMQ enruta mensajes y los elimina al ser consumidos — perfecto para comandos
> que procesa uno solo. Kafka es un log inmutable donde múltiples consumidores
> leen independientemente. Para el pedido de María, necesitamos que driver_simulator,
> notification_router y dashboard_aggregator reciban los mismos 5 eventos
> de forma independiente — solo Kafka garantiza eso."

**"¿Qué pasa si dashboard_aggregator se reinicia mientras María espera?"**
> "Con `auto-offset-reset=earliest` lee todos los eventos desde el principio
> del topic y reconstruye el estado. La columna `event_id UNIQUE` en PostgreSQL
> evita insertar filas duplicadas."

---

---

## TURNO 4 — RabbitMQ: Work Queue y Topic Exchange

### Lo que acaba de pasar (Helen explicó KRaft y Event Sourcing)
Ahora tú explicas cómo RabbitMQ distribuye el trabajo y las notificaciones.

---

### 🗣️ Lo que dices — Work Queue

> "Cuando María hizo click, el `order_api` empaquetó sus datos en un
> `OrderCreatedCommand` — un record inmutable de Java 17 — y lo publicó
> en la cola `new_orders_queue`. Esto es el patrón **Work Queue**: si
> hubiera 3 instancias del `order_processor`, el pedido de María lo
> tomaría **uno solo**, no los tres. RabbitMQ hace round-robin automáticamente.
> Esto evita que el mismo pedido se procese dos veces."

---

### 🗣️ Lo que dices — Topic Exchange

> "Para las notificaciones usamos un **Topic Exchange** llamado `notifications`.
> Cuando Carlos está llegando, publicamos con la routing key `notify.client.sms`.
> El exchange la compara con el patrón `notify.client.*` — el asterisco reemplaza
> exactamente una palabra — y la enruta a `client_sms_queue` donde están los
> `sms_worker`. Cuando el pedido se entrega, publicamos `notify.driver.push`
> y va a `driver_push_queue` para notificarle a Carlos."

---

### 📦 El pedido de María en este turno

```java
// Lo que order_api publica en new_orders_queue:
OrderCreatedCommand {
  commandId:    "cmd-7f3a-uuid"
  orderId:      "abc-123-pedido-maria"    ← clave de partición Kafka
  customerName: "María García"
  product:      "Hamburguesa Doble"
  quantity:     1
  address:      "Calle 100 #15-30, Bogotá"
  issuedAt:     2026-11-12T19:57:20Z
}

// SMS que recibiría María cuando Carlos llega:
"Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos."
→ routing key: "notify.client.sms"  →  client_sms_queue  →  sms_worker-1 o sms_worker-2

// Push que recibiría Carlos cuando entrega:
"¡Entrega completada! Pedido abc-123 entregado exitosamente. ¡Buen trabajo, Carlos Mendoza!"
→ routing key: "notify.driver.push"  →  driver_push_queue  →  push_notification_worker-1 o -2
```

---

### 🖥️ Muestra 1 — Panel de RabbitMQ en el browser

**Abre en el browser:** `http://localhost:15672`
- Usuario: `rappi_admin`
- Contraseña: `rappi_rmq_2024`

**Pasos:**
1. Click en **Exchanges** en el menú de arriba
2. Busca `notifications` en la lista → click en él
3. Señala la sección **Bindings** y di:
   - `notify.client.*` → `client_sms_queue` →
     *"para SMS al cliente, en este caso a María"*
   - `notify.driver.*` → `driver_push_queue` →
     *"para Push al repartidor, en este caso a Carlos"*
4. Di:
   > *"El `*` es un wildcard que reemplaza exactamente una palabra.
   > `notify.client.sms` coincide con `notify.client.*`. Si mañana
   > agregamos email, `notify.client.email` también coincidiría
   > sin tocar el exchange."*

---

### 🖥️ Muestra 2 — Réplicas en la terminal

**En la terminal ya abierta:**
```
docker compose ps
```

**Señala:**
```
rappi-stream-sms_worker-1                Up   ← instancia 1
rappi-stream-sms_worker-2                Up   ← instancia 2
rappi-stream-push_notification_worker-1  Up   ← instancia 1
rappi-stream-push_notification_worker-2  Up   ← instancia 2
```

> *"Dos instancias de cada worker. Ambas consumen de la misma queue.
> RabbitMQ distribuye en round-robin: si María y Juan reciben SMS al
> mismo tiempo, `sms_worker-1` procesa el de María y `sms_worker-2`
> el de Juan — en paralelo. No tienen `container_name` fijo porque
> Docker no puede tener dos contenedores con el mismo nombre."*

---

### 💻 Código — línea por línea

**Archivo:** `notification_router/src/main/java/.../service/NotificationRouterService.java`

```java
// LÍNEA 38: Spring inyecta AUTOMÁTICAMENTE todas las clases @Component
// que implementan NotificationStrategy — sin que este servicio las conozca
private final List<NotificationStrategy> estrategias;
// estrategias = [DriverArrivingSmsStrategy, OrderDeliveredPushStrategy]

// LÍNEA 55: cuando llega un evento de Kafka, itera las estrategias
for (NotificationStrategy estrategia : estrategias) {
    if (estrategia.aplica(envelope.eventType())) {
        // Evento DRIVER_ARRIVING del pedido de María:
        // DriverArrivingSmsStrategy.aplica(DRIVER_ARRIVING)   = true  ← entra aquí
        // OrderDeliveredPushStrategy.aplica(DRIVER_ARRIVING)  = false ← se salta

        Optional<NotificationCommand> comando = estrategia.construir(envelope);
        // construye el SMS: "Tu repartidor Carlos Mendoza está llegando..."

        comando.ifPresent(cmd -> {
            String routingKey = routingKeyPara(cmd.type());
            // cmd.type() = SMS → routingKey = "notify.client.sms"

            rabbitTemplate.convertAndSend("notifications", routingKey, cmd);
            // publica en exchange "notifications" con key "notify.client.sms"
            // → coincide patrón "notify.client.*"
            // → va a client_sms_queue
            // → sms_worker-1 o sms_worker-2 lo toma
        });
        return; // una sola estrategia por evento
    }
}
```

---

### ❓ Preguntas y respuestas

**"¿Por qué no usar Kafka para las notificaciones también?"**
> "Las notificaciones van a un destinatario específico y se procesan exactamente
> una vez. Con Kafka los 3 servicios recibirían el mismo comando de notificación.
> RabbitMQ con Topic Exchange es el patrón correcto para enrutamiento selectivo."

**"¿Qué pasa si un sms_worker cae?"**
> "El mensaje queda en la queue — las queues son durables, sobreviven reinicios.
> Cuando el worker se recupera, toma el mensaje. María lo recibe con pequeño
> retraso pero no se pierde."

**"¿Por qué `notify.client.*` con asterisco y no `notify.client.sms` exacto?"**
> "El asterisco permite que en el futuro podamos agregar `notify.client.email`
> o `notify.client.push` y automáticamente lleguen a la misma queue sin tocar
> la configuración del exchange."

---

---

## TURNO 6 — Patrón Strategy y DIP

### Lo que acaba de pasar (Helen mostró Pedido.java y el Patrón State)
Ahora tú explicas cómo se asigna el repartidor y por qué el servicio no sabe nada de Kafka.

---

### 🗣️ Lo que dices — Strategy

> "Para asignar a Carlos Mendoza al pedido de María usamos el **patrón Strategy**.
> El `OrderProcessorService` llama a `estrategia.asignar(pedido)` sin saber
> cómo funciona la asignación por dentro. Hoy es RoundRobin.
> Mañana podría ser por cercanía o por calificación.
> El servicio **no cambia**."

---

### 🗣️ Lo que dices — DIP

> "Este servicio tiene tres dependencias — **todas son interfaces**.
> No importa `KafkaTemplate`, no importa `JpaRepository`, no importa
> `RoundRobinAssignmentStrategy`. Solo conoce contratos abstractos.
> Si mañana cambiamos Kafka por otro broker, **este servicio no cambia
> ni una línea**."

---

### 📦 El pedido de María en este turno

```
Pedido de María llega → OrderProcessorService llama:
  estrategiaAsignacion.asignar(pedidoDeMaría)

RoundRobinAssignmentStrategy hace:
  cursor = 0 (primer pedido del día)
  disponibles = [Carlos D-001, Laura D-002, Andrés D-003, Valentina D-004, Miguel D-005]
  índice = abs(0 % 5) = 0  →  Carlos Mendoza (D-001)
  pedido.asignarRepartidor("D-001", "Carlos Mendoza")

Segundo pedido (Juan): cursor=1 → Laura D-002
Tercer pedido (Ana):   cursor=2 → Andrés D-003
Sexto pedido:          cursor=5, abs(5%5)=0 → vuelve a Carlos
```

---

### 💻 Muestra 1 — Interfaz Strategy (abre este archivo)

**Archivo:** `order_processor/src/main/java/.../domain/strategy/DriverAssignmentStrategy.java`

```java
// La INTERFAZ — el contrato que debe cumplir toda estrategia
public interface DriverAssignmentStrategy {

    void asignar(Pedido pedido);
    // Un solo método. "Dado este pedido, asigna un repartidor."
    // OrderProcessorService depende de ESTA interfaz, no de RoundRobin
}
```

> *"Un solo método. `OrderProcessorService` depende de esta interfaz.
> Para cambiar la estrategia de asignación, solo se crea una nueva clase
> que implemente esta interfaz. El servicio no cambia."*

---

### 💻 Muestra 2 — RoundRobin (abre este archivo)

**Archivo:** `order_processor/src/main/java/.../domain/strategy/RoundRobinAssignmentStrategy.java`

```java
// LÍNEA 21: AtomicInteger = contador thread-safe
// Si María y Juan hacen click AL MISMO TIEMPO → dos hilos ejecutan esto
private final AtomicInteger cursor = new AtomicInteger(0);
// Sin AtomicInteger: ambos hilos leen cursor=0 → ambos pedidos van a Carlos
// Con AtomicInteger: uno lee 0 (Carlos), el otro lee 1 (Laura) → garantizado

// LÍNEA 37: filtra solo los disponibles
List<Driver> disponibles = repartidores.stream()
        .filter(Driver::isAvailable)   // solo los que tienen available=true
        .toList();
// Si Carlos no está disponible ese día, simplemente no aparece en la lista

// LÍNEA 47: rotación circular
int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
// cursor.getAndIncrement() → lee el valor actual Y lo incrementa atómicamente
// Para María:  cursor era 0 → devuelve 0, deja cursor=1
// Para Juan:   cursor era 1 → devuelve 1, deja cursor=2
// abs(0 % 5) = 0 → Carlos D-001
// abs(1 % 5) = 1 → Laura D-002
// abs(5 % 5) = 0 → vuelve a Carlos (rotación circular)

// LÍNEA 52: llama al método de NEGOCIO del pedido
// Esto activa el patrón State que explicó Helen
pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
// pedido.asignarRepartidor("D-001", "Carlos Mendoza")
// → EstadoCreado.asignarRepartidor() → transiciona a EstadoAsignado
```

---

### 💻 Muestra 3 — OrderProcessorService (abre este archivo)

**Archivo:** `order_processor/src/main/java/.../application/OrderProcessorService.java`

```java
// LÍNEAS 47-50: las tres dependencias — TODAS son interfaces (DIP)
private final OrderRepositoryPort    repositorio;
//   no JpaRepository — no sabe nada de Hibernate ni PostgreSQL
private final OrderEventPublisherPort publicador;
//   no KafkaTemplate — no sabe nada de Kafka ni brokers
private final DriverAssignmentStrategy estrategiaAsignacion;
//   no RoundRobin — no sabe qué algoritmo usa

// LÍNEAS 56-61: constructor — inyección ESTRICTA por constructor
// No hay @Autowired en campos. Las dependencias son final = inmutables.
public OrderProcessorService(OrderRepositoryPort repositorio,
                              OrderEventPublisherPort publicador,
                              DriverAssignmentStrategy estrategiaAsignacion) {
    this.repositorio          = repositorio;
    this.publicador           = publicador;
    this.estrategiaAsignacion = estrategiaAsignacion;
}

// El flujo del pedido de María:
Pedido pedido = new Pedido("abc-123", "María García", "Hamburguesa Doble", 1, "Calle 100");
repositorio.guardar(pedido);            // → INSERT INTO orders (JPA, PostgreSQL)
publicador.publicarOrderCreated(pedido);// → Kafka topic order_events
Thread.sleep(3000);                     // "Buscando repartidor..." en el dashboard
estrategiaAsignacion.asignar(pedido);   // → RoundRobin → Carlos D-001 → EstadoAsignado
repositorio.actualizar(pedido);         // → UPDATE orders SET status='ASIGNADO'
publicador.publicarDriverAssigned(pedido);// → Kafka topic order_events
```

> *"Señala el constructor: todas interfaces. Di: 'Si mañana cambiamos Kafka por otro broker,
> solo se reemplaza la implementación de `OrderEventPublisherPort`. Este servicio no cambia.'"*

---

### 💻 Muestra 4 — NotificationStrategy y Spring inyectando la lista

**Archivo:** `notification_router/src/main/java/.../strategy/NotificationStrategy.java`

```java
public interface NotificationStrategy {
    boolean aplica(OrderEventType eventType);
    // ¿Soy responsable de este evento?
    // DriverArrivingSmsStrategy:   aplica(DRIVER_ARRIVING)  = true
    // OrderDeliveredPushStrategy:  aplica(ORDER_DELIVERED)  = true
    // Ambas para cualquier otro:   aplica(ORDER_CREATED)    = false

    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
    // Construye el comando de notificación con el mensaje real
}
```

**Archivo:** `notification_router/src/main/java/.../strategy/DriverArrivingSmsStrategy.java`

```java
// @Component = Spring registra esta clase en su contexto
@Component
public class DriverArrivingSmsStrategy implements NotificationStrategy {

    @Override
    public boolean aplica(OrderEventType eventType) {
        return eventType == OrderEventType.DRIVER_ARRIVING;
        // true solo cuando Carlos está llegando a casa de María
    }

    @Override
    public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
        DriverArrivingPayload payload =
            objectMapper.readValue(envelope.payload(), DriverArrivingPayload.class);
        // payload.driverName()       = "Carlos Mendoza"
        // payload.estimatedArrival() = "2 minutos"

        String mensaje = String.format(
            "Tu repartidor %s está llegando. Tiempo estimado: %s.",
            payload.driverName(), payload.estimatedArrival()
        );
        // "Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos."

        return Optional.of(new NotificationCommand(..., NotificationType.SMS, mensaje, ...));
    }
}
```

> *"Spring inyecta automáticamente TODAS las clases `@Component` que implementan
> `NotificationStrategy` en la lista. Si creamos `PedidoCreadoEmailStrategy @Component`,
> Spring la incluye sin que `NotificationRouterService` cambie una línea."*

---

### ❓ Preguntas y respuestas

**"¿Qué es DIP en una frase?"**
> "Los módulos de alto nivel — servicios de negocio — no dependen de módulos de
> bajo nivel — Kafka, JPA. Ambos dependen de interfaces abstractas.
> La dirección de la dependencia se invierte: la infraestructura implementa
> las interfaces que define el dominio."

**"¿Puedes cambiar de RoundRobin a 'asignar al más cercano'?"**
> "Sí. Creo `ClosestDriverStrategy implements DriverAssignmentStrategy`, la anoto
> con `@Component`, cambio el `@Bean` en `DomainConfig.java`. `OrderProcessorService`
> no cambia porque solo llama a `estrategiaAsignacion.asignar(pedido)`."

**"¿Qué es AtomicInteger?"**
> "Una variable entera thread-safe. Garantiza que si dos hilos intentan
> incrementarla simultáneamente, cada uno obtiene un valor diferente sin
> condiciones de carrera — sin usar `synchronized`."

---

---

## TURNO 8 — PostgreSQL, JPA y separación de capas

### Lo que acaba de pasar (Helen mostró driver_simulator y dashboard_aggregator)
Ahora tú muestras la persistencia y la separación entre dominio e infraestructura.

---

### 🗣️ Lo que dices — Las 3 tablas

> "PostgreSQL tiene tres tablas. `drivers` tiene los 5 repartidores precargados
> por `init.sql` — Carlos, Laura, Andrés, Valentina y Miguel.
> `orders` tiene una fila por pedido: el `order_processor` hace el INSERT
> cuando María pide, y el `dashboard_aggregator` hace el UPDATE en cada evento.
> `order_events` tiene 5 filas para el pedido de María — una por evento,
> nunca se modifica. La columna `event_id` es UNIQUE para garantizar idempotencia."

---

### 📦 Qué tiene la BD con el pedido de María

```sql
-- Tabla orders: una fila que va cambiando
-- INSERT por order_processor al recibir el comando de María:
id: 'deca59e7-...'  |  customer_name: 'María García'
product: 'Hamburguesa Doble'  |  quantity: 1
address: 'Calle 100 #15-30'   |  status: 'CREADO'   |  driver_id: NULL

-- UPDATE por dashboard_aggregator (evento DRIVER_ASSIGNED):
status: 'ASIGNADO'  |  driver_id: 'D-001'   (Carlos Mendoza)

-- UPDATE (DELIVERY_STARTED): status: 'EN_CAMINO'
-- UPDATE (DRIVER_ARRIVING):  status: 'LLEGANDO'
-- UPDATE (ORDER_DELIVERED):  status: 'ENTREGADO'

-- Tabla order_events: 5 filas inmutables
event_type        | event_timestamp
ORDER_CREATED     | 2026-11-12 19:57:23  ← María hizo click
DRIVER_ASSIGNED   | 2026-11-12 19:57:27  ← 3 segundos después
DELIVERY_STARTED  | 2026-11-12 19:57:32  ← 5 segundos
DRIVER_ARRIVING   | 2026-11-12 19:57:40  ← 8 segundos
ORDER_DELIVERED   | 2026-11-12 19:57:45  ← 22 segundos en total
```

---

### 🖥️ Muestra 1 — Consulta a PostgreSQL en la terminal

**Escribe exactamente esto y Enter:**
```
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT id, customer_name, status, driver_id FROM orders;"
```

**Verás algo así:**
```
         id (truncado)       | customer_name  |  status   | driver_id
-----------------------------+----------------+-----------+-----------
 deca59e7-8b5d-...           | Maria Garcia   | ENTREGADO | D-001
```

> *"El pedido de María en PostgreSQL. `id` = UUID que generó `order_api`.
> `driver_id` = D-001 → Carlos Mendoza. `status` = ENTREGADO."*

**Luego ejecuta también:**
```
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"
```

> *"5 filas — el log de auditoría completo. 22 segundos desde el click hasta
> la entrega. Esto es **Event Sourcing**: el log inmutable de todo lo que pasó."*

---

### 💻 Muestra 2 — Pedido.java vs PedidoEntity.java (abre ambos en tabs)

**Archivo 1:** `order_processor/src/main/java/.../domain/model/Pedido.java`

```java
// MODELO DE DOMINIO — no tiene ni una anotación de Spring ni JPA
public class Pedido {

    // LÍNEAS 44-48: campos INMUTABLES — final = no cambian después de crearse
    private final String id;           // "abc-123-pedido-maria"
    private final String customerName; // "María García"
    private final String product;      // "Hamburguesa Doble"
    private final int    quantity;     // 1
    private final String address;      // "Calle 100 #15-30, Bogotá"
    // NO tiene @Entity, NO tiene @Column, NO importa nada de Hibernate

    // No hay setters públicos como setStatus("ASIGNADO")
    // El estado solo cambia a través de métodos semánticos:
    public void asignarRepartidor(String dId, String dName) { estadoActual.asignarRepartidor(this, dId, dName); }
    public void iniciarEntrega()   { estadoActual.iniciarEntrega(this);   }
    public void registrarLlegada() { estadoActual.registrarLlegada(this); }
    public void completarEntrega() { estadoActual.completarEntrega(this); }
}
```

**Archivo 2:** `order_processor/src/main/java/.../persistence/entity/PedidoEntity.java`

```java
// ENTIDAD JPA — solo para hablar con PostgreSQL
// Pedido.java no sabe que esta clase existe

@Entity                    // LÍNEA 16: "esta clase mapea una tabla en PostgreSQL"
@Table(name = "orders")    // LÍNEA 17: mapea la tabla "orders"
public class PedidoEntity {

    @Id                                          // clave primaria
    @Column(name = "id", length = 36)            // columna "id" VARCHAR(36)
    private String id;                           // "abc-123-pedido-maria"

    @Column(name = "customer_name", length = 150)
    private String customerName;                 // "María García"

    @Column(name = "status", length = 30)
    private String status;                       // "CREADO", "ASIGNADO", etc.

    // Setters SOLO para los 3 campos que pueden cambiar:
    public void setStatus(String status)    { this.status   = status; }
    // Cuando el aggregator procesa DRIVER_ASSIGNED → setStatus("ASIGNADO")

    public void setDriverId(String dId)     { this.driverId = dId; }
    // Cuando Carlos es asignado → setDriverId("D-001")

    public void setUpdatedAt(Instant upAt)  { this.updatedAt = upAt; }
    // NO hay setCustomerName → el nombre de María nunca cambia
}
// JpaOrderRepositoryAdapter convierte Pedido ↔ PedidoEntity
// El dominio nunca importa @Entity. La BD nunca ve Pedido.java.
```

> *"El adaptador `JpaOrderRepositoryAdapter` traduce entre los dos.
> El dominio nunca importa `@Entity`. La base de datos nunca ve `Pedido.java`."*

---

### 🗣️ Lo que dices — `ddl-auto=validate`

> "En `application.properties` tenemos `spring.jpa.hibernate.ddl-auto=validate`.
> Hibernate no crea ni modifica tablas. Solo verifica al arrancar que
> `PedidoEntity` tenga los mismos campos que la tabla `orders` creada por `init.sql`.
> Con `create` Hibernate borraría la tabla en cada arranque — el pedido de María
> del día anterior desaparece. Con `validate` solo comprueba — el control del
> esquema está en `init.sql`, versionado en Git."

---

### ❓ Preguntas y respuestas

**"¿Por qué `ddl-auto=validate` y no `update`?"**
> "Con `update` Hibernate intenta alterar el esquema automáticamente — puede
> borrar columnas accidentalmente en producción. Con `validate` el esquema
> está bajo control explícito en `init.sql`, versionado en Git."

**"¿Por qué Pedido.java no tiene @Entity?"**
> "Porque mezclar lógica de dominio con anotaciones de persistencia viola
> la responsabilidad única. `Pedido` es un POJO puro — no sabe nada de
> bases de datos. Si mañana cambiamos de PostgreSQL a MongoDB, el dominio
> no cambia una línea."

---

---

## TURNO 10 — Docker, cgroups y pruebas unitarias

### Lo que acaba de pasar (Helen mostró WebSocket y Java Records)
Tú cierras con la infraestructura de contenedores y las pruebas.

---

### 🗣️ Lo que dices — Dockerfile multietapa

> "Cada microservicio tiene un **Dockerfile de dos etapas**. Stage 1 compila
> con Maven y JDK completo. Stage 2 copia solo el JAR resultante en una imagen
> mínima con JRE Alpine. La imagen final no tiene Maven, no tiene código fuente.
> Pesa ~150MB en lugar de ~500MB."

---

### 🖥️ Muestra 1 — Dockerfile (abre este archivo)

**Archivo:** `push_notification_worker/Dockerfile`
(o cualquier otro Dockerfile del proyecto)

```dockerfile
# ═══ STAGE 1: BUILD ═══
# Esta imagen NUNCA llega a producción — solo sirve para compilar
FROM maven:3.9.6-eclipse-temurin-17 AS builder
# maven = herramienta de compilación
# eclipse-temurin-17 = JDK Java 17
# "AS builder" = nombre del stage para referenciarlo después

WORKDIR /workspace

# TRUCO DE CACHÉ:
# Copiamos SOLO los pom.xml ANTES que el código fuente.
# Si el código cambia pero los pom.xml no, Docker reutiliza
# esta capa y Maven NO re-descarga internet.
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY push_notification_worker/pom.xml push_notification_worker/pom.xml

RUN mvn dependency:go-offline -pl common,push_notification_worker -am -q
# Descarga todas las dependencias y las guarda en caché

COPY common/src                   common/src
COPY push_notification_worker/src push_notification_worker/src

RUN mvn clean package -pl common,push_notification_worker -am -DskipTests -q
# Compila y genera el .jar
# -DskipTests = no correr pruebas aquí (se corren separado en CI)

# ═══ STAGE 2: RUNTIME ═══
# Esta es la imagen FINAL — pequeña y segura
FROM eclipse-temurin:17-jre-alpine
# jre (no jdk) = solo para ejecutar, sin compilador
# alpine = Linux minimalista (~5MB). Sin esto serían ~200MB más.

WORKDIR /app

# SEGURIDAD: usuario no-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
# Si un atacante explota el JAR, llega como "appuser":
# - no puede instalar software
# - no puede leer /etc/shadow (contraseñas del sistema)
# - no puede escalar privilegios en el host del servidor

# Copia SOLO el JAR del stage anterior — nada más
COPY --from=builder /workspace/push_notification_worker/target/push_notification_worker-*.jar app.jar
# --from=builder = toma del stage llamado "builder"

# Flags de JVM para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# UseContainerSupport: JVM lee los límites de cgroups del contenedor
#   Sin esta flag: JVM ve RAM total del host (ej 16GB) → configura heap de 4GB
#   Con esta flag: JVM ve que el contenedor tiene 256MB → configura heap de 192MB
# MaxRAMPercentage=75.0: usa máximo 75% de los 256MB = 192MB para heap Java
# ExitOnOutOfMemoryError: si se acaba la memoria → proceso termina limpio
#   Docker lo reinicia. Mejor que degradarse lentamente sin memoria.

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

### 🖥️ Muestra 2 — docker stats

**En la terminal:**
```
docker stats --no-stream
```

**Señala la columna `MEM USAGE / LIMIT`:**
```
NAME                        MEM USAGE  /  LIMIT
rappi_order_processor        45.2MiB  /  384MiB   ← kernel bloquea si pasa
rappi_dashboard_aggregator   52.1MiB  /  384MiB
rappi_kafka                 210.3MiB  /  512MiB   ← Kafka necesita más RAM
rappi_postgres               38.5MiB  /  512MiB
```

> *"La columna `LIMIT` es el techo impuesto por **cgroups** del kernel Linux.
> `order_processor` usa 45MB ahora y nunca puede pasar de 384MB.
> En docker-compose.yml escribimos `deploy.resources.limits: memory: 384M`
> y Docker le dice al kernel que cree un cgroup con ese límite.
> Con `-XX:+UseContainerSupport`, la JVM respeta ese límite — sin esta flag
> vería los 16GB del host y configuraría un heap de 4GB para un contenedor
> de 384MB, causando un OOM-Kill inmediato."*

---

### 🖥️ Muestra 3 — Pruebas unitarias

**En la terminal:**
```
mvn test -pl common,order_processor
```

**Mientras corre, dices:**
> *"Estas pruebas corren sin Docker, sin Kafka, sin PostgreSQL.
> Solo memoria RAM. Verifican el mismo flujo del demo: el pedido
> de María, la asignación de Carlos, el ciclo completo de estados."*

**Cuando termina y ves el resultado en verde, señala cada grupo:**

---

### 💻 Código de las pruebas — línea por línea

**Archivo:** `order_processor/src/test/.../domain/model/PedidoTest.java`

```java
@Test
@DisplayName("Ciclo completo: CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO")
void cicloDeVidaCompleto() {

    // ARRANGE: crear el pedido de María
    Pedido pedido = new Pedido("order-001", "María García", "Hamburguesa", 2, "Calle 100 #15-30");
    assertEquals("CREADO", pedido.getNombreEstado()); // estado inicial

    // ACT & ASSERT: cada transición del patrón State

    pedido.asignarRepartidor("D-001", "Carlos Mendoza");
    assertEquals("ASIGNADO",        pedido.getNombreEstado()); // cambió
    assertEquals("D-001",           pedido.getDriverId());     // se guardó
    assertEquals("Carlos Mendoza",  pedido.getDriverName());   // se guardó

    pedido.iniciarEntrega();         // Carlos salió del restaurante
    assertEquals("EN_CAMINO", pedido.getNombreEstado());

    pedido.registrarLlegada();       // Carlos está llegando
    assertEquals("LLEGANDO",  pedido.getNombreEstado());

    pedido.completarEntrega();       // Hamburguesa entregada
    assertEquals("ENTREGADO", pedido.getNombreEstado());
}

@Test
@DisplayName("No debe iniciar entrega desde estado CREADO")
void noDebeIniciarEntregaDesdeCreado() {

    Pedido pedido = new Pedido("order-001", "María García", "Hamburguesa", 2, "Calle 100 #15-30");

    var ex = assertThrows(IllegalStateException.class, pedido::iniciarEntrega);
    // assertThrows VERIFICA que se lance la excepción
    // Si NO se lanza → la prueba FALLA

    assertTrue(ex.getMessage().contains("CREADO"));
    // el mensaje debe mencionar el estado para ser descriptivo
}
```

**Archivo:** `order_processor/src/test/.../application/OrderProcessorServiceTest.java`

```java
@ExtendWith(MockitoExtension.class) // activa Mockito
class OrderProcessorServiceTest {

    @Mock
    private OrderRepositoryPort repositorio;
    // objeto FALSO — no toca PostgreSQL
    // solo registra las llamadas que recibe

    @Mock
    private OrderEventPublisherPort publicador;
    // objeto FALSO — no toca Kafka

    @Mock
    private DriverAssignmentStrategy estrategiaAsignacion;
    // objeto FALSO — no asigna repartidores reales

    @InjectMocks
    private OrderProcessorService service;
    // crea el servicio REAL e inyecta los 3 mocks en su constructor

    @Test
    @DisplayName("Debe ejecutar las operaciones en el orden correcto")
    void debeEjecutarEnOrdenCorrecto() {

        // ARRANGE: comando de María
        OrderCreatedCommand comando = new OrderCreatedCommand(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "María García", "Hamburguesa doble", 2, "Calle 100 #15-30", Instant.now()
        );

        // ACT: ejecutar el flujo real (con mocks, sin Kafka ni BD)
        service.procesarComando(comando);

        // ASSERT: verificar el ORDEN EXACTO de las llamadas
        var ordenVerificacion = inOrder(repositorio, publicador, estrategiaAsignacion);

        ordenVerificacion.verify(repositorio).guardar(any());
        // ↑ "guardar fue llamado PRIMERO"

        ordenVerificacion.verify(publicador).publicarOrderCreated(any());
        // ↑ "publicarOrderCreated fue SEGUNDO"

        ordenVerificacion.verify(estrategiaAsignacion).asignar(any());
        // ↑ "asignar fue TERCERO"

        ordenVerificacion.verify(repositorio).actualizar(any());
        // ↑ "actualizar fue CUARTO"

        ordenVerificacion.verify(publicador).publicarDriverAssigned(any());
        // ↑ "publicarDriverAssigned fue QUINTO"
        // Si cualquier paso falta o está en otro orden → la prueba FALLA

    }

    @Test
    @DisplayName("Si la estrategia falla, no debe publicar DRIVER_ASSIGNED")
    void siEstrategiaFallaNODebePublicarDriverAssigned() {

        // Configurar el mock para que FALLE cuando se llame asignar()
        doThrow(new IllegalStateException("Sin repartidores disponibles"))
            .when(estrategiaAsignacion).asignar(any());
        // "cuando alguien llame asignar() → lanza IllegalStateException"

        OrderCreatedCommand comando = new OrderCreatedCommand(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "María García", "Hamburguesa doble", 2, "Calle 100 #15-30", Instant.now()
        );

        // El servicio debe lanzar la excepción
        assertThrows(IllegalStateException.class, () -> service.procesarComando(comando));

        // verificar que publicarDriverAssigned NUNCA fue llamado
        verify(publicador, never()).publicarDriverAssigned(any());
        // "never()" = verificar que este método NO fue llamado
        // Si María no tiene repartidor disponible, NO se publica en Kafka
    }
}
```

**Al ver los 29 resultados en verde, dices:**
> *"29 pruebas en verde. Sin Spring Boot, sin Kafka, sin PostgreSQL.
> Solo memoria RAM. Las pruebas con Mockito verifican que el `OrderProcessorService`
> llamó a los puertos en el orden correcto: `guardar → publicarOrderCreated →
> asignar → actualizar → publicarDriverAssigned`.
> Si alguno falta o está en otro orden, la prueba falla."*

---

### ❓ Preguntas y respuestas

**"¿Qué son cgroups?"**
> "Control groups — mecanismo del kernel Linux para limitar recursos de procesos.
> En docker-compose.yml pusimos `memory: 384M` para `order_processor`. Docker
> le dice al kernel que cree un cgroup con ese límite. El kernel garantiza que
> el proceso no puede usar más. Con `-XX:+UseContainerSupport`, la JVM lee
> ese límite de cgroup — sin esa flag vería los 16GB del host."

**"¿Qué es `@Mock` exactamente?"**
> "Crea un objeto falso que reemplaza la dependencia real. El `@Mock OrderRepositoryPort`
> no toca PostgreSQL — solo registra qué métodos fueron llamados. `verify(repositorio)
> .guardar(any())` pregunta si se llamó el método. Si no se llamó, la prueba falla."

**"¿Por qué `any()` en el verify?"**
> "`any()` significa 'con cualquier argumento'. Verificamos que `guardar` fue llamado
> con algún `Pedido` — no nos importa cuál exactamente. Si quisiéramos verificar
> el contenido exacto usaríamos `ArgumentCaptor`."

**"¿Por qué `-DskipTests` en el Dockerfile?"**
> "Las pruebas se ejecutan en CI/CD con un comando separado — `mvn test`.
> En el Dockerfile se omiten para que el build de imagen sea rápido y no requiera
> levantar BD ni brokers dentro del proceso de build."

---

## RESUMEN RÁPIDO — frases clave para los 4 turnos

| Turno | La frase más importante |
|---|---|
| **2** | "Kafka para eventos que varios servicios necesitan. RabbitMQ para comandos que procesa uno solo." |
| **4** | "Work Queue: uno solo toma el mensaje. Topic Exchange: enruta por patrones con wildcards." |
| **6** | "Strategy: algoritmo intercambiable. DIP: el servicio no sabe que existe Kafka ni JPA." |
| **8** | "Pedido es POJO puro. PedidoEntity es JPA. El adaptador traduce. El dominio nunca importa @Entity." |
| **10** | "Stage 1 compila. Stage 2 ejecuta. 29 pruebas en verde sin Kafka ni BD." |

## COMANDOS DE TERMINAL — copia y pega en la presentación

```bash
# Turno 2 — Muestra 1
docker compose ps

# Turno 2 — Muestra 2
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events

# Turno 8 — Muestra 1a
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT id, customer_name, status, driver_id FROM orders;"

# Turno 8 — Muestra 1b
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"

# Turno 10 — Muestra 2
docker stats --no-stream

# Turno 10 — Muestra 3
mvn test -pl common,order_processor
```

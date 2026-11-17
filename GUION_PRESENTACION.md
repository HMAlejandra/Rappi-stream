# Guión de Presentación — Juliana
## Rappi-Stream | 15 minutos en total | 4 turnos

> Cada sección tiene:
> - 🗣️ Las palabras exactas que dices
> - 📦 Qué está pasando con el pedido de María García en ese momento
> - 💻 El código con cada línea explicada
> - ❓ Preguntas del profesor y cómo responderlas

---

## EL HILO CONDUCTOR DE TODA LA PRESENTACIÓN

> **María García** entra al dashboard en `http://localhost:8087`, hace click en
> **+ Nuevo Pedido**, elige **Hamburguesa Doble**, escribe su nombre y dirección,
> y hace click en **Pedir ahora**.
> En los próximos **22 segundos** el sistema procesa todo automáticamente:
> asigna a Carlos Mendoza, simula la ruta, envía notificaciones, y el dashboard
> se actualiza en tiempo real sin que ella recargue la página.

Ese recorrido es lo que explicas en cada turno.

---

## TURNO 2 — Arquitectura e infraestructura

### Prepara esto ANTES de hablar
Abre dos terminales listas.

---

### 🗣️ Lo que dices

> "Cuando María pide su hamburguesa, su pedido pasa por **dos brokers de mensajería
> con propósitos distintos**. El comando 'crear pedido' va por **RabbitMQ** porque
> solo un servicio lo debe procesar. Luego, cada cambio de estado — CREADO, ASIGNADO,
> EN_CAMINO, LLEGANDO, ENTREGADO — se publica como evento en **Kafka**, donde tres
> servicios distintos lo leen al mismo tiempo e independientemente: el simulador del
> repartidor, el router de notificaciones, y el aggregator del dashboard."

---

### 📦 El pedido de María en este turno

```
María hace click "Pedir ahora"
        │
        ▼
  RabbitMQ — new_orders_queue
  Mensaje: "Crear pedido de Hamburguesa para María García, Calle 100"
  → solo lo toma order_processor (Work Queue)
        │
        ▼
  order_processor publica en Kafka:
  Evento #1 clave="abc-123" → ORDER_CREATED
  Evento #2 clave="abc-123" → DRIVER_ASSIGNED (Carlos D-001)

  Los 3 consumidores de Kafka reciben AMBOS eventos:
  ┌── driver_simulator     → "fue asignado, inicio la simulación de ruta"
  ├── notification_router  → "Carlos llega, preparo el SMS para María"
  └── dashboard_aggregator → "actualizo el estado en el dashboard de María"
```

---

### 🖥️ Muestra 1 — `docker compose ps`

```bash
docker compose ps
```

**Señala en la salida:**
- No hay `zookeeper` → *"Kafka en modo KRaft: sin ZooKeeper, el broker gestiona sus propios metadatos."*
- `rappi_kafka_init` aparece `Exited (0)` → *"Init container que creó el topic y terminó exitosamente."*

---

### 🖥️ Muestra 2 — Particiones de Kafka

```bash
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events
```

**Salida:**
```
Topic: order_events   PartitionCount: 3   ReplicationFactor: 1
  Partition: 0   Leader: 1
  Partition: 1   Leader: 1
  Partition: 2   Leader: 1
```

**Lo que dices:**
> "3 particiones. La clave de partición es el `orderId` — el UUID del pedido de María.
> Kafka calcula `hash(orderId) % 3` y todos sus eventos van siempre a la misma partición.
> Eso garantiza el orden: CREADO llega antes que DRIVER_ASSIGNED, que llega antes que
> DELIVERY_STARTED. Nunca puede llegar ENTREGADO antes de CREADO para el mismo pedido."

---

### 💻 El código que explica esto

**Archivo:** `order_processor/src/main/java/.../infrastructure/kafka/KafkaOrderEventPublisher.java`

Busca el método `publicar` (al final del archivo):

```java
// LÍNEA 83: orderId es la CLAVE DE PARTICIÓN
//           Todos los eventos del pedido de María usan el mismo "abc-123"
//           → van siempre a la misma partición → llegan en orden
kafkaTemplate.send(TOPIC,   orderId,     envelopeJson);
//                 ↑         ↑            ↑
//           "order_events"  "abc-123"    el mensaje JSON completo
```

Busca el método `publicarOrderCreated`:

```java
// LÍNEA 54: construye el JSON del payload con los datos de María
String payloadJson = String.format(
    "{\"orderId\":\"%s\",\"customerName\":\"%s\",\"product\":\"%s\"," +
     "\"quantity\":%d,\"address\":\"%s\"}",
    pedido.getId(),           // "abc-123-pedido-maria"
    customerName,             // "María García"
    product,                  // "Hamburguesa Doble"
    pedido.getQuantity(),     // 1
    address                   // "Calle 100 #15-30, Bogotá"
);
// Este JSON es el "payload" que viajará dentro del envelope de Kafka
```

**Archivo:** `common/src/main/java/.../events/OrderEventEnvelope.java`

```java
// Este record es el "sobre" que envuelve TODOS los eventos de Kafka
public record OrderEventEnvelope(
    String eventId,          // línea 44: UUID único — clave de idempotencia en BD
    String orderId,          // línea 50: "abc-123" — clave de partición Kafka
    OrderEventType eventType,// línea 53: ORDER_CREATED, DRIVER_ASSIGNED, etc.
    Instant timestamp,       // línea 56: cuándo ocurrió el evento
    String payload           // línea 65: el JSON con los datos específicos
)
// Ejemplo para el pedido de María:
// eventId:   "evt-uuid-001"
// orderId:   "abc-123-pedido-maria"
// eventType: ORDER_CREATED
// timestamp: 2026-11-12T19:57:23Z
// payload:   {"orderId":"abc-123","customerName":"María García",...}
```

---

### ❓ Preguntas y respuestas

**"¿Por qué 3 particiones y no 1?"**
> "Con 1 partición, si María y Juan piden al mismo tiempo sus eventos se procesan en cola.
> Con 3 particiones, el pedido de María va a la partición 0 y el de Juan a la partición 1
> — se procesan en paralelo. Para el MVP 3 es suficiente."

**"¿Qué pasa si el dashboard_aggregator se reinicia mientras María espera?"**
> "Con `auto-offset-reset=earliest` lee todos los eventos desde el principio y reconstruye
> el estado. La columna `event_id UNIQUE` en PostgreSQL evita duplicar filas."

---

---

## TURNO 4 — RabbitMQ: Work Queue y Topic Exchange

### Prepara esto ANTES de hablar
Abre `http://localhost:15672` (usuario: `rappi_admin`, pass: `rappi_rmq_2024`).

---

### 🗣️ Lo que dices — Work Queue

> "Cuando María hace click en 'Pedir ahora', el `order_api` crea un comando inmutable
> con todos sus datos y lo publica en la cola `new_orders_queue`. Esto es el patrón
> **Work Queue**: si hubiera 3 instancias del `order_processor`, el pedido de María
> lo tomaría uno solo — no los tres. RabbitMQ hace round-robin automáticamente.
> Esto evita que el mismo pedido se procese dos veces."

---

### 📦 El pedido de María en este turno

```
María hace click → order_api publica esto en new_orders_queue:

OrderCreatedCommand {
  commandId:    "cmd-7f3a-uuid"           ← ID único de este comando
  orderId:      "abc-123-pedido-maria"    ← ID del pedido (lo generó order_api)
  customerName: "María García"
  product:      "Hamburguesa Doble"
  quantity:     1
  address:      "Calle 100 #15-30, Bogotá"
  issuedAt:     2026-11-12T19:57:20Z
}

Cuando Carlos llega → notification_router publica en exchange "notifications":
  routing key: "notify.client.sms"
  mensaje: "Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos."
  → va a client_sms_queue → sms_worker-1 lo procesa

Cuando el pedido es entregado → notification_router publica:
  routing key: "notify.driver.push"
  mensaje: "¡Entrega completada! Pedido abc-123 entregado. ¡Buen trabajo, Carlos Mendoza!"
  → va a driver_push_queue → push_notification_worker-2 lo procesa
```

---

### 💻 El código — línea por línea

**Archivo:** `common/src/main/java/.../commands/OrderCreatedCommand.java`

```java
// LÍNEA 23: "record" = clase inmutable de Java 17
// Genera automáticamente: constructor, getters, equals, hashCode, toString
// No tiene setters → una vez creado, no se puede modificar
public record OrderCreatedCommand(

    String commandId,    // ID único del comando (UUID)
    String orderId,      // ID del pedido - será clave Kafka
    String customerName, // "María García"
    String product,      // "Hamburguesa Doble"
    int quantity,        // 1
    String address,      // "Calle 100 #15-30, Bogotá"
    Instant issuedAt     // cuándo se creó

) {
    // LÍNEA 46: "constructor compacto" — se ejecuta automáticamente al crear el record
    // Fail-Fast: si algo es inválido, el objeto NUNCA EXISTE
    public OrderCreatedCommand {

        // LÍNEA 47: si commandId es null → NullPointerException inmediata
        Objects.requireNonNull(commandId, "commandId no puede ser nulo");

        // LÍNEA 54: si quantity es 0 o negativo → IllegalArgumentException
        // Ejemplo: María no puede pedir -1 hamburguesas
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "quantity debe ser mayor que cero, recibido: " + quantity);
        }
        // Si algo falla aquí, el rabbitTemplate.convertAndSend() NUNCA se ejecuta
        // El error inválido no llega a RabbitMQ ni a ningún otro servicio
    }
}
```

**Archivo:** `notification_router/src/main/java/.../strategy/DriverArrivingSmsStrategy.java`

```java
// LÍNEA 36: este método responde "¿soy responsable de este evento?"
public boolean aplica(OrderEventType eventType) {
    return eventType == OrderEventType.DRIVER_ARRIVING;
    // true solo cuando Carlos está llegando a casa de María
    // false para todos los demás eventos
}

// LÍNEA 42: este método construye el SMS real para María
public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
    // LÍNEA 44: deserializa el payload JSON → objeto Java
    DriverArrivingPayload payload =
        objectMapper.readValue(envelope.payload(), DriverArrivingPayload.class);
    // payload.driverName()      → "Carlos Mendoza"
    // payload.estimatedArrival() → "2 minutos"

    // LÍNEA 48: construye el texto del SMS
    String mensaje = String.format(
        "Tu repartidor %s está llegando. Tiempo estimado: %s.",
        payload.driverName(),       // "Carlos Mendoza"
        payload.estimatedArrival()  // "2 minutos"
    );
    // resultado: "Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos."

    // LÍNEA 53: empaqueta el SMS en un NotificationCommand
    NotificationCommand comando = new NotificationCommand(
        UUID.randomUUID().toString(),   // ID único de esta notificación
        envelope.orderId(),              // "abc-123-pedido-maria"
        envelope.orderId(),              // recipientId (quién recibe)
        NotificationType.SMS,            // tipo: SMS
        mensaje,                         // el texto de arriba
        Instant.now()
    );

    // LÍNEA 60: Optional.of() = "tengo un valor"
    // Optional.empty() = "no tengo nada" (si hubiera fallado la deserialización)
    return Optional.of(comando);
}
```

**Archivo:** `notification_router/src/main/java/.../service/NotificationRouterService.java`

```java
// LÍNEA 38: Spring inyecta AUTOMÁTICAMENTE todas las clases @Component
// que implementan NotificationStrategy — DriverArrivingSmsStrategy Y OrderDeliveredPushStrategy
private final List<NotificationStrategy> estrategias;

// LÍNEA 55: cuando llega un evento de Kafka, itera las estrategias
public void procesarEvento(OrderEventEnvelope envelope) {
    for (NotificationStrategy estrategia : estrategias) {
        // LÍNEA 56: pregunta a cada estrategia si es su responsabilidad
        if (estrategia.aplica(envelope.eventType())) {
            // Si llega DRIVER_ARRIVING:
            //   DriverArrivingSmsStrategy.aplica()   → true  → entra aquí
            //   OrderDeliveredPushStrategy.aplica()  → false → lo salta

            Optional<NotificationCommand> comando = estrategia.construir(envelope);
            // construye: "Tu repartidor Carlos Mendoza está llegando..."

            comando.ifPresent(cmd -> {
                // LÍNEA 61: decide la routing key según el tipo
                String routingKey = routingKeyPara(cmd.type());
                // cmd.type() = SMS → routingKey = "notify.client.sms"

                // LÍNEA 63: publica en el Topic Exchange de RabbitMQ
                rabbitTemplate.convertAndSend(
                    "notifications",     // nombre del exchange
                    "notify.client.sms", // routing key
                    cmd                  // el NotificationCommand serializado a JSON
                );
                // El exchange compara "notify.client.sms" con el patrón "notify.client.*"
                // coincide → manda a client_sms_queue → sms_worker-1 lo consume
            });
            return; // una sola estrategia por evento — no sigue iterando
        }
    }
}
```

---

### 🖥️ Muestra 1 — Panel RabbitMQ en `http://localhost:15672`

1. **Exchanges** → muestra `notifications` de tipo `topic`
2. Click en `notifications` → muestra los bindings:
   - `notify.client.*` → `client_sms_queue` ← *"para SMS a María"*
   - `notify.driver.*` → `driver_push_queue` ← *"para Push a Carlos"*
3. Di: *"El `*` reemplaza exactamente una palabra. `notify.client.sms` coincide.
   Si mañana agregamos email, `notify.client.email` también coincidiría sin tocar el exchange."*

---

### 🖥️ Muestra 2 — Réplicas

```bash
docker compose ps
```

Señala:
```
rappi-stream-sms_worker-1                Up
rappi-stream-sms_worker-2                Up
rappi-stream-push_notification_worker-1  Up
rappi-stream-push_notification_worker-2  Up
```

> "Dos instancias de cada worker. Si María y Juan reciben SMS al mismo tiempo,
> `sms_worker-1` procesa el de María y `sms_worker-2` el de Juan — en paralelo."

---

### ❓ Preguntas y respuestas

**"¿Por qué no usar Kafka para las notificaciones también?"**
> "Kafka es para cuando múltiples servicios necesitan el mismo evento. Las notificaciones
> van a un destinatario específico y deben procesarse exactamente una vez. RabbitMQ con
> Topic Exchange es el patrón correcto para ese enrutamiento selectivo."

**"¿Qué pasa si un sms_worker cae?"**
> "El mensaje queda en la queue — las queues son durables. Cuando el worker se recupera,
> toma el mensaje. María lo recibe con pequeño retraso, pero no se pierde."

---

---

## TURNO 6 — Patrón Strategy y DIP

### Prepara esto ANTES de hablar
Abre estos archivos en tabs en el editor:
1. `.../domain/strategy/DriverAssignmentStrategy.java`
2. `.../domain/strategy/RoundRobinAssignmentStrategy.java`
3. `.../application/OrderProcessorService.java`
4. `.../notificationrouter/strategy/NotificationStrategy.java`
5. `.../strategy/DriverArrivingSmsStrategy.java`
6. `.../strategy/OrderDeliveredPushStrategy.java`
7. `.../service/NotificationRouterService.java`

---

### 🗣️ Lo que dices — Strategy en order_processor

> "Para asignar a Carlos Mendoza al pedido de María usamos el **patrón Strategy**.
> El `OrderProcessorService` llama a `estrategia.asignar(pedido)` sin saber cómo
> funciona la asignación. Hoy es RoundRobin. Mañana podría ser por cercanía.
> El servicio no cambia."

---

### 📦 El pedido de María en este turno

```
Pedido de María llega → OrderProcessorService llama:
  estrategiaAsignacion.asignar(pedidoDeMaría)

Internamente, RoundRobinAssignmentStrategy hace:
  cursor = 0 (primer pedido del día)
  disponibles = [Carlos D-001, Laura D-002, Andrés D-003, Valentina D-004, Miguel D-005]
  índice = abs(0 % 5) = 0 → selecciona Carlos Mendoza (D-001)
  pedido.asignarRepartidor("D-001", "Carlos Mendoza")

Segundo pedido (Juan): cursor=1 → Laura D-002
Tercer pedido (Ana):   cursor=2 → Andrés D-003
Cuarto pedido:         cursor=3 → Valentina D-004
Quinto pedido:         cursor=4 → Miguel D-005
Sexto pedido:          cursor=5, abs(5%5)=0 → vuelve a Carlos
```

---

### 💻 El código — línea por línea

**Archivo:** `DriverAssignmentStrategy.java`

```java
// Esta es la INTERFAZ del patrón Strategy
// Define el CONTRATO — qué debe hacer toda estrategia de asignación
public interface DriverAssignmentStrategy {

    // ÚNICA LÍNEA: un solo método
    // "Dado este pedido, asigna un repartidor disponible"
    void asignar(Pedido pedido);

    // Si no hay repartidores → lanza IllegalStateException
    // Si hay → llama a pedido.asignarRepartidor() internamente
}
// OrderProcessorService depende de esta interfaz, NO de RoundRobin directamente
// Cambiar de estrategia = cambiar el @Bean en DomainConfig, no el servicio
```

**Archivo:** `RoundRobinAssignmentStrategy.java`

```java
// LÍNEA 1: implementa la interfaz → es UNA estrategia concreta posible
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {

    // LÍNEA 15: lista inmutable de repartidores (cargada de la BD al arrancar)
    private final List<Driver> repartidores;
    // repartidores = [Carlos D-001, Laura D-002, Andrés D-003, Valentina D-004, Miguel D-005]

    // LÍNEA 21: AtomicInteger = contador thread-safe
    // "thread-safe" = si dos pedidos llegan al mismo tiempo (dos hilos),
    // cada uno obtiene un número diferente sin pisarse
    private final AtomicInteger cursor = new AtomicInteger(0);
    // Sin AtomicInteger: ambos pedidos podrían leer cursor=0 → ambos van a Carlos
    // Con AtomicInteger: uno lee 0 (Carlos) y el otro lee 1 (Laura), garantizado

    @Override
    public void asignar(Pedido pedido) {

        // LÍNEA 37: filtra solo los disponibles
        // Si Carlos no está disponible ese día, simplemente no aparece
        List<Driver> disponibles = repartidores.stream()
                .filter(Driver::isAvailable)   // solo los que tienen available=true
                .toList();

        // LÍNEA 42: si nadie está disponible → falla inmediatamente
        if (disponibles.isEmpty()) {
            throw new IllegalStateException(
                "No hay repartidores disponibles para el pedido: " + pedido.getId());
        }

        // LÍNEA 47: selección circular
        // cursor.getAndIncrement() → lee el valor actual Y lo incrementa atómicamente
        // Primer pedido (María):  cursor era 0 → getAndIncrement() devuelve 0, deja cursor=1
        // Segundo pedido (Juan):  cursor era 1 → getAndIncrement() devuelve 1, deja cursor=2
        // abs(...) → por si el número se vuelve negativo en contadores muy grandes
        // % disponibles.size() → módulo = vuelta circular (5%5=0, vuelve al primero)
        int indice = Math.abs(cursor.getAndIncrement() % disponibles.size());
        Driver seleccionado = disponibles.get(indice);
        // Para María: indice=0, seleccionado = Carlos Mendoza (D-001)

        // LÍNEA 52: llama al método de NEGOCIO del pedido
        // Esto activa el PATRÓN STATE internamente:
        //   pedido está en EstadoCreado
        //   EstadoCreado.asignarRepartidor() → aplica asignación + cambia a EstadoAsignado
        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
        // pedido.asignarRepartidor("D-001", "Carlos Mendoza")
    }
}
```

**Archivo:** `OrderProcessorService.java`

```java
// LÍNEAS 47-50: las tres dependencias — TODAS son interfaces (DIP)
private final OrderRepositoryPort    repositorio;
//   ↑ no JpaRepository — no sabe nada de Hibernate ni PostgreSQL
private final OrderEventPublisherPort publicador;
//   ↑ no KafkaTemplate — no sabe nada de Kafka ni brokers
private final DriverAssignmentStrategy estrategiaAsignacion;
//   ↑ no RoundRobinAssignmentStrategy — no sabe qué algoritmo usa

// LÍNEAS 56-61: constructor — inyección ESTRICTA por constructor
// No hay @Autowired en campos — las dependencias son final, inmutables
public OrderProcessorService(OrderRepositoryPort repositorio,
                              OrderEventPublisherPort publicador,
                              DriverAssignmentStrategy estrategiaAsignacion) {
    this.repositorio          = repositorio;
    this.publicador           = publicador;
    this.estrategiaAsignacion = estrategiaAsignacion;
}

public void procesarComando(OrderCreatedCommand comando) {

    // PASO 1: crear el pedido de María en dominio (Fail-Fast)
    Pedido pedido = new Pedido(
        "abc-123-pedido-maria", // orderId del comando
        "María García",          // customerName
        "Hamburguesa Doble",     // product
        1,                       // quantity
        "Calle 100 #15-30"       // address
    );
    // Si quantity fuera 0 → IllegalArgumentException aquí → proceso termina

    // PASO 2: INSERT INTO orders (id, customer_name, ...) VALUES (...)
    repositorio.guardar(pedido);
    // repositorio es OrderRepositoryPort → Spring inyecta JpaOrderRepositoryAdapter
    // JpaOrderRepositoryAdapter convierte Pedido → PedidoEntity → SQL

    // PASO 3: publica ORDER_CREATED en Kafka (clave="abc-123-pedido-maria")
    publicador.publicarOrderCreated(pedido);
    // publicador es OrderEventPublisherPort → Spring inyecta KafkaOrderEventPublisher

    // PASO 3.5: espera 3 segundos
    // El dashboard muestra "Buscando repartidor..." durante este tiempo
    Thread.sleep(assignmentDelayMs); // 3000ms

    // PASO 4: RoundRobin asigna Carlos, pedido transiciona CREADO→ASIGNADO
    estrategiaAsignacion.asignar(pedido);
    // internamente: pedido.asignarRepartidor("D-001", "Carlos Mendoza")
    // → EstadoCreado.asignarRepartidor() → pedido.setEstado(new EstadoAsignado())

    // PASO 5: UPDATE orders SET status='ASIGNADO', driver_id='D-001' WHERE id='abc-123'
    repositorio.actualizar(pedido);

    // PASO 6: publica DRIVER_ASSIGNED en Kafka (clave="abc-123-pedido-maria")
    publicador.publicarDriverAssigned(pedido);
    // Este evento lo reciben: driver_simulator, notification_router, dashboard_aggregator
}
```

---

### 💻 El código del patrón State — qué pasa cuando se asigna Carlos

**Archivo:** `order_processor/src/main/java/.../domain/model/Pedido.java`

```java
// LÍNEA 95: método de negocio — no hay if-else sobre el estado
public void asignarRepartidor(String driverId, String driverName) {
    // delega al ESTADO ACTUAL del pedido
    estadoActual.asignarRepartidor(this, driverId, driverName);
    // Si estadoActual = EstadoCreado  → funciona, transiciona a ASIGNADO
    // Si estadoActual = EstadoAsignado → lanza IllegalStateException
    // Si estadoActual = EstadoEntregado → lanza IllegalStateException
    // No hay IF. El estado actual sabe qué puede y qué no puede hacer.
}
```

**Archivo:** `order_processor/src/main/java/.../domain/state/EstadoCreado.java`

```java
// Este archivo implementa el comportamiento cuando el pedido está en CREADO

// LÍNEA 18: la ÚNICA transición válida desde CREADO
@Override
public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
    // LÍNEA 20: aplica la asignación — guarda driverId y driverName en el pedido
    pedido.aplicarAsignacion(driverId, driverName);
    // pedido ahora tiene: driverId="D-001", driverName="Carlos Mendoza"

    // LÍNEA 21: cambia el estado — el pedido ahora es EstadoAsignado
    pedido.setEstado(new EstadoAsignado());
    // Si María vuelve a pedir asignarRepartidor(), ahora entrará en EstadoAsignado
    // y EstadoAsignado.asignarRepartidor() lanza IllegalStateException
}

// LÍNEAS 24-30: todas las demás operaciones NO son válidas desde CREADO
@Override
public void iniciarEntrega(Pedido pedido) {
    throw new IllegalStateException(
        "No se puede iniciar la entrega: el pedido está en estado CREADO. " +
        "Debe asignarse un repartidor primero."
    );
    // Ejemplo: no puedo decir que Carlos salió del restaurante si
    // ni siquiera le asignaron el pedido todavía
}
```

---

### ❓ Preguntas y respuestas

**"¿Qué es DIP con un ejemplo del proyecto?"**
> "`OrderProcessorService` es lógica de negocio — alto nivel.
> `KafkaTemplate` es infraestructura — bajo nivel. Sin DIP,
> `OrderProcessorService` importaría `KafkaTemplate` directamente.
> Con DIP, importa `OrderEventPublisherPort` — una interfaz.
> La flecha de dependencia se invierte: antes iba Dominio → Kafka,
> ahora va Kafka → Dominio (Kafka implementa la interfaz del dominio)."

**"¿Puedes cambiar de RoundRobin a 'asignar al más cercano'?"**
> "Sí. Creo `ClosestDriverStrategy implements DriverAssignmentStrategy`,
> la anoto con `@Component`, y en `DomainConfig.java` cambio el `@Bean`
> para instanciarla. `OrderProcessorService` no cambia porque solo llama
> a `estrategiaAsignacion.asignar(pedido)` — no sabe cuál es."

---

---

## TURNO 8 — PostgreSQL, JPA, Docker y Pruebas

### Prepara esto ANTES de hablar
Abre en el editor:
1. `order_processor/src/main/java/.../persistence/entity/PedidoEntity.java`
2. `order_processor/src/main/java/.../domain/model/Pedido.java`
3. `order_processor/Dockerfile`
4. `order_processor/src/test/.../domain/model/PedidoTest.java`
5. `order_processor/src/test/.../application/OrderProcessorServiceTest.java`
Abre una terminal lista.

---

### 🗣️ Lo que dices — Las 3 tablas

> "PostgreSQL tiene tres tablas. `drivers` tiene los 5 repartidores precargados
> por `init.sql` — Carlos, Laura, Andrés, Valentina y Miguel. `orders` tiene
> una fila por pedido: el `order_processor` hace el INSERT cuando María pide,
> y el `dashboard_aggregator` hace el UPDATE en cada evento — ASIGNADO, EN_CAMINO,
> LLEGANDO, ENTREGADO. `order_events` tiene 5 filas para el pedido de María,
> una por cada evento, nunca se modifica. La columna `event_id` es UNIQUE
> para garantizar idempotencia."

---

### 📦 Qué tiene la BD durante el pedido de María

```sql
-- Tabla orders: una sola fila que se va actualizando
-- Momento 1 → INSERT por order_processor
id: 'abc-123'  |  customer_name: 'María García'  |  status: 'CREADO'   |  driver_id: NULL

-- Momento 2 → UPDATE por dashboard_aggregator (evento DRIVER_ASSIGNED)
status: 'ASIGNADO'  |  driver_id: 'D-001'  (Carlos Mendoza)

-- Momento 3 → UPDATE (evento DELIVERY_STARTED)
status: 'EN_CAMINO'

-- Momento 4 → UPDATE (evento DRIVER_ARRIVING)
status: 'LLEGANDO'

-- Momento 5 → UPDATE (evento ORDER_DELIVERED)
status: 'ENTREGADO'

-- Tabla order_events: 5 filas inmutables (nunca se modifican)
event_type       | event_timestamp
ORDER_CREATED    | 2026-11-12 19:57:23  ← María hizo click
DRIVER_ASSIGNED  | 2026-11-12 19:57:27  ← Carlos asignado (3s después)
DELIVERY_STARTED | 2026-11-12 19:57:32  ← Carlos salió del restaurante (5s)
DRIVER_ARRIVING  | 2026-11-12 19:57:40  ← Carlos está llegando (8s)
ORDER_DELIVERED  | 2026-11-12 19:57:45  ← Hamburguesa entregada (5s) ← 22s en total
```

---

### 💻 El código — separación Pedido vs PedidoEntity

**Archivo:** `Pedido.java` (el dominio — POJO puro)

```java
// Este es el pedido que usa el DOMINIO — no tiene ninguna anotación de Spring ni JPA
public class Pedido {

    // LÍNEAS 44-48: campos INMUTABLES — final = no se pueden cambiar después de crearse
    private final String id;           // "abc-123-pedido-maria"
    private final String customerName; // "María García"
    private final String product;      // "Hamburguesa Doble"
    private final int    quantity;     // 1
    private final String address;      // "Calle 100 #15-30, Bogotá"
    // NO tiene @Entity, NO tiene @Column, NO importa nada de Hibernate

    // LÍNEAS 51-52: campos MUTABLES — solo cambian via métodos de negocio
    private String driverId;   // null al inicio, "D-001" después de asignar
    private String driverName; // null al inicio, "Carlos Mendoza" después de asignar

    // LÍNEA 53: el estado actual — patrón State
    OrderState estadoActual;   // empieza como EstadoCreado

    // LÍNEA 90: constructor — validaciones Fail-Fast
    public Pedido(String id, String customerName, String product, int quantity, String address) {
        Objects.requireNonNull(id, "id no puede ser nulo");
        if (id.isBlank())          throw new IllegalArgumentException("id no puede estar vacío");
        if (quantity <= 0)         throw new IllegalArgumentException("quantity debe ser > 0");
        // ...más validaciones...

        this.estadoActual = new EstadoCreado(); // siempre nace en CREADO
    }

    // NO HAY setters públicos indiscriminados como setStatus("ENTREGADO")
    // El estado solo cambia a través de métodos semánticos:
    public void asignarRepartidor(String dId, String dName) { estadoActual.asignarRepartidor(this, dId, dName); }
    public void iniciarEntrega()   { estadoActual.iniciarEntrega(this);   }
    public void registrarLlegada() { estadoActual.registrarLlegada(this); }
    public void completarEntrega() { estadoActual.completarEntrega(this); }
}
```

**Archivo:** `PedidoEntity.java` (la infraestructura — solo para JPA)

```java
// Este es el pedido que usa JPA/Hibernate para hablar con PostgreSQL
// El dominio (Pedido.java) NO sabe que esta clase existe

@Entity                    // LÍNEA 16: le dice a Hibernate "esta clase mapea una tabla"
@Table(name = "orders")    // LÍNEA 17: mapea la tabla "orders" de PostgreSQL
public class PedidoEntity {

    @Id                                          // LÍNEA 20: clave primaria
    @Column(name = "id", length = 36)            // LÍNEA 21: columna "id" en la tabla
    private String id;                           // "abc-123-pedido-maria"

    @Column(name = "customer_name", length = 150) // LÍNEA 24: columna "customer_name"
    private String customerName;                  // "María García"

    @Column(name = "status", length = 30)         // LÍNEA 36: columna "status"
    private String status;                        // "CREADO", "ASIGNADO", etc.

    // LÍNEA 65: setters SOLO para los campos que cambian
    public void setStatus(String status)    { this.status   = status; }
    // "ASIGNADO", "EN_CAMINO", "LLEGANDO", "ENTREGADO"

    public void setDriverId(String dId)     { this.driverId = dId; }
    // "D-001" cuando Carlos es asignado

    public void setUpdatedAt(Instant upAt)  { this.updatedAt = upAt; }
    // NO hay setCustomerName → el nombre de María nunca cambia
}
// La conversión Pedido ↔ PedidoEntity la hace JpaOrderRepositoryAdapter
// El dominio nunca ve @Entity. La BD nunca ve Pedido.java.
```

---

### 🖥️ Muestra 1 — Consultas a PostgreSQL (ejecuta después del demo)

```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db \
  -c "SELECT id, customer_name, status, driver_id FROM orders;"
```

**Salida esperada:**
```
         id (truncado)       | customer_name  |  status   | driver_id
-----------------------------+----------------+-----------+-----------
 deca59e7-8b5d-...           | Maria Garcia   | ENTREGADO | D-001
```

> "El pedido de María en PostgreSQL. `id` = UUID que generó `order_api`.
> `driver_id` = D-001 → Carlos Mendoza, primero en el RoundRobin.
> `status` = ENTREGADO → el `dashboard_aggregator` lo actualizó 4 veces."

```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db \
  -c "SELECT event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"
```

**Salida esperada:**
```
   event_type    |     event_timestamp
-----------------+------------------------------
 ORDER_CREATED   | 2026-11-12 19:57:23.977+00
 DRIVER_ASSIGNED | 2026-11-12 19:57:26.997+00
 DELIVERY_STARTED| 2026-11-12 19:57:32.492+00
 DRIVER_ARRIVING | 2026-11-12 19:57:40.496+00
 ORDER_DELIVERED | 2026-11-12 19:57:45.498+00
```

> "5 filas. 22 segundos desde que María hizo click hasta que recibió la hamburguesa.
> Este log es inmutable — Event Sourcing y auditoría completa."

---

### 🖥️ Muestra 2 — Dockerfile multietapa

**Archivo:** `order_processor/Dockerfile`

```dockerfile
# ═══ STAGE 1: BUILD ═══
# Esta imagen NUNCA llega a producción — solo sirve para compilar
FROM maven:3.9.6-eclipse-temurin-17 AS builder
# maven = herramienta de compilación, eclipse-temurin-17 = JDK Java 17
# "AS builder" = le pone nombre a este stage para referenciarlo en el stage 2

WORKDIR /workspace   # directorio de trabajo dentro del contenedor

# TRUCO DE CACHÉ:
# Copiamos SOLO los archivos pom.xml ANTES del código fuente
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY order_processor/pom.xml      order_processor/pom.xml

# Descarga las dependencias (librerías de internet)
RUN mvn dependency:go-offline -pl common,order_processor -am -q
# Docker cachea esta capa. Si el código fuente cambia pero los pom.xml no,
# la próxima vez que se construya la imagen esta capa se salta —
# Maven no re-descarga internet.

# AHORA sí copiamos el código fuente (invalida caché al cambiar código)
COPY common/src         common/src
COPY order_processor/src order_processor/src

# Compila y empaqueta en un archivo .jar
RUN mvn clean package -pl common,order_processor -am -DskipTests -q
# -DskipTests = no correr pruebas en el build (se corren por separado en CI)
# El resultado: order_processor/target/order_processor-1.0.0.jar

# ═══ STAGE 2: RUNTIME ═══
# Esta es la imagen FINAL que corre en producción
# Solo tiene lo necesario para EJECUTAR — no para compilar
FROM eclipse-temurin:17-jre-alpine
# jre (no jdk) = solo el runtime de Java, sin compilador
# alpine = distribución Linux minimalista (~5MB vs ~100MB de Ubuntu)
# Resultado: imagen de ~150MB en lugar de ~500MB del stage 1

WORKDIR /app

# SEGURIDAD: crea un usuario no-root llamado "appuser"
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# -S = usuario del sistema (no puede hacer login)
USER appuser
# A partir de aquí, todo corre como "appuser", no como "root"
# Si un atacante explota el JAR, llega como appuser — no puede:
#   - instalar software (sudo bloqueado)
#   - leer /etc/shadow (contraseñas del sistema)
#   - escalar privilegios en el host

# Copia SOLO el JAR del stage anterior — nada más
COPY --from=builder /workspace/order_processor/target/order_processor-*.jar app.jar
# --from=builder = toma del stage 1 llamado "builder"
# No copia Maven, no copia JDK, no copia código fuente

# Configuración de la JVM para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# UseContainerSupport: JVM lee los límites de cgroups del contenedor
#   Sin esta flag: JVM ve RAM total del host (ej 16GB) y configura heap de 4GB
#   Con esta flag: JVM ve que el contenedor tiene 384MB, configura heap de 288MB
# MaxRAMPercentage=75.0: usa máximo 75% de los 384MB = 288MB para heap Java
# ExitOnOutOfMemoryError: si se acaba la memoria → proceso termina limpio
#   Docker lo reinicia automáticamente. Mejor que degradarse sin memoria.

EXPOSE 8081   # documenta que el servicio usa el puerto 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
# ejecuta: java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar app.jar
```

---

### 🖥️ Muestra 3 — docker stats

```bash
docker stats --no-stream
```

**Señala la columna `MEM USAGE / LIMIT`:**
```
NAME                        MEM USAGE  /  LIMIT
rappi_order_processor        45.2MiB  /  384MiB   ← kernel bloquea si pasa de 384
rappi_dashboard_aggregator   52.1MiB  /  384MiB
rappi_kafka                 210.3MiB  /  512MiB   ← Kafka necesita más (JVM grande)
rappi_postgres               38.5MiB  /  512MiB
```

> "La columna `LIMIT` es el techo impuesto por **cgroups** del kernel Linux.
> `order_processor` usa 45MB ahora y nunca puede pasar de 384MB.
> En docker-compose.yml escribimos `deploy.resources.limits: memory: 384M`
> y Docker le dice al kernel que cree un cgroup con ese límite."

---

### 🖥️ Muestra 4 — Pruebas unitarias

```bash
mvn test -pl common,order_processor
```

**Mientras corre, dices:**
> "Sin Docker, sin Kafka, sin PostgreSQL. Solo memoria RAM. Miran exactamente
> el mismo flujo del demo: el pedido de María, la asignación de Carlos,
> el ciclo completo de estados."

**Al ver los resultados, señala cada grupo:**

---

### 💻 El código de las pruebas — línea por línea

**Archivo:** `PedidoTest.java` — pruebas del dominio puro

```java
// Esta prueba verifica el ciclo de vida COMPLETO del pedido de María
@Test
@DisplayName("Ciclo completo: CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO")
void cicloDeVidaCompleto() {

    // ARRANGE: crear el pedido en estado inicial
    Pedido pedido = new Pedido("order-001", "María García", "Hamburguesa", 2, "Calle 100 #15-30");
    assertEquals("CREADO", pedido.getNombreEstado()); // ← verifica estado inicial

    // ACT & ASSERT: cada transición

    pedido.asignarRepartidor("D-001", "Carlos Mendoza"); // patrón State entra aquí
    assertEquals("ASIGNADO",        pedido.getNombreEstado()); // ← cambió a ASIGNADO
    assertEquals("D-001",           pedido.getDriverId());     // ← se guardó el driver
    assertEquals("Carlos Mendoza",  pedido.getDriverName());   // ← se guardó el nombre

    pedido.iniciarEntrega();         // Carlos salió del restaurante
    assertEquals("EN_CAMINO", pedido.getNombreEstado());

    pedido.registrarLlegada();       // Carlos está llegando
    assertEquals("LLEGANDO",  pedido.getNombreEstado());

    pedido.completarEntrega();       // Hamburguesa entregada
    assertEquals("ENTREGADO", pedido.getNombreEstado());
}

// Esta prueba verifica que el patrón State bloquea transiciones inválidas
@Test
@DisplayName("No debe iniciar entrega desde estado CREADO")
void noDebeIniciarEntregaDesdeCreado() {

    // ARRANGE: pedido en estado CREADO (sin repartidor asignado)
    Pedido pedido = new Pedido("order-001", "María García", "Hamburguesa", 2, "Calle 100 #15-30");

    // ACT & ASSERT: intentar iniciarEntrega sin haber asignado repartidor
    var ex = assertThrows(IllegalStateException.class, pedido::iniciarEntrega);
    // assertThrows VERIFICA que se lance la excepción — si NO se lanza, la prueba FALLA
    // pedido::iniciarEntrega = "ejecuta pedido.iniciarEntrega()"

    assertTrue(ex.getMessage().contains("CREADO"));
    // el mensaje de error debe mencionar "CREADO" para que sea descriptivo
}
```

**Archivo:** `OrderProcessorServiceTest.java` — pruebas con Mockito

```java
@ExtendWith(MockitoExtension.class) // activa Mockito en este test
class OrderProcessorServiceTest {

    // @Mock = crear un OBJETO FALSO que reemplaza la dependencia real
    @Mock
    private OrderRepositoryPort repositorio;
    // Este "repositorio" NO toca PostgreSQL — solo registra las llamadas que recibe

    @Mock
    private OrderEventPublisherPort publicador;
    // Este "publicador" NO toca Kafka — solo registra las llamadas

    @Mock
    private DriverAssignmentStrategy estrategiaAsignacion;
    // Esta "estrategia" NO asigna repartidores reales

    @InjectMocks
    private OrderProcessorService service;
    // Crea el OrderProcessorService REAL e inyecta los 3 mocks en su constructor
    // Es equivalente a: new OrderProcessorService(repositorio, publicador, estrategiaAsignacion)

    // ---- PRUEBA 1: verificar el orden exacto de las operaciones ----
    @Test
    @DisplayName("Debe ejecutar las operaciones en el orden correcto")
    void debeEjecutarEnOrdenCorrecto() {

        // ARRANGE: crear el comando de María
        OrderCreatedCommand comando = new OrderCreatedCommand(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "María García",          // customerName
            "Hamburguesa doble",     // product
            2,                       // quantity
            "Calle 100 #15-30",     // address
            Instant.now()
        );

        // ACT: ejecutar el flujo real del servicio
        service.procesarComando(comando);
        // El servicio usa los mocks — no conecta a Kafka ni PostgreSQL

        // ASSERT: verificar el ORDEN EXACTO de las llamadas
        var ordenVerificacion = inOrder(repositorio, publicador, estrategiaAsignacion);
        // inOrder = verifica que las llamadas ocurrieron en ESTE orden exacto

        ordenVerificacion.verify(repositorio).guardar(any());
        // ↑ "guardar fue llamado PRIMERO, con cualquier Pedido"

        ordenVerificacion.verify(publicador).publicarOrderCreated(any());
        // ↑ "publicarOrderCreated fue SEGUNDO"

        ordenVerificacion.verify(estrategiaAsignacion).asignar(any());
        // ↑ "asignar fue TERCERO"

        ordenVerificacion.verify(repositorio).actualizar(any());
        // ↑ "actualizar fue CUARTO"

        ordenVerificacion.verify(publicador).publicarDriverAssigned(any());
        // ↑ "publicarDriverAssigned fue QUINTO"
        // Si cualquier paso falta o está en otro orden → esta prueba FALLA
    }

    // ---- PRUEBA 2: si no hay repartidores, NO publicar DRIVER_ASSIGNED ----
    @Test
    @DisplayName("Si la estrategia falla, no debe publicar DRIVER_ASSIGNED")
    void siEstrategiaFallaNODebePublicarDriverAssigned() {

        // ARRANGE: configurar el mock para que FALLE cuando se llame asignar()
        doThrow(new IllegalStateException("Sin repartidores disponibles"))
            .when(estrategiaAsignacion).asignar(any());
        // "cuando se llame estrategiaAsignacion.asignar() con cualquier argumento,
        //  lanza IllegalStateException"

        OrderCreatedCommand comando = new OrderCreatedCommand(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(),
            "María García", "Hamburguesa doble", 2, "Calle 100 #15-30", Instant.now()
        );

        // ACT & ASSERT: el servicio lanza la excepción
        assertThrows(IllegalStateException.class, () -> service.procesarComando(comando));

        // ASSERT: verificar que publicarDriverAssigned NUNCA fue llamado
        verify(publicador, never()).publicarDriverAssigned(any());
        // "never()" = verificar que este método NO fue llamado
        // Si María no tiene repartidor disponible, NO se publica DRIVER_ASSIGNED en Kafka
    }
}
```

---

### ❓ Preguntas y respuestas

**"¿Qué son cgroups?"**
> "Control groups — mecanismo del kernel Linux para limitar recursos de procesos.
> En `docker-compose.yml` pusimos `memory: 384M` para `order_processor`. Docker
> le dice al kernel que cree un cgroup con límite de 384MB. El kernel garantiza
> que el proceso no pueda usar más. Con `-XX:+UseContainerSupport`, la JVM lee
> ese límite — sin esa flag vería los 16GB del host y configuraría un heap de 4GB
> para un contenedor de 384MB, causando un OOM-Kill inmediato."

**"¿Por qué `ddl-auto=validate` y no `create` o `update`?"**
> "Con `create`: Hibernate borra y recrea la tabla `orders` en cada arranque —
> el pedido de María del día anterior desaparece. Con `update`: Hibernate intenta
> alterar el esquema automáticamente — puede borrar columnas accidentalmente.
> Con `validate`: solo verifica que `PedidoEntity` coincide con la tabla creada
> por `init.sql`. El esquema está en Git, versionado, bajo control total."

**"¿Qué es `@Mock` exactamente?"**
> "Crea un objeto falso que reemplaza la dependencia real en la prueba.
> El `@Mock OrderRepositoryPort repositorio` no toca PostgreSQL — es una cáscara
> que solo registra qué métodos fueron llamados y con qué argumentos.
> `verify(repositorio).guardar(any())` pregunta: '¿se llamó el método guardar?'
> Si no se llamó, la prueba falla. Así verificamos el comportamiento sin infraestructura."

**"¿Por qué `any()` en el verify?"**
> "`any()` es un matcher de Mockito que significa 'con cualquier argumento'.
> En `verify(repositorio).guardar(any(Pedido.class))` verificamos que `guardar`
> fue llamado con algún objeto de tipo `Pedido` — no nos importa cuál exactamente.
> Si quisiéramos verificar el contenido exacto usaríamos `ArgumentCaptor`."

---

## Resumen rápido — frases clave para recordar

| Concepto | Con el ejemplo de María |
|---|---|
| Kafka vs RabbitMQ | Crear el pedido va por RabbitMQ (uno lo procesa). Los 5 cambios de estado van por Kafka (3 servicios los leen). |
| Clave de partición | El UUID "abc-123" garantiza que los 5 eventos de María van a la misma partición — en orden. |
| Work Queue | Si hay 3 order_processors, el pedido de María lo toma uno solo — no tres. |
| Topic Exchange | SMS cuando Carlos llega (`notify.client.sms`). Push cuando entrega (`notify.driver.push`). |
| `record` | Clase inmutable de Java 17. `OrderCreatedCommand` con datos de María no tiene setters. |
| Fail-Fast | Si María pide `quantity=0`, el constructor del record lanza excepción antes de llegar a RabbitMQ. |
| Patrón Strategy | Para asignar: hoy RoundRobin. Mañana por cercanía. `OrderProcessorService` no cambia. |
| DIP | `OrderProcessorService` llama a `OrderEventPublisherPort` (interfaz). No sabe que adentro hay Kafka. |
| Patrón State | `pedido.asignarRepartidor()` solo funciona en CREADO. En ENTREGADO lanza excepción. No hay if-else. |
| Modelo Rico | `pedido.asignarRepartidor("D-001","Carlos")`. No hay `pedido.setStatus("ASIGNADO")`. |
| ddl-auto=validate | Hibernate verifica que `PedidoEntity` coincide con `orders`. No borra los pedidos de María. |
| Multi-stage Docker | Stage 1: Maven compila. Stage 2: JRE Alpine ejecuta solo el JAR. Sin Maven en producción. |
| cgroups | `order_processor` máximo 384MB. La JVM lo sabe con `UseContainerSupport`. |
| Idempotencia | Si el aggregator reprocesa los 5 eventos de María, `event_id UNIQUE` bloquea los duplicados. |
| `@Mock` | Objeto falso que no toca Kafka/BD pero recuerda las llamadas. `verify()` las comprueba. |
| `inOrder` | Verifica que las llamadas ocurrieron en orden: guardar → publicar → asignar → actualizar. |

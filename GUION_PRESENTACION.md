# Guión de Presentación — Juliana
## Rappi-Stream | 15 minutos en total | 4 turnos

> **Cómo usar este documento:** Cada sección tiene:
> - 🗣️ Las palabras EXACTAS que dices (en negrita la frase clave)
> - 📦 El ejemplo real que ocurre cuando María García pide una Hamburguesa Doble
> - 🖥️ Qué abrir/mostrar/ejecutar ANTES de hablar
> - ❓ Respuestas a las preguntas que te van a hacer

---

## EL EJEMPLO QUE USAS EN TODA LA PRESENTACIÓN

> María García entra al dashboard en `http://localhost:8087`, hace click en **+ Nuevo Pedido**,
> elige **Hamburguesa Doble**, escribe su nombre y dirección, y hace click en **Pedir ahora**.
> En los próximos 23 segundos, el sistema procesa el pedido automáticamente y el dashboard
> se actualiza en tiempo real sin que ella recargue la página.

Ese recorrido completo es lo que explicas en cada turno.

---

## TURNO 2 — Arquitectura e infraestructura

### Prepara esto ANTES de hablar
Abre dos terminales: una para docker y otra lista.

---

### 🗣️ Lo que dices

> "El sistema usa **dos brokers de mensajería con propósitos distintos**.
> Cuando María pide su hamburguesa, el comando 'crear pedido' va por **RabbitMQ** — porque
> solo un servicio debe procesarlo. Una vez procesado, cada cambio de estado — CREADO,
> ASIGNADO, EN_CAMINO, LLEGANDO, ENTREGADO — se publica como evento inmutable en **Kafka**.
> Tres servicios distintos lo leen al mismo tiempo: el simulador del repartidor, el router
> de notificaciones, y el aggregator del dashboard. Cada uno con su propio grupo de consumidores
> e independientemente del otro."

---

### 📦 El ejemplo en el flujo real

```
María hace click "Pedir ahora"
        │
        ▼
  [RabbitMQ - new_orders_queue]
  "Crear pedido de Hamburguesa para María García"
  → Solo lo toma order_processor (Work Queue: uno solo)
        │
        ▼
  order_processor crea el pedido y publica en Kafka:

  Evento 1 → topic order_events, clave="abc-123"  → ORDER_CREATED
  Evento 2 → topic order_events, clave="abc-123"  → DRIVER_ASSIGNED (Carlos Mendoza, D-001)

  Tres servicios leen esos mismos eventos INDEPENDIENTEMENTE:
  ┌──────────────────────────────────────────────────────────┐
  │ driver_simulator  → "asignaron un repartidor, simulo ruta"│
  │ notification_router → "el repartidor llega, envío SMS"   │
  │ dashboard_aggregator → "actualizo el dashboard de María" │
  └──────────────────────────────────────────────────────────┘
```

---

### 🖥️ Muestra 1 — `docker compose ps`

Ejecuta en terminal:
```bash
docker compose ps
```

**Señala esto en la salida:**
- No hay ningún contenedor que diga `zookeeper`. Dices:
  *"Usamos Kafka en modo **KRaft** — sin ZooKeeper. El broker gestiona sus propios metadatos.
  Antes Kafka necesitaba ZooKeeper para funcionar, en versiones modernas ya no."*
- Señala `rappi_kafka_init` con estado `Exited (0)`:
  *"Este fue el init container que corrió una sola vez, creó el topic `order_events`
  con 3 particiones, y terminó exitosamente. Los microservicios esperaron a que terminara
  antes de arrancar."*

---

### 🖥️ Muestra 2 — Particiones de Kafka

Ejecuta:
```bash
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events
```

**La salida se ve así:**
```
Topic: order_events   PartitionCount: 3   ReplicationFactor: 1
  Partition: 0   Leader: 1   Replicas: 1
  Partition: 1   Leader: 1   Replicas: 1
  Partition: 2   Leader: 1   Replicas: 1
```

**Lo que dices señalando la pantalla:**
> "Existen **3 particiones**. La clave de partición es el `orderId` — el UUID del pedido de María.
> Kafka calcula: `hash('abc-123-pedido-maria') % 3 = 1`, y todos los eventos de ese pedido
> van siempre a la **partición 1**. Eso garantiza el orden cronológico estricto:
> CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO.
> Nunca puede llegar ENTREGADO antes que CREADO para el mismo pedido."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Por qué 3 particiones y no 1?"**
> "Con 1 partición, si María y Juan piden al mismo tiempo, los eventos de Juan esperan
> a que terminen los de María. Con 3 particiones, el pedido de María va a la partición 0,
> el de Juan a la partición 1, y se procesan en paralelo. Para un MVP universitario 3 es
> suficiente — en producción podrían ser 12 o 24."

**"¿Qué pasa si el dashboard_aggregator se reinicia mientras María espera su pedido?"**
> "Con `auto-offset-reset=earliest`, al reiniciarse lee todos los eventos desde el principio
> del topic y reconstruye el estado. Si el pedido de María ya tenía status ASIGNADO en Kafka,
> el aggregator lo vuelve a procesar y lo actualiza. La verificación de idempotencia en la BD
> — la columna `event_id UNIQUE` — evita insertar duplicados en la tabla `order_events`."

---

---

## TURNO 4 — RabbitMQ: Work Queue y Topic Exchange

### Prepara esto ANTES de hablar
Abre el browser en `http://localhost:15672` (usuario: `rappi_admin`, contraseña: `rappi_rmq_2024`).
Ve a **Exchanges** y luego a **Queues**.

---

### 🗣️ Lo que dices — Work Queue

> "Cuando María hace click en 'Pedir ahora', el `order_api` crea un `OrderCreatedCommand`
> con el UUID del pedido, el nombre de María, 'Hamburguesa Doble', cantidad 1 y la dirección.
> Lo publica en la cola `new_orders_queue`. Esto es el patrón **Work Queue**: si tuviéramos
> 3 instancias del `order_processor`, el pedido de María lo tomaría **uno solo** — no los tres.
> RabbitMQ hace round-robin automáticamente y marca el mensaje como procesado cuando el
> worker lo confirma. Esto evita que el mismo pedido se procese dos veces y María reciba
> dos hamburguesas cobradas."

---

### 📦 El ejemplo en código — lo que viaja por RabbitMQ

```java
// Esto es lo que order_api publica en new_orders_queue para el pedido de María:
OrderCreatedCommand comando = new OrderCreatedCommand(
    "cmd-7f3a...",            // commandId — UUID único de este comando
    "abc-123-pedido-maria",   // orderId   — UUID del pedido (generado aquí)
    "María García",           // customerName
    "Hamburguesa Doble",      // product
    1,                        // quantity
    "Calle 100 #15-30, Bogotá", // address
    Instant.now()             // issuedAt
);
// Si quantity fuera 0 o negativo, el constructor compacto lanza
// IllegalArgumentException inmediatamente — Fail-Fast.
// El objeto inválido nunca llega a RabbitMQ.

rabbitTemplate.convertAndSend("new_orders_queue", comando);
// Jackson serializa el record a JSON y lo envía al broker.
```

---

### 🗣️ Lo que dices — Topic Exchange

> "Cuando el repartidor Carlos Mendoza está llegando a la dirección de María, el `driver_simulator`
> publica en Kafka el evento `DRIVER_ARRIVING`. El `notification_router` lo lee y necesita
> mandar un SMS a María. Para eso usa el **Topic Exchange** llamado `notifications`.
> Publica con la routing key `notify.client.sms`. El exchange compara contra el patrón
> `notify.client.*` — el asterisco reemplaza exactamente una palabra — y enruta el mensaje
> a `client_sms_queue`. Cuando el pedido se entrega, publica con `notify.driver.push`
> y el exchange lo manda a `driver_push_queue` para notificarle a Carlos."

---

### 📦 El ejemplo en código — lo que viaja por el Topic Exchange

```java
// Cuando Carlos está llegando → SMS para María:
NotificationCommand comandoSMS = new NotificationCommand(
    "notif-uuid-001",
    "abc-123-pedido-maria",   // orderId
    "abc-123-pedido-maria",   // recipientId (el cliente del pedido)
    NotificationType.SMS,
    "Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos.",
    Instant.now()
);
rabbitTemplate.convertAndSend("notifications", "notify.client.sms", comandoSMS);
// exchange "notifications" ve la key "notify.client.sms"
// coincide con el patrón "notify.client.*"
// → lo manda a client_sms_queue
// → sms_worker-1 o sms_worker-2 lo toma (round-robin)

// Cuando el pedido se entrega → Push para Carlos:
NotificationCommand comandoPush = new NotificationCommand(
    "notif-uuid-002",
    "abc-123-pedido-maria",
    "D-001",                  // recipientId = driverId de Carlos
    NotificationType.PUSH,
    "¡Entrega completada! Pedido abc-123 entregado exitosamente. ¡Buen trabajo, Carlos Mendoza!",
    Instant.now()
);
rabbitTemplate.convertAndSend("notifications", "notify.driver.push", comandoPush);
// coincide con "notify.driver.*" → va a driver_push_queue
```

---

### 🗣️ Lo que dices — Réplicas y balanceo

> "Los workers tienen `deploy.replicas: 2` en el docker-compose, lo que significa
> **dos contenedores del mismo servicio corriendo al mismo tiempo**. Ambos consumen
> de la misma queue. Si María y Juan piden al mismo tiempo y los dos reciben SMS cuando
> el repartidor llega, `sms_worker-1` procesa el de María y `sms_worker-2` procesa el
> de Juan — en paralelo, sin que uno espere al otro. Esto es escalabilidad horizontal."

---

### 🖥️ Muestra 1 — Panel RabbitMQ

En `http://localhost:15672`:

1. Click en **Exchanges** → muestra el exchange `notifications` de tipo `topic`
2. Click en `notifications` → muestra los bindings:
   - `notify.client.*` → `client_sms_queue`  ← *"para SMS al cliente — María"*
   - `notify.driver.*` → `driver_push_queue` ← *"para Push al repartidor — Carlos"*
3. Dices: *"El asterisco es un wildcard que reemplaza exactamente una palabra.
   `notify.client.sms` coincide con `notify.client.*`. Si mañana agregamos email,
   `notify.client.email` también coincidiría sin cambiar nada del exchange."*

---

### 🖥️ Muestra 2 — Réplicas en docker compose ps

```bash
docker compose ps
```

**Señala en la salida:**
```
rappi-stream-sms_worker-1                Up   ...
rappi-stream-sms_worker-2                Up   ...
rappi-stream-push_notification_worker-1  Up   ...
rappi-stream-push_notification_worker-2  Up   ...
```

> "Dos instancias de cada worker. No tienen `container_name` fijo porque Docker no puede
> tener dos contenedores con el mismo nombre — les asigna sufijo automático -1 y -2."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Por qué no usar Kafka para las notificaciones también?"**
> "Kafka es ideal cuando múltiples servicios necesitan el mismo evento — como los 3 servicios
> que leen `order_events`. Las notificaciones van a un destinatario específico y deben
> procesarse exactamente una vez: el SMS de María lo manda un solo worker, no todos.
> RabbitMQ con Topic Exchange es el patrón correcto para ese enrutamiento selectivo."

**"¿Qué pasa si un sms_worker cae justo cuando iba a notificarle a María?"**
> "El mensaje queda en la queue de RabbitMQ — las queues son durables, sobreviven reinicios.
> Cuando el worker se recupera, toma el mensaje y manda el SMS. María lo recibe con un
> pequeño retraso pero sin perderlo."

---

---

## TURNO 6 — Patrón Strategy y DIP

### Prepara esto ANTES de hablar
Abre en el editor estos archivos en tabs (en este orden):
1. `order_processor/src/main/java/.../domain/strategy/DriverAssignmentStrategy.java`
2. `order_processor/src/main/java/.../domain/strategy/RoundRobinAssignmentStrategy.java`
3. `order_processor/src/main/java/.../application/OrderProcessorService.java`
4. `notification_router/src/main/java/.../strategy/NotificationStrategy.java`
5. `notification_router/src/main/java/.../strategy/DriverArrivingSmsStrategy.java`
6. `notification_router/src/main/java/.../strategy/OrderDeliveredPushStrategy.java`
7. `notification_router/src/main/java/.../service/NotificationRouterService.java`

---

### 🖥️ Muestra 1 — La interfaz Strategy (DriverAssignmentStrategy)

**Archivo:** `DriverAssignmentStrategy.java`

```java
public interface DriverAssignmentStrategy {
    void asignar(Pedido pedido);  // ← señala esta línea
}
```

**Lo que dices:**
> "Esta es la interfaz del patrón Strategy. Un solo método. Cuando llega el pedido de María,
> el `OrderProcessorService` llama a `estrategia.asignar(pedido)` sin saber si la
> implementación es RoundRobin, por cercanía, o por calificación.
> Para cambiar cómo se asignan los repartidores, solo se crea una nueva clase que
> implemente esta interfaz. El servicio **no cambia ni una línea**."

---

### 📦 El ejemplo — qué pasa cuando María pide su hamburguesa

```
Pedido de María llega al OrderProcessorService.

El servicio llama: estrategiaAsignacion.asignar(pedidoDeMaría)

Internamente, RoundRobinAssignmentStrategy hace:
  cursor = 0 (primer pedido del día)
  disponibles = [Carlos D-001, Laura D-002, Andrés D-003, Valentina D-004, Miguel D-005]
  índice = abs(0 % 5) = 0  →  selecciona Carlos Mendoza (D-001)
  pedido.asignarRepartidor("D-001", "Carlos Mendoza")

Si Juan pide después:
  cursor = 1
  índice = abs(1 % 5) = 1  →  selecciona Laura Rodríguez (D-002)

Si luego pide Ana:
  cursor = 2  →  Andrés Torres (D-003)
  ... y así rota circularmente.
```

---

### 🖥️ Muestra 2 — La implementación (RoundRobinAssignmentStrategy)

**Archivo:** `RoundRobinAssignmentStrategy.java`

**Señala el `AtomicInteger` (línea 32):**
```java
private final AtomicInteger cursor = new AtomicInteger(0);
```

**Lo que dices:**
> "El cursor es un `AtomicInteger` — una variable entera **thread-safe**.
> Si María y Juan hacen click en 'Pedir ahora' exactamente al mismo tiempo, dos hilos
> distintos ejecutan `cursor.getAndIncrement()` simultáneamente.
> `AtomicInteger` garantiza que uno obtiene el 0 y el otro el 1 — nunca los dos el 0.
> Sin esto, ambos pedidos se asignarían a Carlos y Laura se quedaría sin trabajo."

**Señala el `.filter` (línea 46):**
```java
List<Driver> disponibles = repartidores.stream()
        .filter(Driver::isAvailable)
        .toList();
```
> "Solo considera repartidores disponibles. Si Carlos no está disponible ese día,
> simplemente no aparece en la lista y el Round-Robin lo salta."

**Señala `pedido.asignarRepartidor` (línea 52):**
```java
pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
// En este caso: pedido.asignarRepartidor("D-001", "Carlos Mendoza")
```
> "Llama al método de negocio del pedido, que activa el **patrón State** internamente:
> el pedido está en `EstadoCreado`, que es el único estado desde donde se puede asignar
> repartidor. El pedido transiciona a `EstadoAsignado`."

---

### 🖥️ Muestra 3 — El servicio que NO sabe nada de Kafka ni JPA

**Archivo:** `OrderProcessorService.java`

**Señala las tres dependencias (líneas 47-50):**
```java
private final OrderRepositoryPort    repositorio;            // interfaz — no JpaRepository
private final OrderEventPublisherPort publicador;            // interfaz — no KafkaTemplate
private final DriverAssignmentStrategy estrategiaAsignacion; // interfaz — no RoundRobin
```

**Lo que dices:**
> "Este servicio tiene **tres dependencias — todas son interfaces**.
> No importa `KafkaTemplate`, no importa `JpaRepository`, no importa `RoundRobinAssignmentStrategy`.
> Solo conoce contratos abstractos. Spring inyecta las implementaciones concretas al arrancar."

**Señala el flujo del pedido de María (líneas 70-100):**
```java
// 1. Crear el pedido de María en dominio (Fail-Fast)
Pedido pedido = new Pedido(
    "abc-123-pedido-maria",
    "María García",
    "Hamburguesa Doble",
    1,
    "Calle 100 #15-30, Bogotá"
);  // Si cualquier campo es inválido → IllegalArgumentException aquí mismo

// 2. Guardar en PostgreSQL: INSERT INTO orders (id, customer_name, ...) VALUES (...)
repositorio.guardar(pedido);

// 3. Publicar en Kafka: order_events, clave="abc-123-pedido-maria", tipo=ORDER_CREATED
publicador.publicarOrderCreated(pedido);

// 3.5 Espera 3 segundos → el dashboard muestra "Buscando repartidor..."
Thread.sleep(3000);

// 4. Asignar Carlos Mendoza (RoundRobin) → pedido transiciona CREADO→ASIGNADO
estrategiaAsignacion.asignar(pedido);

// 5. UPDATE orders SET status='ASIGNADO', driver_id='D-001' WHERE id='abc-123'
repositorio.actualizar(pedido);

// 6. Publicar en Kafka: order_events, clave="abc-123", tipo=DRIVER_ASSIGNED
publicador.publicarDriverAssigned(pedido);
```

> "Paso a paso el procesamiento del pedido de María. Si mañana cambiamos Kafka por otro
> broker, solo se reemplaza la implementación de `OrderEventPublisherPort`.
> **Este servicio no cambia una línea.**"

**Señala el constructor (líneas 56-62):**
```java
public OrderProcessorService(OrderRepositoryPort repositorio,
                              OrderEventPublisherPort publicador,
                              DriverAssignmentStrategy estrategiaAsignacion) {
```
> "Inyección **estricta por constructor**. No hay `@Autowired` en campos.
> Las dependencias son `final` — no se pueden cambiar después de crear el objeto.
> Esto también hace que las pruebas sean triviales: se pasan mocks en el constructor."

---

### 🖥️ Muestra 4 — Strategy en notification_router

**Archivo:** `NotificationStrategy.java`

```java
public interface NotificationStrategy {
    boolean aplica(OrderEventType eventType);
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}
```
> "Dos métodos: `aplica` dice si esta estrategia es responsable del evento,
> `construir` arma el comando de notificación con el mensaje real."

**Archivo:** `DriverArrivingSmsStrategy.java`

```java
// Este método decide si esta estrategia actúa:
public boolean aplica(OrderEventType eventType) {
    return eventType == OrderEventType.DRIVER_ARRIVING;
    // Solo actúa cuando Carlos está llegando a casa de María
}
```

```java
// Este método construye el SMS que recibirá María:
String mensaje = String.format(
    "Tu repartidor %s está llegando. Tiempo estimado: %s.",
    payload.driverName(),       // "Carlos Mendoza"
    payload.estimatedArrival()  // "2 minutos"
);
// Resultado: "Tu repartidor Carlos Mendoza está llegando. Tiempo estimado: 2 minutos."
```

**Archivo:** `OrderDeliveredPushStrategy.java`

```java
public boolean aplica(OrderEventType eventType) {
    return eventType == OrderEventType.ORDER_DELIVERED;
    // Solo actúa cuando el pedido de María fue entregado
}
```

```java
// Push que recibe Carlos cuando entregó:
String mensaje = String.format(
    "¡Entrega completada! Pedido %s entregado exitosamente. ¡Buen trabajo, %s!",
    envelope.orderId(),      // "abc-123-pedido-maria"
    payload.driverName()     // "Carlos Mendoza"
);
```

---

### 🖥️ Muestra 5 — La magia de Spring inyectando la lista

**Archivo:** `NotificationRouterService.java`

**Señala el constructor (líneas 41-43):**
```java
public NotificationRouterService(List<NotificationStrategy> estrategias,
                                  RabbitTemplate rabbitTemplate) {
    this.estrategias = estrategias;
```

**Lo que dices:**
> "El constructor pide `List<NotificationStrategy>`. Spring detecta que hay dos clases
> `@Component` que implementan esa interfaz — `DriverArrivingSmsStrategy` y
> `OrderDeliveredPushStrategy` — y las inyecta automáticamente como lista.
> Si creamos `PedidoCreadoEmailStrategy implements NotificationStrategy @Component`,
> Spring la incluye sin que este servicio cambie."

**Señala el for loop (líneas 55-61):**
```java
for (NotificationStrategy estrategia : estrategias) {
    if (estrategia.aplica(envelope.eventType())) {
        // Cuando llega DRIVER_ARRIVING del pedido de María:
        // DriverArrivingSmsStrategy.aplica(DRIVER_ARRIVING) = true  ✓
        // OrderDeliveredPushStrategy.aplica(DRIVER_ARRIVING) = false ✗
        // → solo construye el SMS, no el Push

        Optional<NotificationCommand> comando = estrategia.construir(envelope);
        // construye: "Tu repartidor Carlos Mendoza está llegando..."

        comando.ifPresent(cmd -> {
            String routingKey = routingKeyPara(cmd.type());
            // cmd.type() = SMS → routingKey = "notify.client.sms"

            rabbitTemplate.convertAndSend("notifications", routingKey, cmd);
            // publica en exchange "notifications" con key "notify.client.sms"
            // → va a client_sms_queue → sms_worker-1 lo toma y loguea el SMS
        });
        return;
    }
}
```
> "Sin if-else sobre tipos de evento. Cada estrategia encapsula su propia condición y lógica."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Qué es DIP con un ejemplo del proyecto?"**
> "`OrderProcessorService` es un módulo de alto nivel — contiene lógica de negocio.
> `KafkaTemplate` es un módulo de bajo nivel — es infraestructura. Sin DIP,
> `OrderProcessorService` importaría `KafkaTemplate` directamente. Con DIP, importa
> `OrderEventPublisherPort` — una interfaz que el dominio define y la infraestructura
> implementa. Si el profesor pregunta qué significa 'invertir la dependencia': antes la
> flecha iba Dominio → Kafka. Ahora va Kafka → Dominio (Kafka implementa la interfaz del dominio)."

**"¿Puedes cambiar de RoundRobin a 'asignar al repartidor más cercano'?"**
> "Sí. Se crea `ClosestDriverStrategy implements DriverAssignmentStrategy`, se anota
> con `@Component`, y en `DomainConfig.java` se cambia el `@Bean` para instanciarla
> con la lógica de cercanía. `OrderProcessorService` no cambia porque solo llama
> a `estrategiaAsignacion.asignar(pedido)` — no sabe cuál implementación usa."

**"¿Qué es el patrón Strategy en una frase?"**
> "Encapsula un algoritmo en una clase separada para que sea intercambiable.
> El cliente depende de la interfaz, no de la implementación."

---

---

## TURNO 8 — PostgreSQL, JPA y Docker/Pruebas

### Prepara esto ANTES de hablar
Abre en el editor:
1. `order_processor/src/main/java/.../persistence/entity/PedidoEntity.java`
2. `order_processor/src/main/java/.../domain/model/Pedido.java`
3. `order_processor/Dockerfile`

Abre una terminal lista para ejecutar.

---

### 🗣️ Lo que dices — Las 3 tablas

> "PostgreSQL tiene **tres tablas**. `drivers` tiene los 5 repartidores precargados
> por `init.sql` — Carlos, Laura, Andrés, Valentina y Miguel. `orders` tiene una fila
> por cada pedido: cuando María pide, `order_processor` hace el INSERT con status CREADO.
> Luego el `dashboard_aggregator` va actualizando esa misma fila: ASIGNADO, EN_CAMINO,
> LLEGANDO, ENTREGADO. `order_events` tiene **5 filas** para el pedido de María
> — una por cada evento — nunca se modifica, solo se inserta. La columna `event_id`
> es UNIQUE: si el aggregator se reinicia y reprocesa los mismos eventos, no duplica filas."

---

### 📦 El ejemplo en la base de datos — pedido de María

**Tabla `orders` durante el recorrido de María:**
```sql
-- Momento 1: order_processor persiste el pedido recién creado
-- INSERT hecho por order_processor
id: 'abc-123-pedido-maria'  |  customer_name: 'María García'
product: 'Hamburguesa Doble'  |  quantity: 1
address: 'Calle 100 #15-30'  |  status: 'CREADO'  |  driver_id: NULL

-- Momento 2: dashboard_aggregator procesa DRIVER_ASSIGNED
-- UPDATE hecho por dashboard_aggregator
status: 'ASIGNADO'  |  driver_id: 'D-001'

-- Momento 3: dashboard_aggregator procesa DELIVERY_STARTED
status: 'EN_CAMINO'

-- Momento 4: dashboard_aggregator procesa DRIVER_ARRIVING
status: 'LLEGANDO'

-- Momento 5: dashboard_aggregator procesa ORDER_DELIVERED
status: 'ENTREGADO'
```

**Tabla `order_events` — el log de auditoría:**
```sql
-- 5 filas para el pedido de María — inmutables, nunca se modifican
event_id              | order_id    | event_type      | event_timestamp
'evt-001-uuid'        | 'abc-123'   | ORDER_CREATED   | 2026-11-12 19:57:23
'evt-002-uuid'        | 'abc-123'   | DRIVER_ASSIGNED | 2026-11-12 19:57:27
'evt-003-uuid'        | 'abc-123'   | DELIVERY_STARTED| 2026-11-12 19:57:32
'evt-004-uuid'        | 'abc-123'   | DRIVER_ARRIVING | 2026-11-12 19:57:40
'evt-005-uuid'        | 'abc-123'   | ORDER_DELIVERED | 2026-11-12 19:57:45
-- event_id es UNIQUE → si el aggregator reprocesa, falla el INSERT del duplicado
-- → se captura DataIntegrityViolationException → se ignora silenciosamente
```

---

### 🗣️ Lo que dices — Separación de capas

> "Tenemos dos clases que representan el pedido de María en capas distintas."

**Muestra `Pedido.java` y `PedidoEntity.java` juntos lado a lado.**

**En `Pedido.java` señala los campos `private final`:**
```java
// Dominio — POJO puro. Ni una sola importación de Spring ni JPA.
private final String id;           // "abc-123-pedido-maria"
private final String customerName; // "María García"
private final String product;      // "Hamburguesa Doble"
private final int    quantity;     // 1
private final String address;      // "Calle 100 #15-30, Bogotá"
```
> "Los campos son `final` — inmutables desde la creación. Para cambiar el estado,
> María no puede hacer `pedido.setStatus('ASIGNADO')`. Debe llamar a
> `pedido.asignarRepartidor('D-001', 'Carlos')`, que valida que el pedido esté en
> estado CREADO antes de permitir la transición. Eso es **modelo rico**."

**Cambia a `PedidoEntity.java`, señala las líneas 16-17:**
```java
@Entity                   // ← anotación JPA — no existe en Pedido.java
@Table(name = "orders")   // ← mapea la tabla orders de PostgreSQL
public class PedidoEntity {
    @Id                   // ← clave primaria
    @Column(name = "id", length = 36, nullable = false)
    private String id;    // mismo valor: "abc-123-pedido-maria"

    // Setters solo para los campos mutables:
    public void setStatus(String status)    { this.status = status; }   // "ASIGNADO", "EN_CAMINO"...
    public void setDriverId(String dId)     { this.driverId = dId; }    // "D-001"
    public void setUpdatedAt(Instant upAt)  { this.updatedAt = upAt; }
    // ↑ SOLO estos 3 setters — no setCustomerName, no setProduct
}
```
> "El `JpaOrderRepositoryAdapter` traduce: cuando el pedido de María pasa a ASIGNADO,
> busca la `PedidoEntity` por id, llama `entity.setStatus('ASIGNADO')` y guarda.
> **El dominio (`Pedido`) nunca importa `@Entity`.**"

---

### 🗣️ Lo que dices — `ddl-auto=validate`

> "En `application.properties` tenemos `spring.jpa.hibernate.ddl-auto=validate`.
> Hibernate no crea ni modifica las tablas. Solo verifica al arrancar que
> `PedidoEntity` tenga los mismos campos que la tabla `orders` creada por `init.sql`.
> Si alguien borra una columna de `init.sql` sin actualizar la entidad, el servicio
> falla al arrancar con un error descriptivo — antes de procesar el pedido de María.
> Con `ddl-auto=create` Hibernate borraría y recrearía la tabla en cada arranque
> — María perdería todos sus pedidos anteriores."

---

### 🖥️ Muestra 1 — Consulta a PostgreSQL

Ejecuta en terminal:
```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db \
  -c "SELECT id, customer_name, status, driver_id FROM orders;"
```

**Salida esperada (con el pedido de la demo):**
```
                  id                   | customer_name  |  status   | driver_id
---------------------------------------+----------------+-----------+-----------
 deca59e7-8b5d-4ac8-b466-4950cdda36aa  | Maria Garcia   | ENTREGADO | D-001
```

**Lo que dices:**
> "El pedido de María en la base de datos. `id` es el UUID que generó `order_api`.
> `driver_id` es `D-001` — Carlos Mendoza, el primero en el Round-Robin.
> `status` es `ENTREGADO` — el `dashboard_aggregator` lo actualizó 5 veces,
> la última con el evento `ORDER_DELIVERED`."

Luego ejecuta:
```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db \
  -c "SELECT event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"
```

**Salida esperada:**
```
   event_type    |       event_timestamp
-----------------+------------------------------
 ORDER_CREATED   | 2026-11-12 19:57:23.977+00
 DRIVER_ASSIGNED | 2026-11-12 19:57:26.997+00
 DELIVERY_STARTED| 2026-11-12 19:57:32.492+00
 DRIVER_ARRIVING | 2026-11-12 19:57:40.496+00
 ORDER_DELIVERED | 2026-11-12 19:57:45.498+00
```

> "5 filas — 22 segundos desde que María hizo click hasta que recibió su hamburguesa.
> Este log es inmutable. Si queremos saber cuándo exactamente Carlos fue asignado,
> está aquí para siempre. Eso es **Event Sourcing** y **auditoría**."

---

### 🖥️ Muestra 2 — Dockerfile multietapa

**Archivo:** `order_processor/Dockerfile`

**Señala Stage 1 (línea 11):**
```dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS builder
# Imagen grande: tiene Maven + JDK + todas las herramientas de compilación
# Solo se usa para compilar — nunca llega a producción
```

**Señala la caché de dependencias (líneas 20-27):**
```dockerfile
# Primero copiamos SOLO los pom.xml (sin código fuente)
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY order_processor/pom.xml      order_processor/pom.xml
# ...
RUN mvn dependency:go-offline -pl common,order_processor -am -q
# Docker cachea esta capa. Si María no cambió el pom.xml (no agregó dependencias),
# la próxima vez que se reconstruya la imagen esta capa se salta.
# Solo se re-ejecuta si cambia un pom.xml.
```

**Señala Stage 2:**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
# Imagen mínima: solo JRE Alpine. Sin Maven, sin JDK, sin código fuente.
# ~150MB en lugar de ~500MB.
# COPY --from=builder copia SOLO el JAR del stage anterior:
COPY --from=builder /workspace/order_processor/target/order_processor-*.jar app.jar
```

**Señala el usuario no-root:**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
# Si un atacante explota una vulnerabilidad en el JAR de order_processor,
# accede como "appuser" — no puede instalar software, no puede leer /etc/shadow,
# no puede escalar privilegios en el host.
```

**Señala los flags JVM:**
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
# UseContainerSupport: JVM lee que el contenedor tiene 384 MB, no los 16 GB del host.
# MaxRAMPercentage=75.0: usa máximo 288 MB para heap (75% de 384 MB).
# ExitOnOutOfMemoryError: si se acaba la memoria, el proceso termina limpiamente
#   y Docker lo reinicia — mejor que degradarse sin memoria.
```

---

### 🖥️ Muestra 3 — docker stats

```bash
docker stats --no-stream
```

**Señala la columna `MEM USAGE / LIMIT`:**
```
NAME                          MEM USAGE   /   LIMIT
rappi_order_processor          45.2MiB   /   384MiB   ← el kernel bloquea si pasa de 384
rappi_dashboard_aggregator     52.1MiB   /   384MiB
rappi_kafka                   210.3MiB   /   512MiB   ← Kafka necesita más RAM (JVM grande)
rappi_postgres                  38.5MiB  /   512MiB
```

> "La columna `LIMIT` es el techo impuesto por **cgroups** del kernel Linux.
> `order_processor` usa 45 MB ahora mismo y nunca puede pasar de 384 MB.
> Si intentara usar más, el kernel lo mata con OOM-Kill y Docker lo reinicia.
> Esos límites los configuramos en docker-compose.yml:
> `deploy.resources.limits: cpus: '0.50', memory: 384M`"

---

### 🖥️ Muestra 4 — Pruebas unitarias

Ejecuta:
```bash
mvn test -pl common,order_processor
```

**Mientras corre, dices:**
> "Estas pruebas verifican el comportamiento del sistema sin levantar Docker,
> sin Kafka, sin PostgreSQL. Solo memoria RAM. Miran exactamente lo mismo
> que hicimos en el demo: crear un pedido para María, asignar a Carlos,
> simular el ciclo completo."

**Al ver los resultados en verde, señala los grupos de pruebas:**
> - **`PedidoTest`**: *"Verifica el patrón State. Si el pedido de María está en CREADO,
>   `iniciarEntrega()` lanza `IllegalStateException`. Si está en ENTREGADO,
>   ningún método más funciona — es un estado terminal."*
> - **`RoundRobinAssignmentStrategyTest`**: *"Verifica la rotación: 1er pedido → Carlos,
>   2do → Laura, 3ro → Andrés, 4to vuelve a Carlos."*
> - **`OrderProcessorServiceTest`**: *"Usa Mockito. Verifica que al procesar el pedido
>   de María se llamaron exactamente: `guardar` una vez, `publicarOrderCreated` una vez,
>   `asignar` una vez, `actualizar` una vez, `publicarDriverAssigned` una vez,
>   en ese orden exacto."*

---

### 📦 El ejemplo — qué verifica la prueba con Mockito

```java
// OrderProcessorServiceTest.java — así se prueba sin Kafka ni BD:

@Mock OrderRepositoryPort   repositorio;  // objeto falso que no toca PostgreSQL
@Mock OrderEventPublisherPort publicador; // objeto falso que no toca Kafka
@Mock DriverAssignmentStrategy estrategia; // objeto falso que no asigna repartidores
@InjectMocks OrderProcessorService service; // el servicio REAL con los mocks inyectados

@Test
void procesarComandoDeMariaSigueLosPassosEnOrden() {
    // Arrange — preparar el comando de María
    OrderCreatedCommand comando = new OrderCreatedCommand(
        "cmd-001", "abc-123",
        "María García", "Hamburguesa Doble", 1,
        "Calle 100 #15-30, Bogotá", Instant.now()
    );

    // Act — ejecutar el flujo real
    service.procesarComando(comando);

    // Assert — verificar que se llamaron en el orden correcto:
    InOrder inOrder = inOrder(repositorio, publicador, estrategia);
    inOrder.verify(repositorio).guardar(any(Pedido.class));      // paso 2
    inOrder.verify(publicador).publicarOrderCreated(any());      // paso 3
    inOrder.verify(estrategia).asignar(any(Pedido.class));       // paso 4
    inOrder.verify(repositorio).actualizar(any(Pedido.class));   // paso 5
    inOrder.verify(publicador).publicarDriverAssigned(any());    // paso 6
    // Si cualquier paso falta o está en otro orden → la prueba FALLA
}
```

> "Sin Docker. Sin red. Sin broker. La prueba termina en milisegundos y verifica
> el comportamiento exacto del procesamiento del pedido de María."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Qué son cgroups?"**
> "Control groups — un mecanismo del **kernel Linux** para limitar recursos de procesos.
> En `docker-compose.yml` pusimos `memory: 384M` para `order_processor`. Docker le dice
> al kernel que cree un cgroup para ese contenedor con límite de 384 MB.
> El kernel garantiza que el proceso no pueda usar más.
> Con `-XX:+UseContainerSupport`, la JVM lee ese límite de cgroup — sin esa flag,
> la JVM vería los 16 GB del host y configuraría un heap de 4 GB para un contenedor
> que solo tiene 384 MB, causando un OOM-Kill inmediato."

**"¿Por qué `ddl-auto=validate` y no `create` o `update`?"**
> "Con `create`: cada vez que `order_processor` arranca, Hibernate borra la tabla
> `orders` y la recrea vacía. El pedido de María del día anterior desaparece.
> Con `update`: Hibernate intenta alterar el esquema automáticamente — puede
> borrar columnas accidentalmente. Con `validate`: solo verifica. El control
> del esquema está en `init.sql` versionado en Git — podemos rastrear cada cambio."

**"¿Qué es Mockito?"**
> "Un framework para crear objetos falsos que reemplazan dependencias reales en pruebas.
> `@Mock` crea un `KafkaTemplate` que no conecta a ningún broker pero recuerda
> qué métodos se llamaron. `verify(publicador).publicarOrderCreated(any())` confirma
> que el servicio publicó el evento de María en Kafka. Si no lo hizo, la prueba falla.
> Las pruebas no necesitan Docker ni red — corren en segundos."

---

## Resumen rápido — frases clave para recordar

| Concepto | Con el ejemplo de María |
|---|---|
| Kafka vs RabbitMQ | Crear el pedido va por RabbitMQ (uno lo procesa). Los cambios de estado van por Kafka (tres servicios los leen). |
| Clave de partición | El UUID del pedido de María garantiza que sus 5 eventos van siempre a la misma partición — en orden. |
| Work Queue | Si hay 3 order_processors, el pedido de María lo toma uno solo — no tres. |
| Topic Exchange | SMS cuando Carlos llega (`notify.client.sms`). Push cuando entrega (`notify.driver.push`). |
| Patrón Strategy | Para asignar repartidores: hoy RoundRobin. Mañana por cercanía. `OrderProcessorService` no cambia. |
| DIP | `OrderProcessorService` llama a `OrderEventPublisherPort` (interfaz). No sabe que adentro hay Kafka. |
| Modelo Rico | `pedido.asignarRepartidor()` valida que esté en CREADO. No hay `pedido.setStatus("ASIGNADO")`. |
| Fail-Fast | Si `quantity = 0` en el pedido de María → `IllegalArgumentException` en el constructor. No llega a BD. |
| ddl-auto=validate | Hibernate verifica que `PedidoEntity` coincide con la tabla `orders`. No borra los pedidos de María. |
| Multi-stage Docker | Stage 1: Maven compila el JAR. Stage 2: JRE Alpine ejecuta solo el JAR. Sin Maven en producción. |
| cgroups | `order_processor` tiene 384 MB máximo. La JVM lo sabe con `UseContainerSupport`. |
| Idempotencia | Si el aggregator reprocesa los 5 eventos de María, no duplica filas: `event_id UNIQUE` lo bloquea. |

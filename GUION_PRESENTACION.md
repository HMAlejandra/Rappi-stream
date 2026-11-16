# Guión de Presentación — Juliana
## Rappi-Stream | 15 minutos en total | 4 turnos

> **Cómo usar este documento:** Cada sección tiene:
> - 🗣️ Las palabras EXACTAS que dices (en negrita la frase clave)
> - 🖥️ Qué abrir/mostrar/ejecutar ANTES de hablar
> - ❓ Respuestas a las preguntas que te van a hacer

---

## TURNO 2 — Arquitectura e infraestructura

### Prepara esto ANTES de hablar
Abre dos terminales: una para docker y otra lista.

---

### 🗣️ Lo que dices

> "El sistema usa **dos brokers de mensajería con propósitos distintos**.
> Kafka es el System of Record — un **log inmutable** donde cada cambio de estado del pedido queda registrado para siempre. Los tres servicios que necesitan esa información — el simulador, el router de notificaciones y el aggregator del dashboard — cada uno lo lee de forma independiente con su propio grupo de consumidores.
> RabbitMQ lo usamos para dos cosas: recibir el comando inicial de creación de pedido, que solo debe procesar un servicio, y para distribuir las notificaciones a los workers correctos."

---

### 🖥️ Muestra 1 — `docker compose ps`

Ejecuta en terminal:
```bash
docker compose ps
```

**Señala esto en la salida:**
- No hay ningún contenedor que diga `zookeeper`. Dices: *"Estamos usando Kafka en modo KRaft — sin ZooKeeper. El broker gestiona sus propios metadatos."*
- Muestra que `rappi_kafka_init` aparece como `Exited (0)` — dices: *"Este fue el init container que creó el topic `order_events` y ya terminó exitosamente."*

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
> "Existen **3 particiones**. El motivo es la clave de partición: en todos los eventos usamos el `orderId` como clave. Kafka garantiza que todos los eventos del mismo pedido siempre van a la misma partición, lo que garantiza el **orden cronológico estricto**: CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Por qué 3 particiones?"**
> "Para permitir que hasta 3 pedidos se procesen en paralelo en diferentes particiones. Si todos fueran a una sola, se formaría una cola. Tres particiones es un buen balance para este MVP."

**"¿Qué pasa si el dashboard_aggregator se reinicia?"**
> "Lee desde el principio del topic gracias a `auto-offset-reset=earliest`. La verificación de idempotencia en la base de datos evita duplicar eventos."

**"¿Dónde está configurado `auto-offset-reset`?"**
> Archivo: `dashboard_aggregator/src/main/resources/application.properties`
> No está en las properties visibles pero está en el `KafkaConfig.java` del aggregator como configuración del consumidor.

---

---

## TURNO 4 — RabbitMQ: Work Queue y Topic Exchange

### Prepara esto ANTES de hablar
Abre el browser en `http://localhost:15672` (usuario: `rappi_admin`, contraseña: `rappi_rmq_2024`).
Ve a **Exchanges** y luego a **Queues**.

---

### 🗣️ Lo que dices — Work Queue

> "Cuando el browser crea un pedido, el `order_api` lo publica en la cola `new_orders_queue`. Esto es el patrón **Work Queue**: si tuviéramos 3 instancias del `order_processor`, cada pedido lo tomaría **uno solo** — no los tres. RabbitMQ hace round-robin automáticamente. Esto evita que el mismo pedido se procese dos veces."

---

### 🗣️ Lo que dices — Topic Exchange

> "Para las notificaciones usamos un **Topic Exchange** llamado `notifications`. El exchange enruta por **routing keys**. Cuando el repartidor está llegando, publicamos con la key `notify.client.sms` — el exchange la compara contra el patrón `notify.client.*` y la manda a `client_sms_queue`, donde están los `sms_worker`. Cuando se entrega el pedido, publicamos con `notify.driver.push` — el patrón `notify.driver.*` la manda a `driver_push_queue`, donde están los `push_notification_worker`."

---

### 🗣️ Lo que dices — Réplicas y balanceo

> "Los workers tienen `deploy.replicas: 2`, lo que significa **dos instancias del mismo contenedor**. Ambas consumen de la misma queue. RabbitMQ distribuye los mensajes en round-robin entre ellas. Si llegan 4 notificaciones SMS, cada worker procesa 2. Esto es escalabilidad horizontal — se escala sin cambiar código."

---

### 🖥️ Muestra 1 — Panel RabbitMQ

En `http://localhost:15672`:

1. Click en **Exchanges** → muestra el exchange `notifications` de tipo `topic`
2. Click en `notifications` → muestra los bindings:
   - `notify.client.*` → `client_sms_queue`
   - `notify.driver.*` → `driver_push_queue`
3. Dices: *"El asterisco es un wildcard que reemplaza exactamente una palabra. `notify.client.sms` coincide, `notify.client.email` también coincidiría si lo agregáramos."*

---

### 🖥️ Muestra 2 — Réplicas en docker compose ps

```bash
docker compose ps
```

**Señala en la salida:**
```
rappi-stream-sms_worker-1    ...
rappi-stream-sms_worker-2    ...
rappi-stream-push_notification_worker-1    ...
rappi-stream-push_notification_worker-2    ...
```

> "Dos instancias de cada worker. No tienen `container_name` fijo porque Docker no puede tener dos contenedores con el mismo nombre — les asigna sufijo automático."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Por qué no usar Kafka para las notificaciones también?"**
> "Kafka es ideal cuando múltiples servicios necesitan el mismo evento. Las notificaciones van a destinatarios específicos — exactamente una vez. RabbitMQ con Topic Exchange es el patrón correcto para enrutamiento selectivo."

**"¿Qué pasa si un sms_worker cae?"**
> "El mensaje queda en la queue de RabbitMQ. Cuando el worker se recupera, lo consume. Las queues son durables — sobreviven reinicios del broker."

---

---

## TURNO 6 — Patrón Strategy y DIP

### Prepara esto ANTES de hablar
Abre en el editor estos archivos en este orden (tenlos listos en tabs):
1. `order_processor/src/main/java/com/rappistream/orderprocessor/domain/strategy/DriverAssignmentStrategy.java`
2. `order_processor/src/main/java/com/rappistream/orderprocessor/domain/strategy/RoundRobinAssignmentStrategy.java`
3. `order_processor/src/main/java/com/rappistream/orderprocessor/application/OrderProcessorService.java`
4. `notification_router/src/main/java/com/rappistream/notificationrouter/strategy/NotificationStrategy.java`
5. `notification_router/src/main/java/com/rappistream/notificationrouter/strategy/DriverArrivingSmsStrategy.java`
6. `notification_router/src/main/java/com/rappistream/notificationrouter/strategy/OrderDeliveredPushStrategy.java`
7. `notification_router/src/main/java/com/rappistream/notificationrouter/service/NotificationRouterService.java`

---

### 🖥️ Muestra 1 — La interfaz Strategy (DriverAssignmentStrategy)

**Archivo:** `DriverAssignmentStrategy.java`

```java
public interface DriverAssignmentStrategy {
    void asignar(Pedido pedido);  // ← señala esta línea
}
```

**Lo que dices:**
> "Esta es la interfaz del patrón Strategy. Tiene **un solo método**. El `OrderProcessorService` depende de esta interfaz — no de ninguna implementación. Para cambiar la estrategia de asignación, solo se crea una nueva clase que implemente esta interfaz. El servicio **no cambia**."

---

### 🖥️ Muestra 2 — La implementación (RoundRobinAssignmentStrategy)

**Archivo:** `RoundRobinAssignmentStrategy.java`

**Línea 32** — señala el `AtomicInteger`:
```java
private final AtomicInteger cursor = new AtomicInteger(0);
```

**Lo que dices:**
> "El cursor es un `AtomicInteger` — una variable entera **thread-safe**. Si dos pedidos llegan simultáneamente en hilos diferentes, `getAndIncrement()` garantiza que cada hilo obtiene un número diferente sin condiciones de carrera. Sin esto, dos pedidos podrían asignarse al mismo repartidor."

**Línea 46** — señala el `.filter`:
```java
List<Driver> disponibles = repartidores.stream()
        .filter(Driver::isAvailable)
        .toList();
```
> "Primero filtra solo los disponibles, luego aplica el módulo para la rotación circular."

**Línea 52** — señala `pedido.asignarRepartidor`:
```java
pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
```
> "Llama al método de negocio del pedido, que activa el **patrón State** internamente — transiciona de CREADO a ASIGNADO."

---

### 🖥️ Muestra 3 — El servicio que NO sabe nada de Kafka ni JPA

**Archivo:** `OrderProcessorService.java`

**Líneas 47-50** — señala las tres dependencias:
```java
private final OrderRepositoryPort    repositorio;       // ← interfaz, no JpaRepository
private final OrderEventPublisherPort publicador;       // ← interfaz, no KafkaTemplate
private final DriverAssignmentStrategy estrategiaAsignacion; // ← interfaz, no RoundRobin
```

**Lo que dices:**
> "Este servicio tiene **tres dependencias — todas son interfaces**. No importa `KafkaTemplate`, no importa `JpaRepository`, no importa `RoundRobinAssignmentStrategy`. Solo conoce contratos abstractos. Spring inyecta las implementaciones concretas en tiempo de arranque. Si mañana cambiamos Kafka por otro broker, **este servicio no cambia una línea**."

**Líneas 56-62** — señala el constructor:
```java
public OrderProcessorService(OrderRepositoryPort repositorio,
                              OrderEventPublisherPort publicador,
                              DriverAssignmentStrategy estrategiaAsignacion) {
```
> "Inyección **estricta por constructor** — no hay `@Autowired` en campos. Esto es una buena práctica: las dependencias son inmutables y la clase es más fácil de probar."

---

### 🖥️ Muestra 4 — Strategy en notification_router

**Archivo:** `NotificationStrategy.java`

```java
public interface NotificationStrategy {
    boolean aplica(OrderEventType eventType);  // ← señala esto
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}
```
> "Dos métodos: `aplica` dice si esta estrategia es responsable del evento, `construir` arma el comando de notificación."

**Archivo:** `DriverArrivingSmsStrategy.java` — líneas 36-37:
```java
public boolean aplica(OrderEventType eventType) {
    return eventType == OrderEventType.DRIVER_ARRIVING;  // ← esta es su única responsabilidad
}
```

**Archivo:** `OrderDeliveredPushStrategy.java` — línea 37:
```java
return eventType == OrderEventType.ORDER_DELIVERED;
```

> "Cada estrategia sabe exactamente cuándo aplicar. `DriverArrivingSmsStrategy` solo actúa en `DRIVER_ARRIVING`. `OrderDeliveredPushStrategy` solo en `ORDER_DELIVERED`."

---

### 🖥️ Muestra 5 — La magia de Spring inyectando la lista

**Archivo:** `NotificationRouterService.java` — línea 38:
```java
private final List<NotificationStrategy> estrategias;
```

**Líneas 41-43** — el constructor:
```java
public NotificationRouterService(List<NotificationStrategy> estrategias,
                                  RabbitTemplate rabbitTemplate) {
    this.estrategias = estrategias;
```

**Lo que dices:**
> "Aquí está la parte más elegante. El constructor pide `List<NotificationStrategy>` — **Spring busca automáticamente todas las clases que implementen esa interfaz** en el contexto de la aplicación y las inyecta como lista. Tenemos dos estrategias ahora. Si creamos una tercera clase `@Component` con `implements NotificationStrategy`, Spring la incluye automáticamente. **El servicio no cambia ni una línea.**"

**Líneas 55-61** — el for loop:
```java
for (NotificationStrategy estrategia : estrategias) {
    if (estrategia.aplica(envelope.eventType())) {
        Optional<NotificationCommand> comando = estrategia.construir(envelope);
        // ...
    }
}
```
> "Itera, pregunta cuál aplica, construye el comando y lo publica. Sin if-else sobre tipos de evento — cada estrategia encapsula su propia lógica."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Qué es DIP?"**
> "Dependency Inversion Principle — los módulos de alto nivel, como el `OrderProcessorService`, no deben depender de módulos de bajo nivel como `KafkaTemplate`. Ambos deben depender de abstracciones — las interfaces `OrderEventPublisherPort` y `OrderRepositoryPort`. La dirección de la dependencia se invierte: la infraestructura depende del dominio, no al revés."

**"¿Puedes cambiar de RoundRobin a otra estrategia?"**
> "Sí. Se crea `ClosestDriverStrategy implements DriverAssignmentStrategy`, se anota con `@Component`, y en `DomainConfig.java` se cambia el `@Bean` para instanciarla. `OrderProcessorService` no cambia."

**"¿Qué es el patrón Strategy?"**
> "Define una familia de algoritmos, los encapsula en clases separadas y los hace intercambiables. El cliente — en este caso `OrderProcessorService` — depende de la interfaz, no de la implementación concreta."

---

---

## TURNO 8 — PostgreSQL, JPA y Docker/Pruebas

### Prepara esto ANTES de hablar
Abre en el editor:
1. `order_processor/src/main/java/com/rappistream/orderprocessor/infrastructure/persistence/entity/PedidoEntity.java`
2. `order_processor/src/main/java/com/rappistream/orderprocessor/domain/model/Pedido.java`
3. `order_processor/Dockerfile`

Abre una terminal lista para ejecutar.

---

### 🗣️ Lo que dices — Las 3 tablas

> "PostgreSQL tiene **tres tablas**. `drivers` es solo datos semilla — los 5 repartidores precargados. `orders` mantiene el **estado actual** de cada pedido: lo escribe el `order_processor` al crearlo y lo actualiza el `dashboard_aggregator` en cada evento. `order_events` es el **log de auditoría** — una fila por cada evento, nunca se modifica, solo se inserta. La columna `event_id` es `UNIQUE` para garantizar idempotencia."

---

### 🗣️ Lo que dices — Separación de capas

**Muestra `Pedido.java` y `PedidoEntity.java` juntos.**

> "Aquí está la separación de capas. `Pedido.java` es el modelo del dominio — un **POJO puro**, no importa ninguna anotación de Spring ni de JPA. No tiene `@Entity`, no tiene `@Column`."

Señala en `Pedido.java` los campos `private final`:
```java
private final String id;
private final String customerName;
private final String product;
// ...
```
> "Los campos son `final` — inmutables. El estado solo cambia a través de métodos de negocio como `asignarRepartidor()`. No hay setters públicos indiscriminados."

Cambia a `PedidoEntity.java`, señala las líneas 16-17:
```java
@Entity
@Table(name = "orders")
public class PedidoEntity {
```
> "Esta sí es la entidad JPA. Tiene `@Entity`, `@Column`, `@Id` — toda la infraestructura de persistencia. El adaptador `JpaOrderRepositoryAdapter` traduce entre `Pedido` y `PedidoEntity`. **El dominio nunca importa `@Entity`**."

---

### 🗣️ Lo que dices — `ddl-auto=validate`

> "En `application.properties` tenemos `spring.jpa.hibernate.ddl-auto=validate`. Esto significa que Hibernate **no crea ni modifica tablas**. Solo verifica al arrancar que las entidades coincidan con el esquema creado por `init.sql`. Esto es importante en producción: el esquema de la base de datos está bajo control explícito, no lo genera automáticamente un framework."

---

### 🖥️ Muestra 1 — Consulta a PostgreSQL

Ejecuta en terminal (después de crear uno o más pedidos en el demo):
```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT id, customer_name, status, driver_id FROM orders;"
```

**Salida esperada:**
```
                  id                  | customer_name | status    | driver_id
--------------------------------------+---------------+-----------+-----------
 deca59e7-8b5d-4ac8-b466-...          | Maria Garcia  | ENTREGADO | D-001
```

**Lo que dices:**
> "Podemos ver el estado final del pedido persistido en PostgreSQL. El `id` es un UUID generado por el `order_api`. El `driver_id` es `D-001` — el primer repartidor en el Round-Robin. El `status` es `ENTREGADO` porque el `dashboard_aggregator` lo actualizó con cada evento de Kafka."

También puedes mostrar el log de auditoría:
```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT order_id, event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"
```

> "5 filas — una por cada evento del pedido, en orden cronológico. Esto es el **Event Sourcing**: el log inmutable de todo lo que pasó."

---

### 🖥️ Muestra 2 — Dockerfile multietapa

**Archivo:** `order_processor/Dockerfile`

**Señala las líneas 11-12 (Stage 1):**
```dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS builder
```
> "Stage 1 usa Maven con JDK completo — para compilar."

**Señala las líneas 20-22 (caché de dependencias):**
```dockerfile
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
# ...
RUN mvn dependency:go-offline -pl common,order_processor -am -q
```
> "Copiamos **solo los pom.xml primero**, antes del código fuente. Docker cachea esta capa. Si solo cambia código fuente pero no dependencias, Maven no re-descarga todo internet."

**Señala Stage 2:**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
```
> "Stage 2 — imagen final. Solo JRE Alpine, sin Maven, sin JDK, sin código fuente. La imagen final pesa ~150MB en vez de ~500MB."

**Señala las líneas de seguridad:**
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```
> "El proceso Java corre como usuario no-root. Si el contenedor es comprometido, el atacante no tiene privilegios de root en el host del servidor."

**Señala los flags JVM:**
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```
> "`UseContainerSupport` hace que la JVM lea los límites de **cgroups** del contenedor. Sin esto, ve la RAM total del host — por ejemplo 16 GB — y configura su heap basándose en eso, aunque el contenedor solo tenga 384 MB. Con cgroups, usa máximo el 75% de los 384 MB que le asignamos."

---

### 🖥️ Muestra 3 — docker stats

```bash
docker stats --no-stream
```

**Señala la columna `MEM USAGE / LIMIT`:**
```
rappi_order_processor    45.2MiB / 384MiB
rappi_dashboard_aggregator  52.1MiB / 384MiB
rappi_kafka             210.3MiB / 512MiB
```

> "Columna `MEM LIMIT` — es el límite impuesto por cgroups. `order_processor` tiene 384 MB máximo. El kernel bloquea cualquier intento de usar más. `kafka` tiene 512 MB por ser más demandante."

---

### 🖥️ Muestra 4 — Pruebas unitarias

Ejecuta:
```bash
mvn test -pl common,order_processor
```

**Mientras corre, dices:**
> "Estas pruebas corren **sin Spring Boot, sin Kafka, sin PostgreSQL, sin Docker**. Solo memoria RAM. Van a terminar en segundos."

**Al ver los resultados en verde:**
> "29 pruebas en verde. Las de dominio verifican el patrón State — transiciones válidas e inválidas del pedido. Las de Mockito verifican que el `OrderProcessorService` llamó a los puertos en el orden correcto: `guardar → publicarOrderCreated → asignar → actualizar → publicarDriverAssigned`. Si alguno falta o está en otro orden, la prueba falla."

---

### ❓ Preguntas que te harán y cómo responder

**"¿Qué son cgroups?"**
> "Control groups — un mecanismo del **kernel Linux** para limitar, medir y aislar el uso de recursos de un grupo de procesos. Docker usa cgroups para aplicar los límites de `deploy.resources.limits` en el `docker-compose.yml`. El contenedor no puede exceder esos límites independientemente de lo que el proceso intente consumir."

**"¿Por qué `ddl-auto=validate` y no `create` o `update`?"**
> "Con `create` Hibernate borraría y recrearía las tablas cada vez — se perderían todos los datos. Con `update` modificaría el esquema automáticamente — peligroso en producción porque puede borrar columnas accidentalmente. Con `validate` solo comprueba — el control del esquema está en `init.sql`, versionado en Git."

**"¿Qué es Mockito?"**
> "Un framework de Java para crear 'dobles de prueba' — objetos falsos que reemplazan las dependencias reales. `@Mock` crea un `KafkaTemplate` falso que no conecta a nada. `verify()` confirma que el servicio lo llamó con los parámetros correctos. Las pruebas son instantáneas y no necesitan infraestructura."

**"¿Qué hace `@InjectMocks`?"**
> "Crea la instancia real del servicio — `OrderProcessorService` — e inyecta automáticamente todos los `@Mock` declarados en el test. Es equivalente a llamar `new OrderProcessorService(mockRepositorio, mockPublicador, mockEstrategia)`."

---

## Resumen rápido — frases clave para recordar

| Concepto | Frase de una línea |
|---|---|
| Kafka vs RabbitMQ | Kafka: múltiples consumidores leen el mismo evento. RabbitMQ: uno solo toma el mensaje. |
| Partición key | orderId como clave garantiza orden cronológico por pedido. |
| Work Queue | Múltiples workers compiten — cada mensaje lo procesa uno solo. |
| Topic Exchange | Enruta por patrones de routing key con wildcards. |
| Patrón Strategy | Algoritmo intercambiable. Cliente depende de interfaz, no de implementación. |
| DIP | La infraestructura depende del dominio. El dominio no depende de nadie. |
| Modelo Rico | Estado + comportamiento en la misma clase. No hay setters indiscriminados. |
| Fail-Fast | Validar en el constructor. El objeto no existe si los datos son inválidos. |
| ddl-auto=validate | Hibernate no toca el esquema. Solo verifica que coincida con init.sql. |
| Multi-stage Docker | Stage 1 compila. Stage 2 ejecuta. La imagen final no tiene Maven. |
| cgroups | Kernel Linux limita CPU y RAM del contenedor. JVM los respeta con UseContainerSupport. |
| Idempotencia | event_id UNIQUE en BD. El mismo evento puede llegar 2 veces sin duplicar filas. |

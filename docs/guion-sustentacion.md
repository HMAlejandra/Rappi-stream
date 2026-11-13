# Guión de Sustentación — Rappi-Stream

Preguntas más probables de los profesores organizadas por asignatura, con respuestas preparadas.

---

## Sistemas Distribuidos

### P1. ¿Por qué usaron Kafka para los eventos y RabbitMQ para los comandos? ¿No podrían haber usado solo uno?

Podríamos haber usado solo uno, pero los dos patrones de mensajería tienen requisitos distintos.

Los eventos del ciclo de vida del pedido necesitan persistencia duradera, capacidad de replay (reprocesar desde el inicio) y orden garantizado por partición. Kafka ofrece todo eso de forma nativa con su log inmutable particionado.

Los comandos y notificaciones necesitan routing por tipo (un SMS va a una cola, un Push va a otra), baja latencia y consumo destructivo (el mensaje se elimina al procesarlo). RabbitMQ con Topic Exchange hace esto de forma más natural.

Usar solo Kafka para notificaciones sería innecesariamente complejo: no necesitamos replay ni persistencia duradera para un SMS. Usar solo RabbitMQ para eventos perdería la capacidad de replay y el orden por partición. El diseño híbrido usa cada herramienta para lo que hace mejor.

### P2. ¿Qué garantiza Kafka KRaft que antes garantizaba ZooKeeper?

KRaft garantiza exactamente lo mismo que ZooKeeper en términos de funcionalidad: la elección del controller del clúster, el almacenamiento de metadatos de topics y particiones, y la gestión de la pertenencia de los brokers. La diferencia es la implementación: KRaft usa un protocolo de consenso Raft interno dentro del propio Kafka, eliminando la dependencia externa de ZooKeeper. Esto reduce la complejidad operacional (un proceso menos), consume menos memoria y es el futuro oficial de Kafka.

### P3. ¿Qué pasa si el order_processor cae después de hacer el INSERT en PostgreSQL pero antes de publicar en Kafka?

El pedido queda registrado en PostgreSQL pero sin su evento en Kafka. Los consumidores reactivos (`driver_simulator`, `notification_router`, `dashboard_aggregator`) no verán ese pedido porque solo escuchan Kafka. El estado queda inconsistente entre la base de datos y el log de eventos.

Para producción, la solución es el Patrón Transactional Outbox: escribir el evento en una tabla `outbox_events` dentro de la misma transacción de PostgreSQL, y un proceso separado (como Debezium) lee esa tabla y publica en Kafka. Esto garantiza publicación at-least-once. Documentamos esta limitación como parte del análisis crítico del proyecto, y es aceptable para el MVP universitario.

### P4. ¿Qué es Event Sourcing y cómo lo implementaron?

Event Sourcing es un patrón donde el estado actual no se almacena directamente, sino que se reconstruye procesando la secuencia completa de eventos inmutables que han ocurrido. Kafka es nuestro Event Store: cada hecho del ciclo de vida del pedido (creación, asignación, entrega iniciada, repartidor llegando, entrega completada) se registra como un evento en el topic `order_events`. El `dashboard_aggregator` reconstruye el estado de cada pedido procesando esa secuencia en orden y lo persiste en PostgreSQL para consultas rápidas.

La ventaja es la auditabilidad completa: el log de eventos es la fuente de verdad y se puede reconstruir cualquier estado histórico. Si el agregador pierde su estado, puede reprocesar todo el topic desde el inicio.

### P5. ¿Cómo funciona el consumidor stateful? ¿Qué pasa si reinicia?

El `dashboard_aggregator` es un consumidor stateful de Kafka con group ID `dashboard-aggregator-group`. Mantiene el estado consolidado de cada pedido a medida que procesa eventos. Está configurado con `auto.offset.reset=earliest`, lo que significa que al arrancar por primera vez (sin offset guardado) lee desde el inicio del topic.

Si reinicia, Kafka recuerda el último offset confirmado del grupo y retoma desde ahí. Si pierde su estado en PostgreSQL (por ejemplo, se borró la base de datos), al reprocesar desde el inicio reconstruye todo gracias a la idempotencia: cada evento tiene un `eventId` UUID único con una constraint UNIQUE en la tabla `order_events`. Los eventos duplicados se rechazan por la constraint, y el estado final queda correcto.

---

## Sistemas Operativos

### P1. ¿Qué son los cgroups y cómo los usaron?

Los cgroups (control groups) son una característica del kernel Linux que permite agrupar procesos y aplicarles límites de recursos: CPU, memoria, I/O. Docker crea un cgroup por contenedor. En el `docker-compose.yml` definimos límites con `deploy.resources.limits`:

- `cpus: '0.50'` — el contenedor puede usar máximo 50% de un núcleo de CPU.
- `memory: 384M` — límite duro de 384 MB de RAM.

Docker traduce esto en cuotas de cgroup del kernel. Si la JVM intenta alojar más de 384 MB, el OOM Killer del kernel termina el proceso.

### P2. ¿Qué diferencia hay entre `cpus: '0.50'` y tener un núcleo de 500 MHz?

`cpus: '0.50'` no significa 500 MHz. Significa que el contenedor puede usar el 50% del tiempo de CPU de un núcleo, sin importar la frecuencia de ese núcleo. Si el núcleo corre a 3 GHz, el contenedor puede usar 1.5 GHz-segundo por cada segundo de reloj (50% del tiempo a 3 GHz). Es una cuota de tiempo de CPU (CPU scheduling), no una limitación de frecuencia. El kernel Linux usa CFS (Completely Fair Scheduler) para aplicar esta cuota mediante `cpu.cfs_quota_us` y `cpu.cfs_period_us` del cgroup.

### P3. ¿Por qué un usuario no-root en los contenedores?

Por el principio de mínimo privilegio. Si un atacante logra ejecución de código dentro del contenedor (por ejemplo, vía una vulnerabilidad en una librería) y el proceso corre como root, el atacante tiene acceso root dentro del contenedor. Con un usuario no-root (`appuser`), el atacante solo tiene permisos mínimos: no puede modificar archivos del sistema, no puede instalar paquetes, no puede cambiar configuración de red. Esto reduce la superficie de ataque significativamente.

### P4. ¿Qué ventaja tiene el build multi-etapa en Docker?

La imagen de runtime solo incluye lo necesario para ejecutar la aplicación: el JRE (~80 MB en Alpine) y el JAR compilado. No incluye Maven (~100 MB) ni el JDK completo (~300 MB), que solo se necesitan durante el build. Esto reduce la imagen final de ~500 MB a ~120 MB, lo que acelera el download y despliegue. Además, reduce la superficie de ataque: la imagen de runtime no contiene herramientas de compilación (`javac`, `jshell`, `jstack`) que un atacante podría usar.

### P5. ¿Cómo saben que los límites de memoria se aplican correctamente?

Con `docker stats --no-stream`, que muestra el uso real de CPU y RAM de cada contenedor comparado con sus límites. Por ejemplo, si un contenedor muestra `MEM USAGE: 120MiB / 384MiB`, el límite de 384 MB está funcionando y el contenedor está usando 120 MB. Además, configuramos `-XX:+UseContainerSupport` para que la JVM lea los límites del cgroup en lugar de los del host, y `-XX:MaxRAMPercentage=75.0` para que el heap use 75% del límite del contenedor. Si la JVM excede el límite, `-XX:+ExitOnOutOfMemoryError` hace que termine limpiamente y Docker la reinicia con `restart: unless-stopped`.

---

## Programación Orientada a Objetos

### P1. Expliquen el Patrón State sin usar código. Muéstrenlo en el sistema.

El Patrón State permite que un objeto cambie su comportamiento cuando su estado interno cambia. En nuestro sistema, un pedido pasa por cinco estados: CREADO, ASIGNADO, EN_CAMINO, LLEGANDO y ENTREGADO.

En lugar de tener un `if estado == "CREADO"` para decidir si se puede asignar un repartidor, el pedido delega en su estado actual. Cada estado es un objeto que sabe qué transiciones son válidas para él. Por ejemplo, el estado CREADO sabe que `asignarRepartidor` es válido y que `iniciarEntrega` no lo es. El estado ENTREGADO sabe que ninguna transición es válida porque es terminal.

Cuando el estado CREADO recibe `asignarRepartidor`, aplica la asignación y cambia el estado del pedido a ASIGNADO. El pedido no decide qué hacer: le pregunta al estado actual. Esto elimina completamente los condicionales sobre el estado.

### P2. ¿Cómo evitaron los if-else para las transiciones de estado?

No hay ningún `if (estado == ...)` ni `switch (estado)` en el código. Cada método de negocio del `Pedido` (como `asignarRepartidor`, `iniciarEntrega`, `registrarLlegada`, `completarEntrega`) delega al estado actual:

```
pedido.asignarRepartidor() → estadoActual.asignarRepartidor(this, ...)
```

El estado actual (por ejemplo, `EstadoCreado`) implementa `asignarRepartidor` aplicando la asignación y cambiando el estado a `EstadoAsignado`. Si el estado actual fuera `EstadoEntregado`, su implementación de `asignarRepartidor` lanza `IllegalStateException` porque un pedido entregado no se puede reasignar.

El polimorfismo decide: la misma llamada a `asignarRepartidor` hace cosas distintas según el estado actual. No hay condicionales porque cada estado encapsula su propia lógica.

### P3. ¿Qué es la Inversión de Dependencias y dónde la aplicaron?

La Inversión de Dependencias dice que los módulos de alto nivel (dominio) no deben depender de los de bajo nivel (infraestructura). Ambos deben depender de abstracciones.

Lo aplicamos en `order_processor` con dos puertos:

- `OrderRepositoryPort` — interfaz del dominio para persistencia. La implementa `JpaOrderRepositoryAdapter` en la capa de infraestructura usando Spring Data JPA.
- `OrderEventPublisherPort` — interfaz del dominio para publicar eventos. La implementa `KafkaOrderEventPublisher` en la capa de infraestructura usando `KafkaTemplate`.

El `OrderProcessorService` depende de las interfaces, no de las implementaciones. Spring inyecta los adaptadores concretos en tiempo de arranque. Si cambiamos de PostgreSQL a MongoDB, solo creamos un `MongoOrderRepositoryAdapter` que implemente `OrderRepositoryPort`. El dominio no se toca.

El dominio nunca importa clases de JPA, Kafka ni Spring. Toda la dependencia fluye en una sola dirección: infraestructura → dominio, nunca al revés.

### P4. ¿Qué diferencia hay entre el modelo de dominio anémico y el rico? ¿Cuál usaron?

En el modelo anémico, la entidad es solo un contenedor de datos (getters y setters) y toda la lógica vive en servicios externos. En el modelo rico, la entidad encapsula tanto el estado como el comportamiento, y las mutaciones solo ocurren a través de métodos semánticos que validan invariantes.

Usamos el modelo rico. Nuestra entidad `Pedido` no tiene setters públicos. El estado y el `driverId` solo cambian a través de métodos de negocio como `asignarRepartidor` o `iniciarEntrega`, que delegan al Patrón State para validar si la transición es legal. No hay forma de cambiar el estado a "ENTREGADO" directamente con un setter; hay que seguir el flujo completo de transiciones.

La diferencia concreta: en un modelo anémico tendríamos `pedido.setEstado("ASIGNADO")` con un string mágico y un `if` en algún servicio validando que el estado anterior era "CREADO". En nuestro modelo rico, `pedido.asignarRepartidor(driverId, driverName)` delega al estado actual, que decide si es válido y hace la transición. El string mágico no existe y el `if` tampoco.

### P5. ¿Qué son los Java Records y por qué los usaron para los eventos?

Los Java Records son clases inmutables concisas introducidas en Java 16. El compilador genera automáticamente el constructor, getters, `equals`, `hashCode` y `toString`.

Los usamos para todos los contratos del módulo `common` (eventos, comandos, payloads, DTOs) por tres razones:

1. **Inmutabilidad:** los records son inmutables por diseño. Un evento, una vez publicado, no cambia. Esto es esencial para Event Sourcing donde el log de eventos es la fuente de verdad.

2. **Fail-Fast en el constructor compacto:** validamos los invariantes antes de que el objeto exista. Si `quantity` es cero o `commandId` es nulo, se lanza `IllegalArgumentException` y el objeto nunca se crea. No hay forma de tener un comando inválido circulando por el sistema.

3. **Comparación por valor:** dos records con los mismos campos son `equals`. Esto facilita los tests: comparamos el resultado esperado con el obtenido por valor, no por referencia.

---

## Preguntas transversales

### P1. ¿Cuántos tests tienen y qué cubren?

79 tests en total, todos en verde. Cubren:
- Validación Fail-Fast de los contratos (`common`): 15 tests.
- Validación HTTP del API REST (`order_api`): 9 tests.
- Dominio: Estado, Strategy y servicio de orquestación (`order_processor`): 29 tests.
- Ciclo de entrega y publicación de eventos (`driver_simulator`): 6 tests.
- Routing de notificaciones con Strategy (`notification_router`): 5 tests.
- Workers de SMS y Push (`sms_worker` + `push_notification_worker`): 4 tests.
- Agregador stateful con idempotencia (`dashboard_aggregator`): 8 tests.
- WebSocket y endpoint interno (`dashboard_backend`): 3 tests.

### P2. ¿Cómo escalaron los workers?

Los workers de SMS y Push tienen `deploy.replicas: 2` en el `docker-compose.yml`. RabbitMQ distribuye los mensajes en round-robin entre las réplicas (patrón Competing Consumers). Si una réplica cae, RabbitMQ redistribuye sus mensajes a la réplica restante. Se puede escalar dinámicamente con `docker compose up -d --scale sms_worker=3` sin reconfigurar nada.

### P3. ¿Qué pasa si dos eventos del mismo pedido llegan simultáneamente?

No pueden. Kafka garantiza orden dentro de una partición. Al usar `orderId` como clave de partición, todos los eventos de un mismo pedido van a la misma partición y se procesan secuencialmente. No hay concurrencia entre eventos del mismo pedido. Sí puede haber concurrencia entre eventos de pedidos distintos (que van a particiones distintas), pero cada pedido se procesa de forma aislada y ordenada.

### P4. ¿Quién hizo qué?

- **Juliana Rodríguez:** docker-compose.yml (Kafka KRaft), `driver_simulator`, `order_api`, infraestructura de `order_processor` (adaptadores JPA y Kafka, listener RabbitMQ), `notification_router` (Patrón Bridge + Strategy), `sms_worker`, `push_notification_worker`.
- **Helen Martínez:** dominio de `order_processor` (Patrón State, entidad Pedido, Strategy de asignación, puertos DIP), `dashboard_aggregator` (consumidor stateful con idempotencia), `dashboard_backend` (WebSocket + HTML), resource limits en Docker.

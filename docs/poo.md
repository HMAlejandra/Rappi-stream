# Programación Orientada a Objetos — Rappi-Stream

## Patrón State

### Concepto

El Patrón State permite que un objeto cambie su comportamiento cuando su estado interno cambia. En lugar de usar condicionales (`if estado == "CREADO"`) para decidir qué hacer, se delega en un objeto estado que encapsula las reglas de transición.

### Diagrama de transiciones

```
                  asignarRepartidor()
  ┌─────────┐  ───────────────────►  ┌────────────┐
  │ CREADO  │                        │ ASIGNADO   │
  └─────────┘                        └────────────┘
                                          │
                                   iniciarEntrega()
                                          ▼
                                    ┌────────────┐
                                    │ EN_CAMINO  │
                                    └────────────┘
                                          │
                                  registrarLlegada()
                                          ▼
                                    ┌────────────┐
                                    │ LLEGANDO   │
                                    └────────────┘
                                          │
                                  completarEntrega()
                                          ▼
                                    ┌────────────┐
                                    │ ENTREGADO  │  (terminal)
                                    └────────────┘
```

### Implementación

La interfaz `OrderState` define todas las transiciones posibles:

```java
public interface OrderState {
    void asignarRepartidor(Pedido pedido, String driverId, String driverName);
    void iniciarEntrega(Pedido pedido);
    void registrarLlegada(Pedido pedido);
    void completarEntrega(Pedido pedido);
    String nombre();
}
```

Cada estado concreto implementa solo las transiciones válidas para él. Por ejemplo, `EstadoCreado`:

```java
public class EstadoCreado implements OrderState {
    @Override
    public void asignarRepartidor(Pedido pedido, String driverId, String driverName) {
        pedido.aplicarAsignacion(driverId, driverName);
        pedido.setEstado(new EstadoAsignado());  // transición
    }

    @Override
    public void iniciarEntrega(Pedido pedido) {
        throw new IllegalStateException("No se puede iniciar entrega en estado CREADO");
    }
    // ...
}
```

El `Pedido` delega al estado actual sin saber qué estado es:

```java
public void asignarRepartidor(String driverId, String driverName) {
    estadoActual.asignarRepartidor(this, driverId, driverName);
}
```

### Cómo se evitan los if-else

No hay ningún `if (estado == CREADO)` ni `switch (estado)` en el código. Cada llamada a un método de negocio se delega al estado actual, que decide si la transición es válida o lanza una excepción. Si se añade un nuevo estado (por ejemplo, `CANCELADO`), solo se crea una nueva clase `EstadoCancelado` que implementa `OrderState` — sin tocar el resto del código.

## Patrón Strategy

### Concepto

El Patrón Strategy define una familia de algoritmos, los encapsula individualmente y los hace intercambiables. El cliente (que usa la estrategia) no necesita saber qué algoritmo concreto se está ejecutando.

### Strategy 1: Asignación de repartidores

```java
public interface DriverAssignmentStrategy {
    void asignar(Pedido pedido);
}
```

```java
public class RoundRobinAssignmentStrategy implements DriverAssignmentStrategy {
    private int indice = 0;

    @Override
    public void asignar(Pedido pedido) {
        List<Driver> disponibles = repositorio.obtenerRepartidoresDisponibles();
        Driver seleccionado = disponibles.get(indice % disponibles.size());
        indice++;
        pedido.asignarRepartidor(seleccionado.getId(), seleccionado.getName());
    }
}
```

Para cambiar la estrategia a "First Available" o "Nearest Driver", solo se crea una nueva implementación de `DriverAssignmentStrategy` y se inyecta. El `OrderProcessorService` no cambia.

### Strategy 2: Construcción de notificaciones

```java
public interface NotificationStrategy {
    boolean aplica(OrderEventType eventType);
    Optional<NotificationCommand> construir(OrderEventEnvelope envelope);
}
```

```java
@Component
public class DriverArrivingSmsStrategy implements NotificationStrategy {
    @Override
    public boolean aplica(OrderEventType eventType) {
        return eventType == OrderEventType.DRIVER_ARRIVING;
    }

    @Override
    public Optional<NotificationCommand> construir(OrderEventEnvelope envelope) {
        DriverArrivingPayload payload = objectMapper.readValue(envelope.payload(), ...);
        String mensaje = "Tu repartidor " + payload.driverName() + " está llegando.";
        return Optional.of(new NotificationCommand(..., NotificationType.SMS, mensaje, ...));
    }
}
```

`NotificationRouterService` recibe una lista de estrategias inyectadas por Spring y itera:

```java
for (NotificationStrategy estrategia : estrategias) {
    if (estrategia.aplica(envelope.eventType())) {
        Optional<NotificationCommand> cmd = estrategia.construir(envelope);
        cmd.ifPresent(c -> publicar(c));
        return;
    }
}
```

Añadir un nuevo tipo de notificación (por ejemplo, email al cliente cuando el pedido es entregado) solo requiere crear una nueva clase `OrderDeliveredEmailStrategy` con `@Component`. Spring la inyecta automáticamente en la lista. Esto respeta el principio Open/Closed.

## DIP — Inversión de Dependencias

### El problema clásico

Sin DIP, el dominio depende de detalles de infraestructura:

```
Pedido → JPA Repository → PostgreSQL   ❌ (dominio conoce JPA)
```

Si cambiamos de PostgreSQL a MongoDB, hay que modificar el dominio.

### La solución DIP

El dominio define interfaces (puertos) que la infraestructura implementa (adaptadores):

```
dominio: OrderRepositoryPort (interfaz)
                ▲
                │ implements
infraestructura: JpaOrderRepositoryAdapter → Spring Data JPA → PostgreSQL
```

```java
// En el dominio (no importa JPA, Spring ni PostgreSQL)
public interface OrderRepositoryPort {
    void guardar(Pedido pedido);
    void actualizar(Pedido pedido);
    List<Driver> obtenerRepartidoresDisponibles();
}

// En la infraestructura (implementa el puerto con JPA)
@Component
public class JpaOrderRepositoryAdapter implements OrderRepositoryPort {
    // usa SpringDataPedidoRepository y SpringDataDriverRepository
}
```

`OrderProcessorService` depende de `OrderRepositoryPort` (interfaz), no de `JpaOrderRepositoryAdapter` (implementación). Spring inyecta la implementación concreta en tiempo de arranque. Para cambiar de PostgreSQL a MongoDB, solo se crea un `MongoOrderRepositoryAdapter` que implemente `OrderRepositoryPort` — el dominio no se toca.

### Mismo patrón con Kafka

```java
// Puerto en el dominio
public interface OrderEventPublisherPort {
    void publicarOrderCreated(Pedido pedido);
    void publicarDriverAssigned(Pedido pedido);
}

// Adaptador en infraestructura
@Component
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {
    // usa KafkaTemplate
}
```

## Java Records para los contratos

### Qué son los Records

Los Java Records (introducidos en Java 14, estables desde Java 16) son una forma concisa de declarar clases inmutables que son portadores de datos. El compilador genera automáticamente: constructor, getters (sin `get`), `equals`, `hashCode` y `toString`.

```java
public record OrderCreatedCommand(
    String commandId,
    String orderId,
    String customerName,
    String product,
    int quantity,
    String address,
    Instant issuedAt
) { }
```

### Por qué Records para los eventos y comandos

1. **Inmutabilidad:** los records son inmutables por diseño. Todos los campos son `final`. No hay setters. Un evento, una vez publicado, nunca cambia — esto es esencial para Event Sourcing.

2. **Fail-Fast en el constructor compacto:** el constructor compacto valida los invariantes antes de que el objeto exista:

```java
public OrderCreatedCommand {
    Objects.requireNonNull(commandId, "commandId no puede ser nulo");
    if (commandId.isBlank()) {
        throw new IllegalArgumentException("commandId no puede estar vacío");
    }
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity debe ser mayor que cero");
    }
    // ...
}
```

Si los datos son inválidos, el objeto nunca se crea. No hay forma de tener un `OrderCreatedCommand` en estado inconsistente circulando por el sistema.

3. **Comparación por valor:** `equals` y `hashCode` se basan en el contenido, no en la referencia. Dos records con los mismos campos son iguales, lo que facilita testing:

```java
var cmd1 = new OrderCreatedCommand("id1", "order1", "María", "Pizza", 2, "Calle 1", now);
var cmd2 = new OrderCreatedCommand("id1", "order1", "María", "Pizza", 2, "Calle 1", now);
assert cmd1.equals(cmd2);  // true — comparación por valor
```

4. **Serialización JSON natural:** Jackson serializa/deserializa records sin configuración extra. Los nombres de los campos del JSON corresponden automáticamente a los componentes del record.

## Modelo de dominio rico vs. anémico

### Modelo anémico (lo que no hicimos)

En un modelo anémico, las entidades son solo contenedores de datos (getters y setters) sin lógica de negocio. Toda la lógica vive en servicios externos:

```java
// ANÉMICO — solo datos, sin comportamiento
public class PedidoAnemico {
    private String estado;
    private String driverId;
    // ... getters y setters
}

// La lógica vive en un servicio externo
public class PedidoService {
    public void asignarRepartidor(PedidoAnemico p, String driverId) {
        if (!p.getEstado().equals("CREADO")) {
            throw new IllegalStateException();
        }
        p.setDriverId(driverId);
        p.setEstado("ASIGNADO");  // string mágico
    }
}
```

Problemas: strings mágicos (`"CREADO"`), lógica dispersa en servicios, difícil de mantener, sin encapsulamiento.

### Modelo rico (lo que hicimos)

En el modelo rico, la entidad encapsula tanto el estado como el comportamiento. Las mutaciones solo ocurren a través de métodos de negocio que validan invariantes:

```java
// RICO — estado + comportamiento encapsulado
public class Pedido {
    private OrderState estadoActual;  // patrón State
    private String driverId;

    public void asignarRepartidor(String driverId, String driverName) {
        estadoActual.asignarRepartidor(this, driverId, driverName);
        // El estado actual decide si es válido
    }

    public String getNombreEstado() {
        return estadoActual.nombre();  // delegado al estado
    }
}
```

Diferencias concretas:
- **No hay setters públicos:** el `estado` y `driverId` solo cambian a través de métodos semánticos (`asignarRepartidor`, `iniciarEntrega`).
- **No hay strings mágicos:** los estados son objetos (`EstadoCreado`, `EstadoAsignado`) con su propio comportamiento.
- **Invariantes encapsulados:** `asignarRepartidor` delega al estado actual, que valida si la transición es legal. Si no lo es, lanza `IllegalStateException`. No hay forma de asignar un repartidor a un pedido ya entregado.
- **El dominio no conoce infraestructura:** `Pedido` no importa JPA, Kafka, Spring ni ningún framework. Es POJO puro.

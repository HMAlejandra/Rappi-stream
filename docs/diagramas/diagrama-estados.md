# Diagrama de Estados — Ciclo de Vida del Pedido

```mermaid
stateDiagram-v2
    [*] --> CREADO : POST /api/orders\nOrderCreatedCommand → RabbitMQ

    CREADO --> ASIGNADO : order_processor.asignarRepartidor()\nStrategy RoundRobin selecciona driver\nPersiste en PostgreSQL\nKafka → DRIVER_ASSIGNED

    ASIGNADO --> EN_CAMINO : driver_simulator.iniciarEntrega()\nKafka → DELIVERY_STARTED\n[delay: 5s simulado]

    EN_CAMINO --> LLEGANDO : driver_simulator.registrarLlegada()\nKafka → DRIVER_ARRIVING\nnotification_router → SMS al cliente

    LLEGANDO --> ENTREGADO : driver_simulator.completarEntrega()\nKafka → ORDER_DELIVERED\nnotification_router → Push al repartidor\nPostgreSQL actualizado

    ENTREGADO --> [*]

    note right of CREADO
        Validación Fail-Fast:
        • commandId, orderId no nulos/vacíos
        • customerName no vacío
        • quantity > 0
        • address no vacía
    end note

    note right of ASIGNADO
        Patrón State:
        EstadoCreado.asignarRepartidor()
        → Patrón Strategy:
        RoundRobinAssignmentStrategy
        (AtomicInteger thread-safe)
    end note

    note right of EN_CAMINO
        dashboard_aggregator:
        snapshot.aplicarDeliveryStarted()
        WebSocket broadcast → browser
    end note

    note right of LLEGANDO
        Idempotencia en aggregator:
        existsByEventId() antes de procesar
        UNIQUE constraint en order_events
    end note

    note right of ENTREGADO
        Estado terminal.
        Cualquier operación posterior
        lanza IllegalStateException.
        Patrón State sin if-else.
    end note
```

## Reglas de Transición — Implementación sin if-else

Cada estado concreto implementa `OrderState` y solo permite su transición válida:

```
EstadoCreado    → permite: asignarRepartidor()   rechaza: iniciarEntrega, registrarLlegada, completarEntrega
EstadoAsignado  → permite: iniciarEntrega()       rechaza: asignarRepartidor, registrarLlegada, completarEntrega
EstadoEnCamino  → permite: registrarLlegada()     rechaza: asignarRepartidor, iniciarEntrega, completarEntrega
EstadoLlegando  → permite: completarEntrega()     rechaza: todo lo demás
EstadoEntregado → rechaza: TODO (estado terminal)
```

La entidad `Pedido` **nunca** evalúa el estado actual con un `if` o `switch`.
Llama directamente `estadoActual.asignarRepartidor(this, driverId, driverName)` y el estado
concreto decide si lanza `IllegalStateException` o ejecuta la transición.

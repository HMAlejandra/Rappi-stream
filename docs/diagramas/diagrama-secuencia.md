# Diagrama de Secuencia — Flujo Completo End-to-End

```mermaid
sequenceDiagram
    actor C as Cliente HTTP
    participant OA as order_api<br/>:8080
    participant RMQ1 as RabbitMQ<br/>new_orders_queue
    participant OP as order_processor<br/>:8081
    participant PG as PostgreSQL<br/>:5432
    participant K as Kafka KRaft<br/>order_events
    participant DS as driver_simulator<br/>:8082
    participant NR as notification_router<br/>:8083
    participant RMQ2 as RabbitMQ<br/>notifications exchange
    participant SW as sms_worker<br/>:8084 (x2)
    participant PW as push_worker<br/>:8085 (x2)
    participant DA as dashboard_aggregator<br/>:8086
    participant DB as dashboard_backend<br/>:8087
    actor W as Browser<br/>dashboard.html

    %% === PASO 1: Recepción del pedido ===
    C->>+OA: POST /api/orders<br/>{customerName, product, qty, address}
    OA->>OA: Valida OrderCreatedCommand<br/>(record + Fail-Fast)
    OA->>RMQ1: publish(OrderCreatedCommand)
    OA-->>-C: 202 Accepted {orderId}

    %% === PASO 2: Procesamiento ===
    RMQ1->>+OP: consume(OrderCreatedCommand)
    OP->>OP: new Pedido() [Estado: CREADO]
    OP->>PG: INSERT orders (status=CREADO)
    OP->>K: produce(ORDER_CREATED, key=orderId)
    OP->>OP: RoundRobinStrategy.asignar()<br/>[Estado: ASIGNADO]
    OP->>PG: UPDATE orders (status=ASIGNADO, driver_id)
    OP->>K: produce(DRIVER_ASSIGNED, key=orderId)
    deactivate OP

    %% === PASO 3: Reacción paralela a DRIVER_ASSIGNED ===
    par Consumo paralelo — groupId independientes
        K->>+DS: consume(DRIVER_ASSIGNED)
        K->>+NR: consume(DRIVER_ASSIGNED)
        K->>+DA: consume(DRIVER_ASSIGNED)
    end

    DA->>DA: snapshot.aplicarDriverAssigned()
    DA->>PG: UPDATE orders (status=ASIGNADO)
    DA->>PG: INSERT order_events (idempotente)
    DA->>DB: POST /internal/update (OrderStatusDTO)
    DB->>W: WebSocket broadcast → ASIGNADO
    deactivate DA

    NR->>NR: Sin estrategia para DRIVER_ASSIGNED
    deactivate NR

    %% === PASO 4: Simulación del viaje ===
    Note over DS: sleep(5s) — simula salida
    DS->>K: produce(DELIVERY_STARTED, key=orderId)
    deactivate DS

    par
        K->>+DA: consume(DELIVERY_STARTED)
        K->>+NR: consume(DELIVERY_STARTED)
    end
    DA->>DA: snapshot.aplicarDeliveryStarted() → EN_CAMINO
    DA->>DB: POST /internal/update
    DB->>W: WebSocket broadcast → EN_CAMINO
    deactivate DA
    NR->>NR: Sin estrategia para DELIVERY_STARTED
    deactivate NR

    %% === PASO 5: Repartidor llegando ===
    Note over K: sleep(8s) — simula viaje
    K->>+DS: (driver_simulator produce DRIVER_ARRIVING)
    DS->>K: produce(DRIVER_ARRIVING, key=orderId)
    deactivate DS

    par
        K->>+DA: consume(DRIVER_ARRIVING)
        K->>+NR: consume(DRIVER_ARRIVING)
    end

    DA->>DA: snapshot.aplicarDriverArriving() → LLEGANDO
    DA->>DB: POST /internal/update
    DB->>W: WebSocket broadcast → LLEGANDO
    deactivate DA

    NR->>NR: DriverArrivingSmsStrategy.construir()
    NR->>RMQ2: publish(NotificationCommand, routing=notify.client.sms)
    deactivate NR
    RMQ2->>SW: round-robin → una de las 2 réplicas
    SW->>SW: log("📱 SMS ENVIADO — cliente notificado")

    %% === PASO 6: Entrega completada ===
    Note over K: sleep(5s) — simula entrega
    K->>+DS: (driver_simulator produce ORDER_DELIVERED)
    DS->>K: produce(ORDER_DELIVERED, key=orderId)
    deactivate DS

    par
        K->>+DA: consume(ORDER_DELIVERED)
        K->>+NR: consume(ORDER_DELIVERED)
    end

    DA->>DA: snapshot.aplicarOrderDelivered() → ENTREGADO
    DA->>PG: UPDATE orders (status=ENTREGADO)
    DA->>DB: POST /internal/update
    DB->>W: WebSocket broadcast → ENTREGADO ✅
    deactivate DA

    NR->>NR: OrderDeliveredPushStrategy.construir()
    NR->>RMQ2: publish(NotificationCommand, routing=notify.driver.push)
    deactivate NR
    RMQ2->>PW: round-robin → una de las 2 réplicas
    PW->>PW: log("🔔 PUSH ENVIADO — repartidor notificado")
```

## Garantías del sistema en este flujo

| Garantía | Mecanismo |
|----------|-----------|
| Orden de eventos por pedido | `order_id` como clave de partición en Kafka |
| Sin duplicados en PostgreSQL | `event_id` UNIQUE en `order_events` |
| Balanceo de carga en notificaciones | 2 réplicas de cada worker + RabbitMQ Work Queue |
| Resiliencia del dashboard | WebSocket reconecta automáticamente cada 5s |
| Estado reconstruible | `dashboard_aggregator` reprocesa desde `earliest` |

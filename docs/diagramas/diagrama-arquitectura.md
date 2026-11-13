# Diagrama de Arquitectura — Rappi-Stream

```mermaid
graph TB
    subgraph "Cliente"
        C["🌐 Cliente HTTP / Browser"]
    end

    subgraph "Capa de Entrada"
        OA["📦 order_api\nPOST /api/orders\n:8080"]
    end

    subgraph "RabbitMQ — Comandos"
        NOQ["Queue: new_orders_queue"]
        NEX["Exchange: notifications\n(topic)"]
        SMSQ["Queue: client_sms_queue\nnotify.client.*"]
        PUSHQ["Queue: driver_push_queue\nnotify.driver.*"]
    end

    subgraph "Procesamiento"
        OP["⚙️ order_processor\nState + Strategy + JPA\n:8081"]
    end

    subgraph "Kafka KRaft — order_events\n3 particiones · clave: order_id"
        K["🟠 Apache Kafka KRaft\nSin ZooKeeper"]
    end

    subgraph "Consumidores Kafka"
        DS["🚴 driver_simulator\ngroup: driver-simulator-group\n:8082"]
        NR["🔀 notification_router\nBridge · group: notification-router-group\n:8083"]
        DA["📊 dashboard_aggregator\nStateful · earliest\ngroup: dashboard-aggregator-group\n:8086"]
    end

    subgraph "Workers RabbitMQ"
        SW["📱 sms_worker\n2 réplicas · :8084"]
        PW["🔔 push_notification_worker\n2 réplicas · :8085"]
    end

    subgraph "Dashboard"
        DB["🖥️ dashboard_backend\nWebSocket STOMP\n:8087"]
        UI["🗂️ dashboard.html\nSockJS + STOMP"]
    end

    subgraph "PostgreSQL :5432"
        PG["🐘 rappi_stream_db\ntablas: orders · order_events · drivers"]
    end

    C -->|"POST /api/orders"| OA
    OA -->|"OrderCreatedCommand\n(JSON)"| NOQ
    OA -->|"202 Accepted {orderId}"| C
    NOQ -->|"consume"| OP
    OP -->|"INSERT orders"| PG
    OP -->|"ORDER_CREATED\nDRIVER_ASSIGNED"| K
    K -->|"DRIVER_ASSIGNED"| DS
    K -->|"todos los eventos"| NR
    K -->|"todos los eventos"| DA
    DS -->|"DELIVERY_STARTED\nDRIVER_ARRIVING\nORDER_DELIVERED"| K
    NR -->|"notify.client.sms\nnotify.driver.push"| NEX
    NEX -->|"notify.client.*"| SMSQ
    NEX -->|"notify.driver.*"| PUSHQ
    SMSQ -->|"round-robin"| SW
    PUSHQ -->|"round-robin"| PW
    DA -->|"UPDATE orders\nINSERT order_events"| PG
    DA -->|"POST /internal/update"| DB
    DB -->|"WebSocket broadcast\n/topic/orders"| UI
    UI <-->|"WebSocket STOMP"| C
```

## Notas de Arquitectura

### Patrón de flujo: Comando → Evento → Comando + Persistencia

1. **RabbitMQ como canal de comandos**: `order_api` publica `OrderCreatedCommand` en `new_orders_queue`. Es un comando porque representa una intención: "quiero crear este pedido". RabbitMQ es ideal aquí por su modelo Work Queue (garantía de entrega a exactamente un consumidor).

2. **Kafka como log de eventos inmutable**: una vez que `order_processor` acepta y procesa el comando, publica eventos inmutables en Kafka. Estos eventos son hechos ocurridos, no intenciones.

3. **Tres consumidores independientes del mismo log**: `driver_simulator`, `notification_router` y `dashboard_aggregator` tienen group IDs distintos, por lo que cada uno recibe una copia de cada evento. Kafka garantiza el orden dentro de cada partición (por `order_id`).

4. **RabbitMQ como canal de notificaciones**: `notification_router` traduce eventos Kafka en comandos de notificación y los enruta por tipo usando Topic Exchange.
```

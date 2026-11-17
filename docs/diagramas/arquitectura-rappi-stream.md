# Diagrama de Arquitectura — Rappi-Stream

## Flujo completo de mensajes

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (Browser)                                 │
│                         HTML + JS  /  WebSocket STOMP                           │
└──────────────────────────────────┬──────────────────────────────────────────────┘
                                   │ HTTP REST / WebSocket
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           dashboard_backend  :8085                              │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌────────────────────────┐ │
│  │ OrderApiProxy        │  │ SnapshotProxy         │  │ InternalUpdate         │ │
│  │ Controller           │  │ Controller            │  │ Controller             │ │
│  │ POST /api/orders ──► │  │ GET /snapshots ──►    │  │ POST /internal/update  │ │
│  │  → order_api:8080    │  │  → aggregator:8084    │  │  ◄─ aggregator push    │ │
│  └─────────────────────┘  └──────────────────────┘  └────────┬───────────────┘ │
│                                                               │ STOMP broadcast │
│                                                     WebSocketConfig             │
└───────────────────────────────────────────────────────────────────────────────-─┘
         │ HTTP                                                       ▲
         ▼                                                            │ HTTP POST
┌─────────────────────┐                               ┌──────────────────────────┐
│   order_api  :8080  │                               │  dashboard_aggregator    │
│                     │                               │          :8084           │
│  OrderController    │                               │                          │
│  POST /api/orders   │                               │  OrderEventsListener     │
│         │           │                               │  @KafkaListener          │
│         ▼           │                               │  order_events ◄──────────┼──┐
│  OrderPublisher     │                               │         │                │  │
│  Service            │                               │  OrderAggregator         │  │
│         │           │                               │  Service                 │  │
│         ▼           │                               │  (builds OrderSnapshot)  │  │
│  RabbitMqConfig ✦   │                               │         │                │  │
│  orders exchange    │                               │  DashboardBackend        │  │
└──────────┬──────────┘                               │  Client (HTTP push)      │  │
           │ AMQP                                     └──────────────────────────┘  │
           │ order_created_queue                                                     │
           ▼                                                                         │
┌─────────────────────────────────────────────────────────────────────────────┐     │
│                         order_processor  :8081                              │     │
│                                                                             │     │
│  OrderCreatedCommandListener  (@RabbitListener)                             │     │
│               │                                                             │     │
│               ▼                                                             │     │
│       OrderProcessorService  (application layer)                           │     │
│               │                                                             │     │
│       ┌───────┴────────────┐                                                │     │
│       │  Domain / State Machine (Patrón State)                              │     │
│       │  Pedido ──► EstadoCreado ──► EstadoAsignado ──► EstadoEnCamino     │     │
│       │            ──► EstadoLlegando ──► EstadoEntregado                  │     │
│       │                                                                     │     │
│       │  RoundRobinAssignmentStrategy (Patrón Strategy)                     │     │
│       └───────────────────┐                                                 │     │
│                           │                                                 │     │
│       JpaOrderRepository  │  KafkaOrderEventPublisher                      │     │
│       Adapter (PostgreSQL)│         │                                       │     │
└───────────────────────────┼─────────┼───────────────────────────────────────┘     │
                            │         │ Kafka                                        │
                     [DB]   │         │ topic: order_events                          │
               PostgreSQL◄──┘         └─────────────────────────────────────────────┘
                                                  │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
                 ┌────────────────────────┐           ┌───────────────────────────┐
                 │  driver_simulator :8082 │           │  notification_router :8083 │
                 │                        │           │                           │
                 │  DriverAssigned        │           │  OrderEventsListener      │
                 │  Listener              │           │  @KafkaListener           │
                 │  @KafkaListener        │           │  order_events             │
                 │                        │           │          │                │
                 │  DeliverySimulator     │           │  Notification             │
                 │  Service               │           │  RouterService            │
                 │  (sleeps, simulates    │           │  (Patrón Strategy)        │
                 │   delivery stages)     │           │          │                │
                 │          │             │           │  RabbitMqConfig ✦✦        │
                 │          ▼             │           │  notifications exchange   │
                 │  Kafka producer:       │           │  routing keys:            │
                 │  DELIVERY_STARTED      │           │  notify.client.sms  ──►  │
                 │  DRIVER_ARRIVING   ────┼───►       │  notify.driver.push ──►  │
                 │  ORDER_DELIVERED       │  order    └────────────┬──────────────┘
                 └────────────────────────┘  _events              │ AMQP
                                                       ┌──────────┴──────────┐
                                                       │                     │
                                                       ▼                     ▼
                                          ┌────────────────────┐  ┌──────────────────────┐
                                          │   sms_worker :8086  │  │ push_notification    │
                                          │                    │  │ worker :8087         │
                                          │  SmsNotification   │  │                      │
                                          │  Listener          │  │  PushNotification    │
                                          │  @RabbitListener   │  │  Listener            │
                                          │  client_sms_queue  │  │  @RabbitListener     │
                                          │                    │  │  driver_push_queue   │
                                          │  [logs SMS send]   │  │                      │
                                          └────────────────────┘  │  [logs push send]    │
                                                                   └──────────────────────┘
```

## Leyenda

| Símbolo | Significado |
|---------|-------------|
| `✦`     | **Propietario autoritativo** de la topología RabbitMQ (exchange + queues + bindings) |
| `✦✦`    | Propietario autoritativo del exchange `notifications` completo |
| `──►`   | Flujo de mensajes / llamada HTTP |
| `@KafkaListener` | Consumidor Kafka (Spring Kafka) |
| `@RabbitListener` | Consumidor RabbitMQ (Spring AMQP) |

## Brokers de mensajería

### Kafka — topic `order_events`
| Productor | Consumidores |
|-----------|--------------|
| `order_processor` (via `KafkaOrderEventPublisher`) | `driver_simulator`, `notification_router`, `dashboard_aggregator` |

### RabbitMQ — exchange `orders` (direct)
| Productor | Cola | Consumidor |
|-----------|------|------------|
| `order_api` | `order_created_queue` | `order_processor` |

### RabbitMQ — exchange `notifications` (topic) — **declarado por `notification_router`**
| Productor | Routing Key | Cola | Consumidor |
|-----------|-------------|------|------------|
| `notification_router` | `notify.client.*` | `client_sms_queue` | `sms_worker` |
| `notification_router` | `notify.driver.*` | `driver_push_queue` | `push_notification_worker` |

## Persistencia

| Servicio | Tecnología | Qué almacena |
|----------|-----------|---------------|
| `order_processor` | PostgreSQL (JPA) | Entidades `Pedido` y `Driver` |
| `dashboard_aggregator` | PostgreSQL (JPA) | `OrderEntity` y `OrderEventEntity` (log de eventos) |
```

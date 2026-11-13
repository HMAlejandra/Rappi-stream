# 🚀 Rappi-Stream — Plataforma de Logística en Tiempo Real

> Proyecto universitario interdisciplinario · Sistemas Distribuidos · Sistemas Operativos · POO
> Desarrollado por Juliana Rodríguez y Helen Martínez

## Descripción

Sistema de entrega de pedidos en tiempo real que integra Apache Kafka (modo KRaft), RabbitMQ y PostgreSQL con ocho microservicios Spring Boot. Demuestra arquitectura basada en eventos (Event Sourcing), mensajería asíncrona con dos brokers complementarios, patrones de diseño avanzados (State, Strategy, DIP, Bridge) y contenerización con límites de recursos.

## Arquitectura

```
                         ┌─────────────┐
                         │ PostgreSQL  │
                         │   16.2      │
                         └──────┬──────┘
                                │
  Cliente ──HTTP──► order_api ──RabbitMQ──► order_processor ──Kafka──┐
                     (8080)      (new_orders   (8081)               │
                                  _queue)                          │
                                                                   ▼
                                                          ┌───────────────┐
                                            ┌────────────│ Kafka KRaft  │
                                            │            │ (order_events)│
                                            │            └───────────────┘
                              ┌─────────────┼─────────────────┐
                              ▼             ▼                 ▼
                    driver_simulator  notification_router  dashboard_aggregator
                       (8082)            (8083)                (8086)
                          │                │                      │
                    Kafka (publica   RabbitMQ               PostgreSQL + HTTP
                    3 eventos)        (Topic Exchange)             │
                                       /        \                   ▼
                                      ▼          ▼          dashboard_backend
                               sms_worker   push_worker        (8087) ──► WebSocket ──► Browser
                              (8084, x2)    (8085, x2)
```

## Microservicios

| Servicio | Puerto | Descripción | RAM | CPU |
|----------|--------|-------------|-----|-----|
| order_api | 8080 | API REST — recibe pedidos vía `POST /api/orders` | 384M | 0.50 |
| order_processor | 8081 | Consume RabbitMQ, aplica dominio (State + Strategy), persiste en PG, publica en Kafka | 384M | 0.50 |
| driver_simulator | 8082 | Consume `DRIVER_ASSIGNED` de Kafka, simula el ciclo de entrega | 256M | 0.25 |
| notification_router | 8083 | Puente Kafka → RabbitMQ (Patrón Bridge + Strategy) | 256M | 0.25 |
| sms_worker | 8084 | Consume `client_sms_queue`, simula SMS (2 réplicas) | 256M | 0.25 |
| push_notification_worker | 8085 | Consume `driver_push_queue`, simula Push (2 réplicas) | 256M | 0.25 |
| dashboard_aggregator | 8086 | Consumidor stateful Kafka, reconstruye estado, persiste en PG | 384M | 0.50 |
| dashboard_backend | 8087 | Sirve dashboard HTML, WebSocket a navegadores | 384M | 0.50 |

## Infraestructura

| Servicio | Puerto | URL |
|----------|--------|-----|
| Kafka KRaft | 9092 | — (sin ZooKeeper) |
| RabbitMQ | 5672 / 15672 | http://localhost:15672 |
| PostgreSQL | 5432 | — |

## Inicio rápido

```bash
# 1. Clonar el repositorio
git clone https://github.com/HMAlejandra/Rappi-stream.git
cd Rappi-stream

# 2. Configurar variables de entorno
cp .env.example .env

# 3. Compilar todos los módulos (instala common en el repo local)
mvn clean install -DskipTests

# 4. Levantar toda la infraestructura y los microservicios
docker compose up -d

# 5. Verificar que todo esté corriendo
docker compose ps

# 6. Verificar límites de recursos
docker stats --no-stream

# 7. Crear un pedido de prueba
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "María García",
    "product": "Hamburguesa doble",
    "quantity": 2,
    "address": "Calle 100 #15-30, Bogotá"
  }'

# 8. Ver el dashboard en tiempo real
# Abrir en el navegador: http://localhost:8087
```

## Verificaciones

```bash
# Kafka sin ZooKeeper
docker compose ps    # no aparece ningún servicio "zookeeper"

# Topic order_events con 3 particiones
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events

# RabbitMQ: exchange notifications + colas
# Panel: http://localhost:15672 (rappi_admin / rappi_rmq_2024)

# Workers con réplicas
docker compose ps sms_worker push_notification_worker

# PostgreSQL con datos
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT * FROM orders;"
```

## Tecnologías

| Tecnología | Versión | Uso |
|-----------|---------|-----|
| Java | 17 | Lenguaje principal (Records, pattern matching) |
| Spring Boot | 3.2.5 | Framework de microservicios |
| Maven | 3.9 | Build multi-módulo |
| Apache Kafka | 7.6.0 (KRaft) | Log de eventos inmutable (Event Sourcing) |
| RabbitMQ | 3.13 | Comandos sincrónicos + notificaciones (Topic Exchange) |
| PostgreSQL | 16.2 | Persistencia relacional + idempotencia |
| JUnit 5 | — | Pruebas unitarias (79 tests) |
| Mockito | 5 | Mocks para pruebas de servicio |
| Docker | Compose v2 | Contenerización con límites cgroups |

## Patrones de diseño implementados

| Patrón | Dónde | Descripción |
|--------|-------|-------------|
| **State** | `order_processor` domain/state/ | Ciclo de vida del pedido: CREADO → ASIGNADO → EN_CAMINO → LLEGANDO → ENTREGADO. Sin if-else ni switch. |
| **Strategy** | `order_processor` strategy/ | Asignación de repartidores (RoundRobinAssignmentStrategy) |
| **Strategy** | `notification_router` strategy/ | Construcción de notificaciones según tipo de evento (DriverArrivingSmsStrategy, OrderDeliveredPushStrategy) |
| **DIP** | `order_processor` domain/port/ | Puertos abstractos (OrderRepositoryPort, OrderEventPublisherPort) implementados por adaptadores concretos |
| **Bridge** | `notification_router` | Puente entre dos brokers: consume Kafka, publica en RabbitMQ |
| **Event Sourcing** | Kafka + dashboard_aggregator | Kafka como System of Record inmutable; el agregador reconstruye estado procesando eventos |

## Justificación técnica: Kafka + RabbitMQ

**Kafka** se usa para el log de eventos inmutables (`order_events`). Cada hecho del sistema (pedido creado, repartidor asignado, entrega iniciada, repartidor llegando, pedido entregado) se registra como un evento en Kafka con `orderId` como clave de partición, garantizando orden estricto por pedido. Kafka actúa como Event Store: el `dashboard_aggregator` reconstruye el estado actual procesando la secuencia completa de eventos.

**RabbitMQ** se usa para dos cosas distintas:
1. **Comando inicial** (`new_orders_queue`): `order_api` publica el `OrderCreatedCommand` y `order_processor` lo consume. Es un comando puntual, no un evento histórico.
2. **Notificaciones** (Topic Exchange `notifications`): `notification_router` publica `NotificationCommand` con routing keys (`notify.client.sms`, `notify.driver.push`) que enrutan a colas dedicadas consumidas por los workers.

No se usa un solo broker porque los dos patrones de mensajería tienen requisitos diferentes: los eventos necesitan persistencia, replay y orden por partición (Kafka), mientras que los comandos y notificaciones necesitan routing por tipo y baja latencia (RabbitMQ).

## Evidencias de pruebas

```bash
mvn test
```

Resultado: **79 tests, 0 fallos, 0 errores, 0 omitidos**

| Módulo | Tests |
|--------|-------|
| common | 15 |
| order_api | 9 |
| order_processor | 29 |
| driver_simulator | 6 |
| notification_router | 5 |
| sms_worker | 2 |
| push_notification_worker | 2 |
| dashboard_aggregator | 8 |
| dashboard_backend | 3 |
| **Total** | **79** |

## Escalado de workers

Los workers de SMS y Push escalan horizontalmente con 2 réplicas cada uno. RabbitMQ distribuye los mensajes en round-robin entre las instancias (Work Queue pattern):

```bash
# Ver réplicas activas
docker compose ps sms_worker push_notification_worker

# Escalar manualmente a 3 réplicas
docker compose up -d --scale sms_worker=3 --scale push_notification_worker=3

# Ver balanceo de carga en logs
docker compose logs -f sms_worker
```

## Documentación técnica

| Documento | Contenido |
|-----------|-----------|
| [docs/arquitectura.md](docs/arquitectura.md) | Arquitectura, justificación híbrida Kafka+RabbitMQ, KRaft, atomicidad |
| [docs/sistemas-distribuidos.md](docs/sistemas-distribuidos.md) | Event Sourcing, particiones, consumidor stateful, idempotencia, escalabilidad |
| [docs/sistemas-operativos.md](docs/sistemas-operativos.md) | cgroups, Docker, JVM en contenedores, build multi-etapa, principio mínimo privilegio |
| [docs/poo.md](docs/poo.md) | State, Strategy, DIP, Records, modelo rico vs anémico |
| [docs/guion-sustentacion.md](docs/guion-sustentacion.md) | Preguntas y respuestas para la sustentación |

## Compilar sin Docker

```bash
mvn clean install -DskipTests
```

## Ejecutar pruebas

```bash
mvn test
```

## Autoras

- **Juliana Rodríguez** — Fases 3, 5, 7 (docker-compose Kafka KRaft, driver_simulator, order_api, infraestructura order_processor, notification_router, sms_worker, push_notification_worker)
- **Helen Martínez** — Fases 4, 6, 8 (dominio order_processor, dashboard_aggregator, dashboard_backend, resource limits)

---

*Desarrollado como proyecto final universitario — 2024*

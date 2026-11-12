# 🚀 Rappi-Stream — Plataforma de Logística en Tiempo Real

> Proyecto universitario interdisciplinario · Sistemas Distribuidos · Sistemas Operativos · POO

## Descripción

Sistema de entrega de pedidos en tiempo real que integra Apache Kafka KRaft, RabbitMQ, PostgreSQL y ocho microservicios Spring Boot para demostrar arquitecturas basadas en eventos, mensajería asíncrona y patrones de diseño avanzados.

## Arquitectura

```
order_api → RabbitMQ (new_orders_queue)
         → order_processor → PostgreSQL + Kafka (order_events)
              → driver_simulator    → Kafka
              → notification_router → RabbitMQ (notifications exchange)
                   → sms_worker
                   → push_notification_worker
              → dashboard_aggregator → PostgreSQL + dashboard_backend
                   → dashboard_backend → WebSocket → Browser
```

## Requisitos Previos

- Docker Desktop 24+ (con Docker Compose v2)
- Java 17 (para desarrollo local)
- Maven 3.9+ (para desarrollo local)
- 6 GB de RAM disponibles para Docker

## Inicio Rápido

```bash
# 1. Clonar el repositorio
git clone https://github.com/HMAlejandra/Rappi-stream.git
cd Rappi-stream

# 2. Configurar variables de entorno
cp .env.example .env

# 3. Levantar toda la infraestructura
docker compose up -d

# 4. Verificar que todo esté corriendo
docker compose ps

# 5. Crear un pedido de prueba
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "María García",
    "product": "Hamburguesa doble",
    "quantity": 2,
    "address": "Calle 100 #15-30, Bogotá"
  }'

# 6. Ver el dashboard
# Abrir en el navegador: http://localhost:8087
```

## Microservicios

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| order_api | 8080 | API REST — recibe pedidos |
| order_processor | 8081 | Procesa pedidos, asigna repartidores |
| driver_simulator | 8082 | Simula el recorrido de entrega |
| notification_router | 8083 | Puente Kafka → RabbitMQ |
| sms_worker | 8084 | Notificaciones SMS a clientes |
| push_notification_worker | 8085 | Notificaciones Push a repartidores |
| dashboard_aggregator | 8086 | Reconstruye estado desde Kafka |
| dashboard_backend | 8087 | Sirve el dashboard con WebSocket |

## Infraestructura

| Servicio | Puerto | URL |
|----------|--------|-----|
| Kafka KRaft | 9092 | — |
| RabbitMQ | 5672 / 15672 | http://localhost:15672 |
| PostgreSQL | 5432 | — |

## Compilar sin Docker

```bash
mvn clean install -DskipTests
```

## Ejecutar pruebas

```bash
mvn test
```

## Tecnologías

- **Java 17** · Spring Boot 3.2.5 · Maven 3.9
- **Apache Kafka** (modo KRaft — sin ZooKeeper)
- **RabbitMQ** 3.13 (Topic Exchange)
- **PostgreSQL** 16.2
- **JUnit 5** · Mockito 5
- **Docker** · Docker Compose v2

## Patrones Implementados

- **State**: Ciclo de vida del pedido (CREADO → ENTREGADO)
- **Strategy**: Asignación de repartidores
- **DIP**: Puertos e interfaces para Kafka, RabbitMQ y PostgreSQL
- **Event Sourcing**: Kafka como System of Record inmutable
- **Bridge**: notification_router como puente Kafka → RabbitMQ

---

*Desarrollado como proyecto final universitario — 2024*

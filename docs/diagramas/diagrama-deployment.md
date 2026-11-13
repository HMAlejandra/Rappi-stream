# Diagrama de Deployment — Docker Compose

```mermaid
graph TB
    subgraph "Host — Docker Desktop"

        subgraph "Red interna: rappi_net (bridge)"

            subgraph "Infraestructura"
                PG["🐘 postgres:16.2-alpine\ncpus: 0.50 | mem: 512M\n:5432"]
                RMQ["🐇 rabbitmq:3.13-management\ncpus: 0.50 | mem: 384M\n:5672 / :15672"]
                K["🟠 cp-kafka:7.6.0 (KRaft)\ncpus: 0.75 | mem: 512M\n:9092"]
                KINIT["kafka-init\ncrea order_events\n3 particiones"]
            end

            subgraph "Microservicios Spring Boot"
                OA["order_api\ncpus: 0.50 | mem: 384M\n:8080"]
                OP["order_processor\ncpus: 0.50 | mem: 384M\n:8081"]
                DS["driver_simulator\ncpus: 0.25 | mem: 256M\n:8082"]
                NR["notification_router\ncpus: 0.25 | mem: 256M\n:8083"]
                SW1["sms_worker replica-1\ncpus: 0.25 | mem: 256M"]
                SW2["sms_worker replica-2\ncpus: 0.25 | mem: 256M"]
                PW1["push_worker replica-1\ncpus: 0.25 | mem: 256M"]
                PW2["push_worker replica-2\ncpus: 0.25 | mem: 256M"]
                DA["dashboard_aggregator\ncpus: 0.50 | mem: 384M\n:8086"]
                DB["dashboard_backend\ncpus: 0.50 | mem: 384M\n:8087"]
            end

        end

        subgraph "Volúmenes persistentes"
            V1["postgres_data"]
            V2["rabbitmq_data"]
            V3["kafka_data"]
        end

    end

    subgraph "Puertos expuestos al host"
        H1["localhost:8080 → order_api"]
        H2["localhost:8087 → dashboard_backend"]
        H3["localhost:15672 → RabbitMQ UI"]
        H4["localhost:9092 → Kafka"]
        H5["localhost:5432 → PostgreSQL"]
    end

    PG --- V1
    RMQ --- V2
    K --- V3

    KINIT -->|"service_healthy"| K
    OA -->|"service_healthy"| RMQ
    OP -->|"service_healthy"| RMQ
    OP -->|"service_healthy"| K
    OP -->|"service_healthy"| PG
    OP -->|"service_completed"| KINIT
    DS -->|"service_healthy"| K
    NR -->|"service_healthy"| K
    NR -->|"service_healthy"| RMQ
    SW1 -->|"service_healthy"| RMQ
    SW2 -->|"service_healthy"| RMQ
    PW1 -->|"service_healthy"| RMQ
    PW2 -->|"service_healthy"| RMQ
    DA -->|"service_healthy"| K
    DA -->|"service_healthy"| PG
    DA -->|"service_healthy"| DB
    DB -->|"service_healthy"| PG
```

## Consumo total de recursos estimado

| Servicio | CPU máx | RAM máx |
|----------|---------|---------|
| postgres | 0.50 | 512M |
| rabbitmq | 0.50 | 384M |
| kafka | 0.75 | 512M |
| order_api | 0.50 | 384M |
| order_processor | 0.50 | 384M |
| driver_simulator | 0.25 | 256M |
| notification_router | 0.25 | 256M |
| sms_worker × 2 | 0.50 | 512M |
| push_worker × 2 | 0.50 | 512M |
| dashboard_aggregator | 0.50 | 384M |
| dashboard_backend | 0.50 | 384M |
| **TOTAL** | **~5.25 cores** | **~4.5 GB** |

> Se recomienda al menos **6 GB de RAM** asignados a Docker Desktop y un procesador de 4 núcleos o más.

## Comandos de verificación de recursos en vivo

```bash
# Ver todos los contenedores y sus límites
docker stats --no-stream

# Ver solo los workers escalados
docker compose ps sms_worker
docker compose ps push_notification_worker

# Verificar Kafka KRaft (sin ZooKeeper)
docker compose ps | grep -v zookeeper   # no debe aparecer ningún zookeeper

# Inspeccionar límites de un contenedor específico
docker inspect rappi_order_api --format '{{.HostConfig.Memory}} {{.HostConfig.NanoCpus}}'
```

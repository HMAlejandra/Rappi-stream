# Evidencias de Pruebas de Integración — Rappi-Stream

## Fecha: 2026-11-11

## 1. Kafka KRaft sin ZooKeeper ✅

**Verificación:** `docker compose ps` no muestra ningún servicio `zookeeper`.

```
NAME             SERVICE
rappi_kafka      kafka
rappi_postgres   postgres
rappi_rabbitmq   rabbitmq
```

Kafka arranca en modo KRaft (broker + controller en un solo nodo) sin ZooKeeper. Confirmado.

## 2. Topic order_events con 3 particiones ✅

**Comando:**
```bash
docker exec rappi_kafka kafka-topics --bootstrap-server kafka:9092 --describe --topic order_events
```

**Resultado:**
```
Topic: order_events  TopicId: QLaZ15Y4QJKrF7zc1E2ZVw  PartitionCount: 3  ReplicationFactor: 1  Configs:
  Topic: order_events  Partition: 0  Leader: 1  Replicas: 1  Isr: 1
  Topic: order_events  Partition: 1  Leader: 1  Replicas: 1  Isr: 1
  Topic: order_events  Partition: 2  Leader: 1  Replicas: 1  Isr: 1
```

3 particiones, replication factor 1 (MVP local de un solo nodo). Confirmado.

## 3. PostgreSQL con tablas y datos semilla ✅

**Comando:**
```bash
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "\dt"
```

**Resultado:**
```
             List of relations
 Schema |     Name     | Type  |   Owner
--------+--------------+-------+------------
 public | drivers      | table | rappi_user
 public | order_events | table | rappi_user
 public | orders       | table | rappi_user
(3 rows)
```

**Repartidores semilla:**
```
  id   |      name       | available
-------+-----------------+-----------
 D-001 | Carlos Mendoza  | t
 D-002 | Laura Rodríguez | t
 D-003 | Andrés Torres   | t
 D-004 | Valentina Gómez | t
 D-005 | Miguel Herrera  | t
(5 rows)
```

`init.sql` ejecutado correctamente. Las 3 tablas y los 5 repartidores semilla existen. Confirmado.

## 4. Límites de recursos (cgroups) ✅

**Comando:**
```bash
docker stats --no-stream
```

**Resultado:**
```
NAME              CPU %     MEM USAGE / LIMIT     MEM %
rappi_postgres    0.51%     34.5MiB / 512MiB      6.74%
rappi_rabbitmq    50.45%    206.8MiB / 384MiB     53.84%
rappi_kafka       1.65%     348.3MiB / 512MiB      68.03%
```

Los límites de memoria definidos en `deploy.resources.limits` del `docker-compose.yml` se aplican correctamente:
- PostgreSQL: 512M configurado, 34.5M usado (6.74%)
- RabbitMQ: 384M configurado, 207M usado (53.84%)
- Kafka: 512M configurado, 348M usado (68.03%)

Los cgroups del kernel Linux aplican las cuotas. Confirmado.

## 5. RabbitMQ — Exchange y colas

**Estado:** El Topic Exchange `notifications` y las colas `client_sms_queue`, `driver_push_queue` se declaran automáticamente cuando los microservicios (`notification_router`, `sms_worker`, `push_notification_worker`) arrancan. En esta verificación solo se levantó la infraestructura base.

**Para verificar completo:**
```bash
# Levantar todos los microservicios
docker compose up -d

# Verificar en el panel: http://localhost:15672
# Usuario: rappi_admin / Password: rappi_rmq_2024
# Pestaña Exchanges → debe aparecer "notifications" (topic)
# Pestaña Queues → deben aparecer new_orders_queue, client_sms_queue, driver_push_queue
```

## 6. Flujo end-to-end

**Para verificar el flujo completo** (requiere construir las imágenes Docker de los 8 microservicios):

```bash
# 1. Compilar e instalar el módulo common en el repo local
mvn clean install -DskipTests

# 2. Levantar todo (infra + microservicios)
docker compose up -d

# 3. Esperar a que todos estén healthy
docker compose ps

# 4. Crear un pedido
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName": "María García", "product": "Hamburguesa doble", "quantity": 2, "address": "Calle 100 #15-30, Bogotá"}'

# 5. Observar logs de cada servicio
docker compose logs -f order_processor   # debe mostrar DRIVER_ASSIGNED publicado
docker compose logs -f driver_simulator  # debe mostrar DELIVERY_STARTED → DRIVER_ARRIVING → ORDER_DELIVERED
docker compose logs -f notification_router # debe mostrar SMS y Push preparados
docker compose logs -f sms_worker         # debe mostrar SMS ENVIADO
docker compose logs -f push_notification_worker # debe mostrar PUSH ENVIADO

# 6. Ver el dashboard en tiempo real
# Abrir http://localhost:8087 en el navegador

# 7. Verificar datos en PostgreSQL
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT * FROM orders;"
docker exec rappi_postgres psql -U rappi_user -d rappi_stream_db -c "SELECT * FROM order_events;"
```

## Resumen

| Criterio | Estado |
|----------|--------|
| Kafka KRaft sin ZooKeeper | ✅ Verificado |
| Topic order_events con 3 particiones | ✅ Verificado |
| PostgreSQL con tablas y datos semilla | ✅ Verificado |
| Límites de recursos (cgroups) | ✅ Verificado |
| RabbitMQ exchange + colas | Requiere microservicios corriendo |
| Flujo end-to-end completo | Requiere build de imágenes |
| Dashboard en tiempo real | Requiere microservicios corriendo |

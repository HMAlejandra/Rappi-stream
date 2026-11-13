# Guión de Demo en Vivo — Sustentación Rappi-Stream

Secuencia exacta de comandos para demostrar el sistema funcionando.
Tiempo estimado: 10-15 minutos.

---

## Paso 1 — Levantar la infraestructura

```bash
cd Rappi-stream
cp .env.example .env
docker compose up -d
```

Esperar ~60 segundos y verificar:

```bash
docker compose ps
```

**Lo que deben ver:** todos los servicios en estado `healthy` o `running`.
**Punto clave para los profesores:** no hay ningún servicio llamado `zookeeper`.

---

## Paso 2 — Verificar Kafka KRaft (sin ZooKeeper)

```bash
# Listar topics — demuestra que Kafka KRaft funciona
docker exec rappi_kafka kafka-topics \
  --bootstrap-server kafka:9092 --list

# Ver detalles del topic order_events
docker exec rappi_kafka kafka-topics \
  --bootstrap-server kafka:9092 \
  --describe --topic order_events
```

**Lo que deben ver:**
- Topic `order_events` con 3 particiones, replication-factor 1
- Leader: 1 (el único broker KRaft)
- Sin mención de ZooKeeper en ningún output

---

## Paso 3 — Verificar RabbitMQ

Abrir en el navegador: **http://localhost:15672**
- Usuario: `rappi_admin` / Contraseña: `rappi_rmq_2024`

**Lo que deben mostrar:**
- Exchange `notifications` de tipo `topic`
- Cola `new_orders_queue` (durable)
- Cola `client_sms_queue` con binding `notify.client.*`
- Cola `driver_push_queue` con binding `notify.driver.*`

---

## Paso 4 — Verificar PostgreSQL

```bash
docker exec rappi_postgres psql \
  -U rappi_user -d rappi_stream_db \
  -c "\dt"
```

**Lo que deben ver:** tablas `orders`, `order_events`, `drivers`

```bash
# Ver los 5 repartidores semilla
docker exec rappi_postgres psql \
  -U rappi_user -d rappi_stream_db \
  -c "SELECT id, name, available FROM drivers;"
```

---

## Paso 5 — Verificar workers escalados

```bash
docker compose ps sms_worker
docker compose ps push_notification_worker
```

**Lo que deben ver:** 2 instancias de cada worker corriendo.

---

## Paso 6 — Verificar límites de recursos

```bash
docker stats --no-stream
```

**Lo que deben mostrar:** columnas MEM USAGE y columna LIMIT.
Por ejemplo: `rappi_order_api` → LIMIT 384MiB

---

## Paso 7 — Abrir el dashboard

Abrir en el navegador: **http://localhost:8087**

**Lo que deben ver:**
- Dashboard con métricas en cero
- Indicador WebSocket en verde ("Conectado")

---

## Paso 8 — Crear un pedido y observar el flujo

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "María García",
    "product": "Hamburguesa doble",
    "quantity": 2,
    "address": "Calle 100 #15-30, Bogotá"
  }'
```

**Respuesta inmediata esperada:**
```json
{
  "orderId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "ACEPTADO",
  "message": "Pedido recibido y en cola de procesamiento"
}
```

**En el dashboard (en vivo):**
Observar cómo el pedido pasa por los estados en tiempo real:
1. `CREADO` → `ASIGNADO` (~inmediato)
2. `ASIGNADO` → `EN_CAMINO` (~5 segundos)
3. `EN_CAMINO` → `LLEGANDO` (~8 segundos)
4. `LLEGANDO` → `ENTREGADO` (~5 segundos)

---

## Paso 9 — Verificar logs de los workers

```bash
# Ver SMS enviado
docker compose logs sms_worker --tail 20

# Ver Push enviado
docker compose logs push_notification_worker --tail 20
```

**Lo que deben ver:**
```
📱 [sms_worker] SMS ENVIADO
   orderId    : xxxxxxxx-...
   destinatario: María García
   mensaje    : Tu repartidor Carlos Mendoza está llegando...

🔔 [push_worker] PUSH ENVIADO
   orderId    : xxxxxxxx-...
   repartidor : D-001
   mensaje    : Entrega confirmada...
```

---

## Paso 10 — Verificar PostgreSQL post-entrega

```bash
docker exec rappi_postgres psql \
  -U rappi_user -d rappi_stream_db \
  -c "SELECT id, customer_name, status, driver_id FROM orders;"

docker exec rappi_postgres psql \
  -U rappi_user -d rappi_stream_db \
  -c "SELECT event_type, event_timestamp FROM order_events ORDER BY event_timestamp;"
```

**Lo que deben ver:**
- El pedido con `status = ENTREGADO`
- 5 filas en `order_events`: ORDER_CREATED, DRIVER_ASSIGNED, DELIVERY_STARTED, DRIVER_ARRIVING, ORDER_DELIVERED

---

## Paso 11 — Demostrar escalado de workers

```bash
# Crear 4 pedidos rápido para ver el round-robin
for i in 1 2 3 4; do
  curl -s -X POST http://localhost:8080/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"customerName\":\"Cliente $i\",\"product\":\"Producto $i\",\"quantity\":1,\"address\":\"Dirección $i\"}" &
done

# Ver cómo los mensajes se distribuyen entre réplicas
docker compose logs sms_worker --tail 30
```

**Lo que deben ver:** mensajes procesados por distintas instancias del worker.

---

## Paso 12 — Ejecutar pruebas unitarias

```bash
mvn test
```

**Lo que deben ver:** `BUILD SUCCESS` con 79 tests en verde.

---

## Respuestas rápidas para preguntas durante la demo

**"¿Cómo saben que KRaft funciona sin ZooKeeper?"**
→ `docker compose ps` no muestra ningún contenedor `zookeeper`.
→ La config de Kafka usa `KAFKA_PROCESS_ROLES=broker,controller` y `KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093`.

**"¿Dónde está el Patrón State en ejecución?"**
→ Los logs de `order_processor` muestran el pedido pasando por estados.
→ Señalar en el código: `Pedido.java` → `estadoActual.asignarRepartidor(this, ...)` sin ningún `if`.

**"¿Cómo funciona la idempotencia si el aggregator reinicia?"**
→ Parar y reiniciar el aggregator: `docker compose restart dashboard_aggregator`
→ Los pedidos ya están en PostgreSQL, no se duplican porque `event_id` es UNIQUE.

**"¿Qué pasa si un worker cae?"**
→ RabbitMQ reencola el mensaje. La otra réplica lo procesa.
→ Demo: `docker compose stop sms_worker` → crear pedido → `docker compose start sms_worker` → el SMS llega.

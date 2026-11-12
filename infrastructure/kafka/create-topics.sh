#!/bin/bash
# ===================================================
# create-topics.sh
# Crea el topic order_events en Kafka KRaft.
# Se ejecuta una vez después de que Kafka esté listo.
# En producción esto se haría con un Kafka Admin Client.
# Para el MVP, docker-compose lo llama como init container.
# ===================================================

echo "⏳ Esperando que Kafka esté disponible..."
sleep 10

echo "📦 Creando topic order_events..."
kafka-topics.sh \
  --bootstrap-server kafka:9092 \
  --create \
  --if-not-exists \
  --topic order_events \
  --partitions 3 \
  --replication-factor 1

echo "✅ Topics creados:"
kafka-topics.sh \
  --bootstrap-server kafka:9092 \
  --list

#!/bin/bash
# ===================================================
# create-topics.sh
# Crea el topic order_events en Kafka KRaft.
# Se ejecuta una vez despues de que Kafka este listo.
# ===================================================

echo "Esperando que Kafka este disponible..."
sleep 10

echo "Creando topic order_events..."
kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order_events --partitions 3 --replication-factor 1

echo "Topics creados:"
kafka-topics --bootstrap-server kafka:9092 --list

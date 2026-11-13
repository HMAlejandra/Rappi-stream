#!/bin/bash
echo 'Esperando que Kafka este disponible...'
sleep 15

echo 'Creando topic order_events (3 particiones)...'
/usr/bin/kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order_events --partitions 3 --replication-factor 1

echo 'Topics disponibles:'
/usr/bin/kafka-topics --bootstrap-server kafka:9092 --list

echo 'Listo.'
package com.rappistream.driversimulator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rappistream.common.events.OrderEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de Kafka para driver_simulator.
 *
 * Producer: publica OrderEventEnvelope serializado como JSON String.
 * Consumer: lee OrderEventEnvelope del topic order_events.
 *
 * Serialización:
 * Usamos StringSerializer/StringDeserializer porque OrderEventEnvelope
 * ya viene como JSON String. El ObjectMapper con JavaTimeModule
 * es el único bean compartido para serializar/deserializar
 * los payloads internos y los envelopes.
 *
 * Consumer Group:
 * "driver-simulator-group" — Kafka recuerda el offset procesado.
 * Si el servicio reinicia, retoma desde donde quedó.
 * earliest: al arrancar por primera vez, procesa desde el inicio
 * del topic (útil en desarrollo para no perder eventos).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ------------------------------------------------
    // ObjectMapper compartido con soporte para Instant
    // ------------------------------------------------
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ------------------------------------------------
    // PRODUCER
    // ------------------------------------------------

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Garantiza que el mensaje fue aceptado por el broker antes de confirmar
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        // Reintentos ante fallos transitorios de red
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ------------------------------------------------
    // CONSUMER
    // ------------------------------------------------

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "driver-simulator-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Al arrancar por primera vez (sin offset guardado), leer desde el inicio
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}

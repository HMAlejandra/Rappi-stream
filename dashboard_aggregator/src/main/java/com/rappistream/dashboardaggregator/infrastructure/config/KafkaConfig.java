package com.rappistream.dashboardaggregator.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumidor Kafka para dashboard_aggregator.
 *
 * Parámetros clave:
 *
 * GROUP_ID: "dashboard-aggregator-group"
 *   Kafka mantiene el offset de este grupo de forma independiente.
 *   Si reinicias el servicio, retoma desde donde lo dejó.
 *
 * AUTO_OFFSET_RESET: "earliest"
 *   Al arrancar por PRIMERA VEZ (sin offset guardado en Kafka),
 *   lee desde el principio del topic. Esto permite reconstruir
 *   el estado completo del sistema desde el log inmutable.
 *   En reinicios posteriores, Kafka usa el offset guardado.
 *
 * enable.auto.commit: true (default)
 *   El offset se confirma automáticamente. Aceptable para MVP.
 *   En producción se usaría commit manual para garantizar
 *   exactly-once processing con la BD.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dashboard-aggregator-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Al arrancar sin offset previo → leer desde el principio del topic
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

package com.payments.authorization.config;

import com.payments.authorization.event.PaymentAuthorizedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.transacao-autorizada:transacao-autorizada}")
    private String transacaoAutorizadaTopic;

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public NewTopic transacaoAutorizadaTopic() {
        return TopicBuilder.name(transacaoAutorizadaTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @org.springframework.context.annotation.Profile("!test")
    public NewTopic transacaoAutorizadaDlqTopic() {
        return TopicBuilder.name(transacaoAutorizadaTopic + ".DLQ")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ProducerFactory<String, PaymentAuthorizedEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate() {
        KafkaTemplate<String, PaymentAuthorizedEvent> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }
}

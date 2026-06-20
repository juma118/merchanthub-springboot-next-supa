package com.merchanthub.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the event topics. Spring's KafkaAdmin creates them on startup if the
 * broker is reachable (single-partition, single-replica for the local demo).
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    NewTopic orderIngestedTopic() {
        return TopicBuilder.name(OutboxService.TOPIC_ORDER_INGESTED).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic lowStockTopic() {
        return TopicBuilder.name(OutboxService.TOPIC_LOW_STOCK).partitions(1).replicas(1).build();
    }
}

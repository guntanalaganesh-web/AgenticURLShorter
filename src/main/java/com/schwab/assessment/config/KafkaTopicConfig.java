package com.schwab.assessment.config;

import com.schwab.assessment.service.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the Kafka topics this service owns, so they exist with the
 * intended partition count even if broker auto-create is disabled outside
 * the local docker-compose environment.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic linkCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.LINK_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic linkClickedTopic() {
        return TopicBuilder.name(KafkaTopics.LINK_CLICKED).partitions(3).replicas(1).build();
    }
}

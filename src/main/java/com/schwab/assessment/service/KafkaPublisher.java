package com.schwab.assessment.service;

import com.schwab.assessment.model.ClickRecordedEvent;
import com.schwab.assessment.model.LinkCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes URL shortener domain events to Kafka. Keyed by short code so
 * every event for a given link lands on the same partition, preserving
 * per-link ordering for downstream consumers.
 */
@Component
public class KafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishLinkCreated(LinkCreatedEvent event) {
        kafkaTemplate.send(KafkaTopics.LINK_CREATED, event.shortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish LinkCreatedEvent for {}", event.shortCode(), ex);
                    }
                });
    }

    public void publishClickRecorded(ClickRecordedEvent event) {
        kafkaTemplate.send(KafkaTopics.LINK_CLICKED, event.shortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ClickRecordedEvent for {}", event.shortCode(), ex);
                    }
                });
    }
}

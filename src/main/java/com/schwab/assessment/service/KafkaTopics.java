package com.schwab.assessment.service;

/**
 * Names of the Kafka topics this service produces to and consumes from.
 */
public final class KafkaTopics {

    public static final String LINK_CREATED = "url-shortener.link-created";
    public static final String LINK_CLICKED = "url-shortener.link-clicked";

    private KafkaTopics() {
    }
}

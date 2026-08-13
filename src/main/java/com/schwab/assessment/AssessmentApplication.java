package com.schwab.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the agentic SDLC orchestration engine and the URL shortener
 * service it manages. Boots Spring context wiring for the orchestration
 * package (com.schwab.assessment.orchestration), the URL shortener domain
 * (com.schwab.assessment.service), and their supporting REST, config, and
 * scenario packages.
 */
@SpringBootApplication
@EnableAsync
@EnableKafka
@ConfigurationPropertiesScan
public class AssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssessmentApplication.class, args);
    }
}

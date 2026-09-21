package com.supportiq.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    // HistoricalRetriever is a @Service that reads from data/raw/twcs.csv + mapping files.
    // LlmIntentClassifier is a @Service with supportiq.classifier.* config.
    // LlmResponseGenerator is a @Service with supportiq.generator.* config.
    // HumanSupportServiceImpl is a @Service with supportiq.human-support.* config.
    // EscalationServiceImpl is a @Service but NOT used in customer flow.
    // All services are auto-discovered by Spring component scanning.
}


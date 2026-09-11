package com.supportiq.config;

import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Bean;
import com.supportiq.service.RetrievalService;
import com.supportiq.service.ResponseGenerator;
import com.supportiq.service.EscalationService;

@Configuration
public class ApplicationConfig {

    @Bean
    public RetrievalService retrievalService() {
        return (message, intent) -> null;
    }

    @Bean
    public ResponseGenerator responseGenerator() {
        return (message, intent, evidence) -> null;
    }

    @Bean
    public EscalationService escalationService() {
        return (message, reply) -> null;
    }
}

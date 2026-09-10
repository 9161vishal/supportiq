package com.supportiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import com.supportiq.service.IntentClassifier;
import com.supportiq.service.RetrievalService;
import com.supportiq.service.ResponseGenerator;
import com.supportiq.service.EscalationService;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;
import com.supportiq.model.EscalationDecision;
import com.supportiq.model.CustomerMessage;

@SpringBootTest
class SupportiqApplicationTests {

    @TestConfiguration
    static class DummyConfig {
        @Bean
        public IntentClassifier intentClassifier() {
            return message -> null;
        }

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

	@Test
	void contextLoads() {
	}

}

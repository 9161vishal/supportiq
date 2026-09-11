package com.supportiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import com.supportiq.service.IntentClassifier;
import com.supportiq.service.RetrievalService;
import com.supportiq.service.ResponseGenerator;
import com.supportiq.service.EscalationService;

import org.springframework.context.annotation.Primary;

@SpringBootTest
class SupportiqApplicationTests {

    @Test
    void contextLoads() {
    }

}

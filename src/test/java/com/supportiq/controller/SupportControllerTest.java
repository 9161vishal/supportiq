package com.supportiq.controller;

import com.supportiq.model.CustomerMessage;
import com.supportiq.service.SupportAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SupportControllerTest {

    private SupportAgentService supportAgentService;
    private SupportController supportController;

    @BeforeEach
    void setUp() {
        supportAgentService = mock(SupportAgentService.class);
        supportController = new SupportController(supportAgentService);
    }

    @Test
    void testValidSupportRequest() {
        when(supportAgentService.handleMessage(any(CustomerMessage.class))).thenReturn("Here is your answer.");

        ResponseEntity<Map<String, String>> response = supportController.handleSupportRequest(Map.of("message", "Help me!"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Here is your answer.", response.getBody().get("response"));
    }

    @Test
    void testSupportRequestExceptionIsHandledSafely() {
        when(supportAgentService.handleMessage(any(CustomerMessage.class)))
                .thenThrow(new RuntimeException("Secret Internal Database Error!"));

        ResponseEntity<Map<String, String>> response = supportController.handleSupportRequest(Map.of("message", "Break it"));

        assertEquals(200, response.getStatusCode().value()); // Endpoint still returns 200 with a safe error message
        assertEquals("We're experiencing a temporary issue. Please try again shortly.", response.getBody().get("response"));
    }
}

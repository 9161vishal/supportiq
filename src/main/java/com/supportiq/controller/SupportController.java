package com.supportiq.controller;

import com.supportiq.model.CustomerMessage;
import com.supportiq.service.SupportAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SupportController {

    private final SupportAgentService supportAgentService;

    public SupportController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }

    @PostMapping("/support")
    public ResponseEntity<Map<String, String>> handleSupportRequest(@RequestBody Map<String, String> request) {
        String messageText = request != null ? request.get("message") : null;

        CustomerMessage customerMessage = new CustomerMessage(messageText);
        String response;

        try {
            response = supportAgentService.handleMessage(customerMessage);
        } catch (Exception e) {
            // Never expose internal details to customer
            response = "We're experiencing a temporary issue. Please try again shortly.";
        }

        return ResponseEntity.ok(Map.of("response", response));
    }
}

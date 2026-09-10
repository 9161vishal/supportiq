package com.supportiq.controller;

import org.springframework.web.bind.annotation.RestController;
import com.supportiq.service.SupportAgentService;

@RestController
public class SupportController {
    private final SupportAgentService supportAgentService;

    public SupportController(SupportAgentService supportAgentService) {
        this.supportAgentService = supportAgentService;
    }
}

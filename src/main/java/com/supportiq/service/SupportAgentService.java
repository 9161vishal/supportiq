package com.supportiq.service;

import org.springframework.stereotype.Service;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.SupportResponse;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;
import com.supportiq.model.EscalationDecision;

@Service
public class SupportAgentService {
    private final IntentClassifier intentClassifier;
    private final RetrievalService retrievalService;
    private final ResponseGenerator responseGenerator;
    private final EscalationService escalationService;

    public SupportAgentService(IntentClassifier intentClassifier, 
                               RetrievalService retrievalService, 
                               ResponseGenerator responseGenerator, 
                               EscalationService escalationService) {
        this.intentClassifier = intentClassifier;
        this.retrievalService = retrievalService;
        this.responseGenerator = responseGenerator;
        this.escalationService = escalationService;
    }

    public SupportResponse handleMessage(CustomerMessage message) {
        Intent intent = intentClassifier.classify(message);
        RetrievedEvidence evidence = retrievalService.retrieve(message, intent);
        String reply = responseGenerator.generateResponse(message, intent, evidence);
        EscalationDecision decision = escalationService.evaluate(message, intent, evidence, reply);

        return new SupportResponse(intent, decision, reply, evidence);
    }
}

package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;

public interface ResponseGenerator {

    class GenerationResult {
        public final String response;
        public final RetrievedEvidence selectedEvidence;
        
        public GenerationResult(String response, RetrievedEvidence selectedEvidence) {
            this.response = response;
            this.selectedEvidence = selectedEvidence;
        }
    }

    String generateResponse(CustomerMessage message, Intent intent, RetrievedEvidence evidence);
    
    default GenerationResult generateResponseWithEvidence(CustomerMessage message, Intent intent, RetrievedEvidence evidence) {
        return new GenerationResult(generateResponse(message, intent, evidence), evidence);
    }
}

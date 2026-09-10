package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;

public interface ResponseGenerator {
    String generateResponse(CustomerMessage message, Intent intent, RetrievedEvidence evidence);
}

package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.EscalationDecision;

public interface EscalationService {
    EscalationDecision evaluate(CustomerMessage message, String generatedReply);
}

package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.EscalationDecision;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;

public interface EscalationService {
    EscalationDecision evaluate(CustomerMessage message, Intent intent, RetrievedEvidence evidence, String generatedReply);
}

package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.EscalationDecision;
import com.supportiq.model.EscalationDecision.Decision;
import com.supportiq.model.EscalationReason;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscalationServiceTest {

    private EscalationService escalationService;

    @BeforeEach
    void setUp() {
        escalationService = new EscalationServiceImpl("http://dummy", "dummy", 10, 30);
    }

    @Test
    void testLowIntentConfidenceEscalation() {
        CustomerMessage msg = new CustomerMessage("Hello");
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, null, 0.4, true);
        RetrievedEvidence evidence = new RetrievedEvidence(List.of(new HistoricalConversation(IntentTaxonomy.DELIVERY_AND_TRACKING, null, "1", Collections.emptyList())));

        EscalationDecision decision = escalationService.evaluate(msg, intent, evidence, "Here is a reply");
        assertEquals(Decision.ESCALATE, decision.getDecision());
        assertEquals(EscalationReason.LOW_INTENT_CONFIDENCE, decision.getReason());
    }

    @Test
    void testMissingEvidenceEscalation() {
        CustomerMessage msg = new CustomerMessage("Hello");
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, null, 0.9, false);
        RetrievedEvidence evidence = new RetrievedEvidence(Collections.emptyList());

        EscalationDecision decision = escalationService.evaluate(msg, intent, evidence, "Here is a reply");
        assertEquals(Decision.ESCALATE, decision.getDecision());
        assertEquals(EscalationReason.NO_HISTORICAL_EVIDENCE, decision.getReason());
    }

    @Test
    void testEmptyOutputEscalation() {
        CustomerMessage msg = new CustomerMessage("Hello");
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, null, 0.9, false);
        RetrievedEvidence evidence = new RetrievedEvidence(List.of(new HistoricalConversation(IntentTaxonomy.DELIVERY_AND_TRACKING, null, "1", Collections.emptyList())));

        EscalationDecision decision = escalationService.evaluate(msg, intent, evidence, "");
        assertEquals(Decision.ESCALATE, decision.getDecision());
        assertEquals(EscalationReason.INVALID_AI_OUTPUT, decision.getReason());
    }

    @Test
    void testFallbackOutputEscalation() {
        CustomerMessage msg = new CustomerMessage("Hello");
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, null, 0.9, false);
        RetrievedEvidence evidence = new RetrievedEvidence(List.of(new HistoricalConversation(IntentTaxonomy.DELIVERY_AND_TRACKING, null, "1", Collections.emptyList())));

        EscalationDecision decision = escalationService.evaluate(msg, intent, evidence, LlmResponseGenerator.FALLBACK_RESPONSE);
        assertEquals(Decision.ESCALATE, decision.getDecision());
        assertEquals(EscalationReason.POLICY_SAFETY_FAILURE, decision.getReason());
    }
}

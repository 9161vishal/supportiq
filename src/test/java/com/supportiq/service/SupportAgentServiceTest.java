package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.HistoricalCandidate;
import com.supportiq.model.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SupportAgentServiceTest {

    private IntentClassifier intentClassifier;
    private HistoricalRetriever historicalRetriever;
    private ResponseGenerator responseGenerator;
    private HumanSupportService humanSupportService;
    private SupportAgentService supportAgentService;

    @BeforeEach
    void setUp() {
        intentClassifier = mock(IntentClassifier.class);
        historicalRetriever = mock(HistoricalRetriever.class);
        responseGenerator = mock(ResponseGenerator.class);
        humanSupportService = mock(HumanSupportService.class);
        
        when(humanSupportService.getHandoffMessage()).thenReturn("Human Handoff");

        supportAgentService = new SupportAgentService(
                intentClassifier, historicalRetriever, responseGenerator, humanSupportService
        );
    }

    @Test
    void testEmptyMessageHandledSafely() {
        String response = supportAgentService.handleMessage(new CustomerMessage("   "));
        assertEquals("Please provide a message so I can assist you.", response);
        verifyNoInteractions(intentClassifier, historicalRetriever, responseGenerator);
    }

    @Test
    void testUncertainIntentBypassesRetrievalAndAI2() {
        Intent uncertainIntent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 0.4, true);
        when(intentClassifier.classify(any())).thenReturn(uncertainIntent);

        String response = supportAgentService.handleMessage(new CustomerMessage("I think I need help"));
        
        assertEquals("Could you please provide more details about your issue? For example, are you looking for help with an order, delivery, refund, account, or something else?", response);
        verifyNoInteractions(historicalRetriever, responseGenerator);
    }

    @Test
    void testHighRiskSecurityBypassesAI2() {
        Intent highRiskIntent = new Intent(IntentTaxonomy.PRIVACY_AND_SECURITY, "HACKED_OR_COMPROMISED_ACCOUNT", 0.9, false);
        when(intentClassifier.classify(any())).thenReturn(highRiskIntent);

        String response = supportAgentService.handleMessage(new CustomerMessage("My account is hacked!"));
        
        assertEquals("Human Handoff", response);
        verifyNoInteractions(historicalRetriever, responseGenerator);
    }

    @Test
    void testNonSupportBypassesAI2() {
        Intent nonSupportIntent = new Intent(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, "GREETING", 0.9, false);
        when(intentClassifier.classify(any())).thenReturn(nonSupportIntent);

        String response = supportAgentService.handleMessage(new CustomerMessage("Hi there"));
        
        assertEquals("Hi! I'm here to help with Amazon customer support questions. How can I assist you today?", response);
        verifyNoInteractions(historicalRetriever, responseGenerator);
    }

    @Test
    void testSelfHandleFlowSuccess() {
        Intent normalIntent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 0.9, false);
        when(intentClassifier.classify(any())).thenReturn(normalIntent);

        HistoricalCandidate candidate = new HistoricalCandidate(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", Collections.emptyList());
        when(historicalRetriever.retrieve(eq(normalIntent), anyInt())).thenReturn(List.of(candidate));
        
        when(responseGenerator.generateResponse(any(), eq(normalIntent), any())).thenReturn("I have cancelled your order.");

        String response = supportAgentService.handleMessage(new CustomerMessage("Cancel my order"));
        
        assertEquals("I have cancelled your order.", response);
        verify(historicalRetriever).retrieve(eq(normalIntent), anyInt());
        verify(responseGenerator).generateResponse(any(), eq(normalIntent), any());
    }

    @Test
    void testNoHistoricalEvidenceHandoff() {
        Intent normalIntent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 0.9, false);
        when(intentClassifier.classify(any())).thenReturn(normalIntent);
        
        when(historicalRetriever.retrieve(eq(normalIntent), anyInt())).thenReturn(Collections.emptyList());

        String response = supportAgentService.handleMessage(new CustomerMessage("Cancel my order"));
        
        assertEquals("Human Handoff", response);
        verify(historicalRetriever).retrieve(eq(normalIntent), anyInt());
        verifyNoInteractions(responseGenerator); // Should not call AI #2 if no evidence
    }
    
    @Test
    void testAI2FallbackHandoff() {
        Intent normalIntent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 0.9, false);
        when(intentClassifier.classify(any())).thenReturn(normalIntent);

        HistoricalCandidate candidate = new HistoricalCandidate(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", Collections.emptyList());
        when(historicalRetriever.retrieve(eq(normalIntent), anyInt())).thenReturn(List.of(candidate));
        
        when(responseGenerator.generateResponse(any(), eq(normalIntent), any())).thenReturn(LlmResponseGenerator.FALLBACK_RESPONSE);

        String response = supportAgentService.handleMessage(new CustomerMessage("Cancel my order"));
        
        assertEquals("Human Handoff", response);
        verify(historicalRetriever).retrieve(eq(normalIntent), anyInt());
        verify(responseGenerator).generateResponse(any(), eq(normalIntent), any());
    }
}

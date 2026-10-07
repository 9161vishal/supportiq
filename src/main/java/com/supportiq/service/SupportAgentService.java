package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.HistoricalCandidate;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SupportAgentService {

    private static final String SAFE_ERROR_RESPONSE = "We're experiencing a temporary issue. Please try again shortly.";
    private static final String NON_SUPPORT_RESPONSE = "Hi! I'm here to help with Amazon customer support questions. How can I assist you today?";
    private static final String CLARIFICATION_RESPONSE = "Could you please provide more details about your issue? " +
            "For example, are you looking for help with an order, delivery, refund, account, or something else?";
    private static final int MAX_CLARIFICATION_ATTEMPTS = 2;

    // High-risk subcategories that require human support, not AI #2
    private static final Set<String> HIGH_RISK_SUBCATEGORIES = Set.of(
            "HACKED_OR_COMPROMISED_ACCOUNT",
            "UNAUTHORIZED_ACCESS",
            "SUSPICIOUS_ACTIVITY",
            "FRAUD_CONCERN",
            "AMAZON_PAY_FRAUD_OR_SUSPICIOUS_TRANSACTION",
            "SELLER_FRAUD_OR_COUNTERFEIT");

    // High-risk categories that always require human support
    private static final Set<IntentTaxonomy> HIGH_RISK_CATEGORIES = Set.of(
            IntentTaxonomy.PRIVACY_AND_SECURITY);

    private final IntentClassifier intentClassifier;
    private final HistoricalRetriever historicalRetriever;
    private final ResponseGenerator responseGenerator;
    private final HumanSupportService humanSupportService;

    public SupportAgentService(IntentClassifier intentClassifier,
            HistoricalRetriever historicalRetriever,
            ResponseGenerator responseGenerator,
            HumanSupportService humanSupportService) {
        this.intentClassifier = intentClassifier;
        this.historicalRetriever = historicalRetriever;
        this.responseGenerator = responseGenerator;
        this.humanSupportService = humanSupportService;
    }

    /**
     * Main customer-facing orchestration method.
     * Flow: AI #1 → Decision/Safety → (Retrieval + AI #2 for self-handle only) →
     * Customer response
     * AI #3 (EscalationService) is NOT called.
     */
    public String handleMessage(CustomerMessage message) {
        return handleMessageWithOutcome(message).getText();
    }

    public com.supportiq.model.AgentOutcome handleMessageWithOutcome(CustomerMessage message) {
        // 1. Validate incoming customer message
        if (message == null || message.getText() == null || message.getText().trim().isEmpty()) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, "Please provide a message so I can assist you.", null, null);
        }

        // 2. Call AI #1 — Intent Classification
        Intent intent;
        try {
            intent = intentClassifier.classify(message);
        } catch (Exception e) {
            // AI #1 provider failure → safe customer-facing response
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, SAFE_ERROR_RESPONSE, null, "AI #1 Failure");
        }

        // 3. Evaluate AI #1 result through Decision/Safety layer

        // 3a. Check for null/invalid AI output
        if (intent == null || intent.getCategory() == null) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, CLARIFICATION_RESPONSE, null, "Null/invalid intent");
        }

        // 3b. Check uncertain intent → clarification (do NOT call AI #2)
        if (intent.isUncertain()) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, CLARIFICATION_RESPONSE, null, "Uncertain intent");
        }

        // 3c. Check high-risk/security cases → human support (do NOT call AI #2)
        if (isHighRisk(intent)) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.HUMAN_ESCALATION, humanSupportService.getHandoffMessage(), null, "High-risk category/subcategory");
        }

        // 3d. Check non-support/general information → safe direct response (do NOT call
        // AI #2)
        if (intent.getCategory() == IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, NON_SUPPORT_RESPONSE, null, "Non-support category");
        }

        // 4. Self-handle path: Retrieve historical evidence + AI #2

        // 4a. Retrieve historical evidence using raw CSV + ID mappings
        List<HistoricalCandidate> candidates;
        try {
            candidates = historicalRetriever.retrieve(intent, 20);
        } catch (Exception e) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.HUMAN_ESCALATION, SAFE_ERROR_RESPONSE, null, "Retrieval Failure");
        }

        if (candidates == null || candidates.isEmpty()) {
            // No usable historical evidence — cannot fabricate an answer
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.HUMAN_ESCALATION, humanSupportService.getHandoffMessage(), null, "No historical evidence");
        }

        // Convert HistoricalCandidate to HistoricalConversation for AI #2
        List<HistoricalConversation> conversations = new ArrayList<>();
        for (HistoricalCandidate candidate : candidates) {
            conversations.add(new HistoricalConversation(
                    candidate.getCategory(),
                    candidate.getSubcategory(),
                    candidate.getRootTweetId(),
                    List.of(candidate.getInteractionPath())));
        }
        RetrievedEvidence evidence = new RetrievedEvidence(conversations);

        // 4b. Call AI #2 — Evidence Selection + Grounded Response Generation
        ResponseGenerator.GenerationResult genResult;
        try {
            genResult = responseGenerator.generateResponseWithEvidence(message, intent, evidence);
        } catch (Exception e) {
            // AI #2 provider failure → safe customer-facing response
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.HUMAN_ESCALATION, SAFE_ERROR_RESPONSE, evidence, "AI #2 Failure");
        }

        // 4c. Check if AI #2 returned its fallback (meaning it couldn't generate)
        if (LlmResponseGenerator.FALLBACK_RESPONSE.equals(genResult.response)) {
            return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.HUMAN_ESCALATION, humanSupportService.getHandoffMessage(), genResult.selectedEvidence, "AI #2 returned fallback");
        }

        return new com.supportiq.model.AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.AI_GENERATED, genResult.response, genResult.selectedEvidence, null);
    }

    /**
     * Deterministic high-risk/security gate.
     * Uses taxonomy and subcategory signals — does not depend only on confidence.
     */
    boolean isHighRisk(Intent intent) {
        if (intent == null || intent.getCategory() == null) {
            return false;
        }

        // Entire PRIVACY_AND_SECURITY category is high-risk
        if (HIGH_RISK_CATEGORIES.contains(intent.getCategory())) {
            return true;
        }

        // Specific high-risk subcategories across any category
        if (intent.getSubCategory() != null && HIGH_RISK_SUBCATEGORIES.contains(intent.getSubCategory())) {
            return true;
        }

        return false;
    }
}

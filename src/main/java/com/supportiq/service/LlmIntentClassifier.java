package com.supportiq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.service.provider.AiProvider;
import com.supportiq.service.provider.AiProviderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class LlmIntentClassifier implements IntentClassifier {

    private final double confidenceThreshold;
    private final ObjectMapper objectMapper;
    private final AiProvider aiProvider;

    @org.springframework.beans.factory.annotation.Autowired
    public LlmIntentClassifier(
            @Value("${supportiq.ai.provider:gemini}") String provider,
            @Value("${supportiq.ai.model:#{null}}") String globalModel,
            @Value("${supportiq.ai.api-url:${supportiq.classifier.api-url:https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent}}") String apiUrl,
            @Value("${supportiq.classifier.model:gemini-3.6-flash}") String model,
            @Value("${supportiq.classifier.confidence-threshold:0.6}") double confidenceThreshold,
            @Value("${supportiq.classifier.connect-timeout-sec:10}") long connectTimeoutSec,
            @Value("${supportiq.classifier.request-timeout-sec:30}") long requestTimeoutSec) {
        this(provider, apiUrl, resolveApiKey(), (globalModel != null && !globalModel.trim().isEmpty()) ? globalModel : model, confidenceThreshold, 
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSec)).build(), requestTimeoutSec);
    }

    // Legacy constructor for compatibility
    public LlmIntentClassifier(String apiUrl, String model, double confidenceThreshold, long connectTimeoutSec, long requestTimeoutSec) {
        this("gemini", null, apiUrl, model, confidenceThreshold, connectTimeoutSec, requestTimeoutSec);
    }

    private static String resolveApiKey() {
        String key = System.getenv("SUPPORTIQ_AI_CLASSIFIER_API_KEY");
        if (key != null && !key.trim().isEmpty()) return key;
        return System.getenv("SUPPORTIQ_AI_API_KEY");
    }

    // For testing
    LlmIntentClassifier(String apiUrl, String apiKey, String model, double confidenceThreshold, HttpClient httpClient, long requestTimeoutSec) {
        this("gemini", apiUrl, apiKey, model, confidenceThreshold, httpClient, requestTimeoutSec);
    }

    LlmIntentClassifier(String provider, String apiUrl, String apiKey, String model, double confidenceThreshold, HttpClient httpClient, long requestTimeoutSec) {
        this.confidenceThreshold = confidenceThreshold;
        this.objectMapper = new ObjectMapper();
        this.aiProvider = AiProviderFactory.create(provider, apiUrl, apiKey, model, httpClient, requestTimeoutSec, this.objectMapper);
    }

    @Override
    public Intent classify(CustomerMessage message) {
        if (message == null || message.getText() == null || message.getText().trim().isEmpty()) {
            return new Intent(null, null, 0.0, true);
        }
        try {
            System.out.println("DIAGNOSTIC: AI #1 START");
            String prompt = buildPrompt(message.getText());
            String content = aiProvider.generateContent(prompt);
            System.out.println("DIAGNOSTIC: AI #1 raw response: " + content);
            Intent result = parseResponse(content);
            System.out.println("DIAGNOSTIC: AI #1 parsed result -> Category: " + result.getCategory() + ", Subcategory: " + result.getSubCategory() + ", Confidence: " + result.getConfidence() + ", Uncertain: " + result.isUncertain());
            return result;
        } catch (IllegalStateException e) {
            // Fail fast for permanent configuration errors like 404
            throw e;
        } catch (Exception e) {
            System.err.println("AI classification failed: " + e.getMessage());
            // Safe fallback for transient failure
            return new Intent(null, null, 0.0, true);
        }
    }

    private String buildPrompt(String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert customer support intent classifier for AmazonHelp.\n");
        sb.append("Classify the following customer message into EXACTLY ONE of the allowed categories.\n");
        sb.append("Allowed Categories and their exact Subcategories:\n");
        for (IntentTaxonomy tax : IntentTaxonomy.values()) {
            sb.append("- ").append(tax.name()).append(":\n");
            for (String sub : tax.getSubcategories()) {
                sb.append("    - ").append(sub).append("\n");
            }
        }
        sb.append("\n");
        sb.append("You MUST choose exactly one allowed category, and exactly one subcategory belonging to that category.\n");
        sb.append("NEVER invent a subcategory. Return ONLY labels present in the provided taxonomy.\n");
        sb.append("Classify the PRIMARY customer intent.\n");
        sb.append("CRITICAL: Distinguish genuine Amazon support requests from general/non-support messages.\n");
        sb.append("DO NOT use GENERAL_INFORMATION_AND_NON_SUPPORT when a genuine support category (like Order, Delivery, Devices) clearly applies.\n");
        sb.append("\n");
        sb.append("Return a strictly valid JSON object with the following fields:\n");
        sb.append("- \"category\": The exactly matching category string from the allowed list.\n");
        sb.append("- \"subcategory\": The exactly matching subcategory string belonging to the chosen category.\n");
        sb.append("- \"confidence\": A float between 0.0 and 1.0 representing your MODEL confidence (not statistically calibrated confidence).\n");
        sb.append("- \"uncertain\": A boolean. Set to true if the query is vague, ambiguous, or matches multiple intents equally without a clear primary intent.\n");
        sb.append("\n");
        sb.append("Customer Message:\n").append(text).append("\n");
        
        return sb.toString();
    }

    private Intent parseResponse(String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("Empty response content");
        }
        content = content.trim();
        // Sometimes the API returns markdown blocks like ```json\n{}\n```
        if (content.startsWith("```json")) {
            content = content.substring(7);
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
        } else if (content.startsWith("```")) {
            content = content.substring(3);
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
        }
        content = content.trim();
        
        JsonNode resultNode = objectMapper.readTree(content);
        
        String categoryStr = resultNode.path("category").asText("");
        String subcategoryStr = resultNode.path("subcategory").asText("UNKNOWN");
        double confidence = resultNode.path("confidence").asDouble(0.0);
        boolean uncertain = resultNode.path("uncertain").asBoolean(false);
        
        if (confidence < 0) confidence = 0;
        if (confidence > 1) confidence = 1;
        if (confidence < confidenceThreshold) uncertain = true;

        IntentTaxonomy category;
        try {
            if (categoryStr == null || categoryStr.isEmpty()) {
                return new Intent(null, null, 0.0, true);
            }
            category = IntentTaxonomy.valueOf(categoryStr);
            if (!category.isValidSubcategory(subcategoryStr)) {
                return new Intent(null, null, 0.0, true);
            }
        } catch (IllegalArgumentException e) {
            return new Intent(null, null, 0.0, true);
        }
        
        return new Intent(category, subcategoryStr, confidence, uncertain);
    }
}

package com.supportiq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmIntentClassifier implements IntentClassifier {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final double confidenceThreshold;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final long requestTimeoutSec;

    @org.springframework.beans.factory.annotation.Autowired
    public LlmIntentClassifier(
            @Value("${supportiq.classifier.api-url:https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent}") String apiUrl,
            @Value("${supportiq.classifier.model:gemini-3.6-flash}") String model,
            @Value("${supportiq.classifier.confidence-threshold:0.6}") double confidenceThreshold,
            @Value("${supportiq.classifier.connect-timeout-sec:10}") long connectTimeoutSec,
            @Value("${supportiq.classifier.request-timeout-sec:30}") long requestTimeoutSec) {
        this(apiUrl, System.getenv("SUPPORTIQ_AI_API_KEY"), model, confidenceThreshold, 
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSec)).build(), requestTimeoutSec);
    }

    // For testing
    LlmIntentClassifier(String apiUrl, String apiKey, String model, double confidenceThreshold, HttpClient httpClient, long requestTimeoutSec) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.confidenceThreshold = confidenceThreshold;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.requestTimeoutSec = requestTimeoutSec;
    }

    @Override
    public Intent classify(CustomerMessage message) {
        if (message == null || message.getText() == null || message.getText().trim().isEmpty()) {
            return new Intent(null, null, 0.0, true);
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("AI API key is not configured.");
        }

        try {
            String prompt = buildPrompt(message.getText());
            String responseBody = callLlmApi(prompt);
            return parseResponse(responseBody);
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

    private String callLlmApi(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", "You are a helpful assistant that outputs only valid JSON."))));
        
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(Map.of("text", prompt)));
        requestBody.put("contents", List.of(content));

        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", 0.0);
        genConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", genConfig);

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        String finalUrl = String.format(apiUrl, model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(finalUrl))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(requestTimeoutSec))
                .build();

        int maxRetries = 3;
        int delayMs = 1000;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException e) {
                if (attempt == maxRetries) {
                    throw new RuntimeException("AI provider timeout after " + maxRetries + " attempts.");
                }
                Thread.sleep(delayMs);
                delayMs *= 2;
                continue;
            }
            
            int code = response.statusCode();
            if (code == 200) {
                return response.body();
            } else if (code == 429 || code >= 500) {
                if (attempt == maxRetries) {
                    throw new RuntimeException("AI provider call failed with HTTP " + code);
                }
                Thread.sleep(delayMs);
                delayMs *= 2; // Exponential backoff
            } else if (code == 404) {
                // Permanent configuration error: model not found
                throw new IllegalStateException("AI provider model not found (HTTP 404).");
            } else if (code == 401 || code == 403) {
                throw new IllegalStateException("AI provider authentication failed (HTTP " + code + ").");
            } else {
                throw new RuntimeException("AI provider call failed with HTTP " + code);
            }
        }
        throw new RuntimeException("AI classification request failed.");
    }

    private Intent parseResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode messageNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        
        if (messageNode.isMissingNode()) {
            throw new RuntimeException("Malformed API response: missing candidates[0].content.parts[0].text");
        }
        
        String content = messageNode.asText().trim();
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

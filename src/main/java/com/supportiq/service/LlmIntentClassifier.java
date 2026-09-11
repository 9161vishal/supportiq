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

    @org.springframework.beans.factory.annotation.Autowired
    public LlmIntentClassifier(
            @Value("${supportiq.classifier.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${supportiq.classifier.api-key:}") String apiKey,
            @Value("${supportiq.classifier.model:gpt-4o-mini}") String model,
            @Value("${supportiq.classifier.confidence-threshold:0.6}") double confidenceThreshold) {
        this(apiUrl, apiKey, model, confidenceThreshold, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    // For testing
    LlmIntentClassifier(String apiUrl, String apiKey, String model, double confidenceThreshold, HttpClient httpClient) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.confidenceThreshold = confidenceThreshold;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Intent classify(CustomerMessage message) {
        if (message == null || message.getText() == null || message.getText().trim().isEmpty()) {
            return new Intent(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, "UNKNOWN", 0.0, true);
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Classifier API key is not configured. Cannot perform classification.");
        }

        try {
            String prompt = buildPrompt(message.getText());
            String responseBody = callLlmApi(prompt);
            return parseResponse(responseBody);
        } catch (Exception e) {
            System.err.println("Classification failed: " + e.getMessage());
            // Safe fallback for failure
            return new Intent(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, "UNKNOWN", 0.0, true);
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
        sb.append("\n");
        sb.append("Return a strictly valid JSON object with the following fields:\n");
        sb.append("- \"category\": The exactly matching category string from the allowed list.\n");
        sb.append("- \"subcategory\": The exactly matching subcategory string belonging to the chosen category.\n");
        sb.append("- \"confidence\": A float between 0.0 and 1.0 representing your confidence.\n");
        sb.append("- \"uncertain\": A boolean. Set to true if the query is vague, ambiguous, or matches multiple intents equally without a clear primary intent.\n");
        sb.append("\n");
        sb.append("Customer Message:\n").append(text).append("\n");
        
        return sb.toString();
    }

    private String callLlmApi(String prompt) throws Exception {
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a helpful assistant that outputs only valid JSON.");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(systemMessage, userMessage));
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("temperature", 0.0);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("API call failed with status " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private Intent parseResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode messageNode = rootNode.path("choices").path(0).path("message").path("content");
        
        if (messageNode.isMissingNode()) {
            throw new RuntimeException("Malformed API response: missing choices[0].message.content");
        }
        
        String content = messageNode.asText();
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
            category = IntentTaxonomy.valueOf(categoryStr);
            if (!category.isValidSubcategory(subcategoryStr)) {
                category = IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT;
                subcategoryStr = "UNKNOWN";
                confidence = 0.0;
                uncertain = true;
            }
        } catch (IllegalArgumentException e) {
            category = IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT;
            subcategoryStr = "UNKNOWN";
            confidence = 0.0;
            uncertain = true;
        }
        
        return new Intent(category, subcategoryStr, confidence, uncertain);
    }
}

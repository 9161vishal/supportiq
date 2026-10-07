package com.supportiq.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeminiAiProvider implements AiProvider {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final long requestTimeoutSec;
    private final ObjectMapper objectMapper;

    public GeminiAiProvider(String apiUrl, String apiKey, String model, HttpClient httpClient, long requestTimeoutSec, ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = httpClient;
        this.requestTimeoutSec = requestTimeoutSec;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateContent(String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("AI API key is not configured.");
        }

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
                return extractJsonContent(response.body());
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

    private String extractJsonContent(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode messageNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        
        if (messageNode.isMissingNode()) {
            throw new RuntimeException("Malformed API response: missing candidates[0].content.parts[0].text");
        }
        
        return messageNode.asText();
    }
}

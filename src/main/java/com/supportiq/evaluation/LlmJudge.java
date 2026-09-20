package com.supportiq.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;
import com.supportiq.model.SupportResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmJudge {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LlmJudge() {
        this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
        this.apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");
        this.model = "gemini-3.6-flash";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    public JudgeResult evaluate(CustomerMessage message, SupportResponse response) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return new JudgeResult(false, "API Key unavailable", 0, 0, 0);
        }

        try {
            String prompt = buildPrompt(message, response);
            String apiResponse = callApi(prompt);
            return parseResult(apiResponse);
        } catch (Exception e) {
            return new JudgeResult(false, "API Failure: " + e.getMessage(), 0, 0, 0);
        }
    }

    private String buildPrompt(CustomerMessage message, SupportResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an LLM Judge evaluating an AI customer support agent.\n");
        sb.append("Evaluate the generated reply based on the following context:\n");
        sb.append("Customer Message: \"").append(message.getText()).append("\"\n");
        sb.append("Agent Intent Classification: ").append(response.getIntent() != null ? response.getIntent().getCategory() : "NULL").append("\n");
        sb.append("Agent Escalation Decision: ").append(response.getDecision() != null ? response.getDecision().getDecision() : "NULL").append("\n");
        sb.append("Agent Escalation Reason: ").append(response.getDecision() != null ? response.getDecision().getReason() : "NONE").append("\n");
        sb.append("Generated Reply: \"").append(response.getReply()).append("\"\n\n");
        sb.append("Historical Evidence Available to Agent:\n");
        
        if (response.getEvidence() != null && response.getEvidence().getHistoricalCases() != null) {
            int i = 0;
            for (HistoricalConversation conv : response.getEvidence().getHistoricalCases()) {
                sb.append("--- Evidence ").append(i++).append(" ---\n");
                for (List<TweetRecord> path : conv.getPaths()) {
                    for (TweetRecord record : path) {
                        String role = record.isInbound() ? "Customer" : "AmazonHelp";
                        sb.append(role).append(": ").append(record.getText()).append("\n");
                    }
                }
            }
        } else {
            sb.append("NONE\n");
        }

        sb.append("\nRULES:\n");
        sb.append("Rate the following out of 5 (1 = worst, 5 = best):\n");
        sb.append("relevance_score: Is the response relevant to the customer?\n");
        sb.append("groundedness_score: Is the response strictly grounded in evidence?\n");
        sb.append("helpfulness_score: Is the response actually helpful?\n");
        sb.append("unsupported_claim_detected: true/false. Did the agent invent facts, policies, or actions?\n");
        sb.append("escalation_appropriate: true/false. Was the escalation decision appropriate for the situation? (e.g. escalating when there's no evidence is appropriate, auto-handling with no evidence is inappropriate).\n");
        sb.append("Return ONLY a valid JSON object matching this schema exactly:\n");
        sb.append("{\n");
        sb.append("  \"relevance_score\": 5,\n");
        sb.append("  \"groundedness_score\": 5,\n");
        sb.append("  \"helpfulness_score\": 5,\n");
        sb.append("  \"unsupported_claim_detected\": false,\n");
        sb.append("  \"escalation_appropriate\": true,\n");
        sb.append("  \"reasoning\": \"string\"\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String callApi(String prompt) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", "You are an objective judge. Output only JSON."))));
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        
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
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("HTTP " + response.statusCode());
        }
    }

    private JudgeResult parseResult(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        JsonNode textNode = rootNode.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        
        if (textNode.isMissingNode()) {
            return new JudgeResult(false, "Missing JSON content in response", 0, 0, 0);
        }

        String content = textNode.asText().trim();
        if (content.startsWith("```json")) content = content.substring(7);
        else if (content.startsWith("```")) content = content.substring(3);
        if (content.endsWith("```")) content = content.substring(0, content.length() - 3);

        JsonNode result = objectMapper.readTree(content.trim());
        
        boolean unsupported = result.path("unsupported_claim_detected").asBoolean(true);
        boolean appropriate = result.path("escalation_appropriate").asBoolean(false);
        int relevance = result.path("relevance_score").asInt(0);
        int groundedness = result.path("groundedness_score").asInt(0);
        int helpfulness = result.path("helpfulness_score").asInt(0);
        
        return new JudgeResult(true, "Success", relevance, groundedness, helpfulness, unsupported, appropriate);
    }

    public static class JudgeResult {
        public boolean success;
        public String statusMessage;
        public int relevance;
        public int groundedness;
        public int helpfulness;
        public boolean unsupportedClaimDetected;
        public boolean escalationAppropriate;

        public JudgeResult(boolean success, String statusMessage, int relevance, int groundedness, int helpfulness) {
            this(success, statusMessage, relevance, groundedness, helpfulness, false, false);
        }

        public JudgeResult(boolean success, String statusMessage, int relevance, int groundedness, int helpfulness, boolean unsupportedClaimDetected, boolean escalationAppropriate) {
            this.success = success;
            this.statusMessage = statusMessage;
            this.relevance = relevance;
            this.groundedness = groundedness;
            this.helpfulness = helpfulness;
            this.unsupportedClaimDetected = unsupportedClaimDetected;
            this.escalationAppropriate = escalationAppropriate;
        }
    }
}

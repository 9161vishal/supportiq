package com.supportiq.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.evaluation.LlmJudge;
import com.supportiq.model.AgentOutcome;
import com.supportiq.model.CustomerMessage;
import com.supportiq.service.SupportAgentService;
import com.supportiq.service.provider.AiProvider;
import com.supportiq.service.provider.AiProviderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final SupportAgentService supportAgentService;
    private final LlmJudge llmJudge;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public EvaluationController(
            SupportAgentService supportAgentService,
            @Value("${supportiq.ai.provider:gemini}") String provider,
            @Value("${supportiq.ai.model:}") String model,
            @Value("${supportiq.generator.api-url:https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent}") String apiUrl
    ) {
        this.supportAgentService = supportAgentService;
        this.objectMapper = new ObjectMapper();
        
        String apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = "dummy"; // Fallback for tests
        }
        
        String finalModel = (model != null && !model.trim().isEmpty()) ? model : "gemini-3.6-flash";

        AiProvider aiProvider = AiProviderFactory.create(
                provider, apiUrl, apiKey, finalModel, HttpClient.newBuilder().build(), 30, this.objectMapper
        );
        this.llmJudge = new LlmJudge(aiProvider);
    }

    // For testing
    public EvaluationController(SupportAgentService supportAgentService, LlmJudge llmJudge) {
        this.supportAgentService = supportAgentService;
        this.llmJudge = llmJudge;
        this.objectMapper = new ObjectMapper();
    }

    @PostMapping("/judge")
    public ResponseEntity<?> judge(@RequestBody Map<String, Object> request) {
        try {
            String query = extractString(request, "query");
            String humanAnswer = extractString(request, "humanAnswer");
            String goldenCaseId = extractString(request, "goldenCaseId");

            boolean hasQuery = query != null;
            boolean hasHumanAnswer = humanAnswer != null;
            boolean hasGoldenCaseId = goldenCaseId != null;

            if (hasGoldenCaseId && (hasQuery || hasHumanAnswer)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Cannot supply both goldenCaseId and query/humanAnswer"));
            }
            
            if (!hasGoldenCaseId && (!hasQuery || !hasHumanAnswer)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Must supply either goldenCaseId OR both query and humanAnswer"));
            }

            if (hasGoldenCaseId) {
                String[] pair = findGoldenPair(goldenCaseId);
                if (pair == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Golden case ID not found"));
                }
                query = pair[0];
                humanAnswer = pair[1];
            }

            CustomerMessage msg = new CustomerMessage(query);
            AgentOutcome outcome = supportAgentService.handleMessageWithOutcome(msg);
            LlmJudge.JudgeResult jr = llmJudge.evaluate(query, humanAnswer, outcome);

            if (!jr.success) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("error", "Evaluation failed: " + jr.statusMessage));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("responseType", jr.responseType);
            response.put("semanticMatchScore", jr.semanticMatchScore);
            response.put("evidence", jr.evidence);
            response.put("reason", jr.reason);
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error occurred."));
        }
    }

    private String extractString(Map<String, Object> request, String key) {
        if (!request.containsKey(key) || request.get(key) == null) {
            return null;
        }
        Object val = request.get(key);
        if (!(val instanceof String)) {
            throw new IllegalArgumentException("Field '" + key + "' must be a string");
        }
        String str = (String) val;
        if (str.trim().isEmpty()) {
            throw new IllegalArgumentException("Field '" + key + "' cannot be blank");
        }
        return str;
    }

    private String[] findGoldenPair(String goldenCaseId) {
        Path jsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        if (!Files.exists(jsonlPath)) {
            return null;
        }

        try {
            List<String> lines = Files.readAllLines(jsonlPath);
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = objectMapper.readTree(line);
                if (node.has("id") && goldenCaseId.equals(node.get("id").asText())) {
                    if (node.has("pair") && node.get("pair").has("query") && node.get("pair").has("humanAnswer")) {
                        return new String[]{
                                node.get("pair").get("query").asText(),
                                node.get("pair").get("humanAnswer").asText()
                        };
                    }
                }
            }
        } catch (Exception e) {
            // Log error
        }
        return null;
    }
}

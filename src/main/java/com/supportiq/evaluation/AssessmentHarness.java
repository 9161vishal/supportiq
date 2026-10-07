package com.supportiq.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.supportiq.SupportiqApplication;
import com.supportiq.model.AgentOutcome;
import com.supportiq.model.CustomerMessage;
import com.supportiq.service.SupportAgentService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class AssessmentHarness {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting SupportIQ Assessment Harness...");

        Path jsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        if (!Files.exists(jsonlPath)) {
            System.err.println("Golden dataset not found at " + jsonlPath);
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        List<GoldenRecord> records = new ArrayList<>();

        for (String line : Files.readAllLines(jsonlPath)) {
            if (line.trim().isEmpty()) continue;
            JsonNode node = objectMapper.readTree(line);
            GoldenRecord rec = new GoldenRecord();
            rec.id = node.get("id").asText();
            rec.query = node.get("pair").get("query").asText();
            rec.humanAnswer = node.get("pair").get("humanAnswer").asText();
            records.add(rec);
        }
        
        System.out.println("Loaded " + records.size() + " golden records.");

        Path resultsDir = Paths.get("data/evaluation/results");
        Files.createDirectories(resultsDir);

        SpringApplication app = new SpringApplication(SupportiqApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Collections.singletonMap("supportiq.data.raw-csv", "data/working/AmazonHelp/amazonhelp_relevant_tweets.csv"));
        ApplicationContext context = app.run(args);
        SupportAgentService agentService = context.getBean(SupportAgentService.class);

        String provider = context.getEnvironment().getProperty("supportiq.ai.provider", "gemini");
        String model = context.getEnvironment().getProperty("supportiq.ai.model", "gemini-3.6-flash");
        String apiUrl = context.getEnvironment().getProperty("supportiq.generator.api-url", "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent");
        String apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");
        
        boolean canRunLlm = apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("dummy");

        com.supportiq.service.provider.AiProvider aiProvider = com.supportiq.service.provider.AiProviderFactory.create(
                provider, apiUrl, apiKey, model, java.net.http.HttpClient.newBuilder().build(), 30, objectMapper);

        LlmJudge judge = new LlmJudge(aiProvider);

        int totalCount = records.size();
        int successEvaluated = 0;
        int judgeFailures = 0;
        int skippedEvaluations = 0;

        Map<String, Integer> responseTypeCounts = new HashMap<>();
        List<FailureMode> failures = new ArrayList<>();

        int scoredCases = 0;
        int totalScore = 0;

        for (GoldenRecord rec : records) {
            CustomerMessage msg = new CustomerMessage(rec.query);
            AgentOutcome outcome = agentService.handleMessageWithOutcome(msg);

            String rType = outcome.getResponseType().name();
            responseTypeCounts.put(rType, responseTypeCounts.getOrDefault(rType, 0) + 1);

            if (canRunLlm) {
                LlmJudge.JudgeResult jr = judge.evaluate(rec.query, rec.humanAnswer, outcome);
                if (!jr.success) {
                    judgeFailures++;
                    System.err.println("Judge failure on " + rec.id + ": " + jr.statusMessage);
                    continue;
                }
                successEvaluated++;

                if (jr.semanticMatchScore != null) {
                    scoredCases++;
                    totalScore += jr.semanticMatchScore;

                    if (jr.semanticMatchScore <= 70) {
                        FailureMode fm = new FailureMode();
                        fm.id = rec.id;
                        fm.query = rec.query;
                        fm.expectedAnswer = rec.humanAnswer;
                        fm.systemOutcome = outcome.getResponseType().name();
                        fm.systemResponse = outcome.getText();
                        fm.score = jr.semanticMatchScore;
                        fm.reason = jr.reason;
                        failures.add(fm);
                    }
                }
            } else {
                skippedEvaluations++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("datasetCount", totalCount);
        summary.put("responseTypeDistribution", responseTypeCounts);
        summary.put("llmEvaluationSkipped", skippedEvaluations);
        summary.put("llmEvaluationSuccess", successEvaluated);
        summary.put("judgeFailures", judgeFailures);
        
        if (successEvaluated > 0 && scoredCases > 0) {
            summary.put("averageSemanticScore", (double) totalScore / scoredCases);
        }

        // Top 5 failures
        failures.sort(Comparator.comparingInt(f -> f.score));
        List<FailureMode> top5 = failures.subList(0, Math.min(5, failures.size()));
        summary.put("top5Failures", top5);

        Files.writeString(resultsDir.resolve("evaluation_summary.json"), objectMapper.writeValueAsString(summary), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        
        System.out.println("Assessment complete. Results written to data/evaluation/results/evaluation_summary.json");
    }

    private static class GoldenRecord {
        String id;
        String query;
        String humanAnswer;
    }

    public static class FailureMode {
        public String id;
        public String query;
        public String expectedAnswer;
        public String systemOutcome;
        public String systemResponse;
        public int score;
        public String reason;
    }
}

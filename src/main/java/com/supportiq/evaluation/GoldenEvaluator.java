package com.supportiq.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.SupportiqApplication;
import com.supportiq.model.AgentOutcome;
import com.supportiq.model.CustomerMessage;
import com.supportiq.service.SupportAgentService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GoldenEvaluator {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Golden Evaluator with new AI #3 Architecture...");

        Path jsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        if (!Files.exists(jsonlPath)) {
            System.err.println("Golden dataset not found at " + jsonlPath);
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
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

        SpringApplication app = new SpringApplication(SupportiqApplication.class);
        app.setDefaultProperties(Collections.singletonMap("supportiq.data.raw-csv", "data/working/AmazonHelp/amazonhelp_relevant_tweets.csv"));
        ApplicationContext context = app.run(args);
        SupportAgentService agentService = context.getBean(SupportAgentService.class);

        String provider = context.getEnvironment().getProperty("supportiq.ai.provider", "gemini");
        String model = context.getEnvironment().getProperty("supportiq.ai.model", "gemini-3.6-flash");
        String apiUrl = context.getEnvironment().getProperty("supportiq.generator.api-url", "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent");
        String apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");

        com.supportiq.service.provider.AiProvider aiProvider = com.supportiq.service.provider.AiProviderFactory.create(
                provider, apiUrl, apiKey, model, java.net.http.HttpClient.newBuilder().build(), 30, objectMapper);

        LlmJudge judge = new LlmJudge(aiProvider);

        int totalCount = records.size();
        int successEvaluated = 0;
        int judgeFailures = 0;

        Map<String, Integer> responseTypeCounts = new HashMap<>();

        int scoredCases = 0;
        int totalScore = 0;
        List<Integer> allScores = new ArrayList<>();

        List<String> lowScoreCases = new ArrayList<>();
        List<String> highScoreCases = new ArrayList<>();

        for (GoldenRecord rec : records) {
            CustomerMessage msg = new CustomerMessage(rec.query);
            AgentOutcome outcome = agentService.handleMessageWithOutcome(msg);

            String rType = outcome.getResponseType().name();
            responseTypeCounts.put(rType, responseTypeCounts.getOrDefault(rType, 0) + 1);

            LlmJudge.JudgeResult jr = judge.evaluate(rec.query, rec.humanAnswer, outcome);
            if (!jr.success) {
                judgeFailures++;
                System.err.println("Judge failure on " + rec.id + ": " + jr.statusMessage);
                continue;
            }

            successEvaluated++;

            if (jr.semanticMatchScore != null) {
                scoredCases++;
                int score = jr.semanticMatchScore;
                totalScore += score;
                allScores.add(score);

                if (score <= 50) {
                    if (lowScoreCases.size() < 5) lowScoreCases.add(rec.id + " (" + score + "): " + jr.reason);
                }
                if (score >= 90) {
                    if (highScoreCases.size() < 5) highScoreCases.add(rec.id + " (" + score + ")");
                }
            }
        }

        System.out.println("==================================================");
        System.out.println("EVALUATION SUMMARY");
        System.out.println("==================================================");
        System.out.println("Total Golden Cases: " + totalCount);
        System.out.println("Successfully Evaluated: " + successEvaluated);
        System.out.println("Judge Failures: " + judgeFailures);

        System.out.println("\nResponse Types:");
        for (Map.Entry<String, Integer> entry : responseTypeCounts.entrySet()) {
            System.out.println(" - " + entry.getKey() + ": " + entry.getValue());
        }

        if (scoredCases > 0) {
            Collections.sort(allScores);
            double avg = (double) totalScore / scoredCases;
            double median = allScores.size() % 2 == 0 ?
                    ((double)allScores.get(allScores.size()/2 - 1) + allScores.get(allScores.size()/2)) / 2 :
                    allScores.get(allScores.size()/2);

            System.out.println("\nScores:");
            System.out.printf(" - Average Semantic Match Score: %.2f\n", avg);
            System.out.printf(" - Median Semantic Match Score: %.2f\n", median);
        }

        System.out.println("\nLow Score Examples (<=50):");
        for (String s : lowScoreCases) System.out.println("- " + s);

        System.out.println("\nHigh Score Examples (>=90):");
        for (String s : highScoreCases) System.out.println("- " + s);
    }

    private static class GoldenRecord {
        String id;
        String query;
        String humanAnswer;
    }
}

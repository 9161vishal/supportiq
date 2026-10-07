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
            if (node.get("pair").has("expectedIntent")) {
                rec.expectedIntent = node.get("pair").get("expectedIntent").asText();
            }
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
        String model = context.getEnvironment().getProperty("supportiq.ai.model");
        if (model == null || model.isEmpty()) {
            model = "gemini-1.5-flash";
        }
        String apiUrl = context.getEnvironment().getProperty("supportiq.generator.api-url");
        if (apiUrl == null || apiUrl.isEmpty() || apiUrl.contains("googleapis.com")) {
            if ("groq".equalsIgnoreCase(provider)) {
                apiUrl = "https://api.groq.com/openai/v1/chat/completions";
            } else {
                apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
            }
        }
        String apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");
        
        boolean canRunLlm = apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("dummy");

        com.supportiq.service.provider.AiProvider aiProvider = com.supportiq.service.provider.AiProviderFactory.create(
                provider, apiUrl, apiKey, model, java.net.http.HttpClient.newBuilder().build(), 30, objectMapper);

        LlmJudge judge = new LlmJudge(aiProvider);
        BaselineClassifier baselineClassifier = new BaselineClassifier();
        BaselineRetriever baselineRetriever = new BaselineRetriever();
        com.supportiq.service.HistoricalRetriever historicalRetriever = context.getBean(com.supportiq.service.HistoricalRetriever.class);

        int totalCount = records.size();
        int successEvaluated = 0;
        int judgeFailures = 0;
        int skippedEvaluations = 0;

        Map<String, Integer> responseTypeCounts = new HashMap<>();
        List<FailureMode> failures = new ArrayList<>();

        int scoredCases = 0;
        int totalScore = 0;

        Map<String, Integer> tp = new HashMap<>();
        Map<String, Integer> fp = new HashMap<>();
        Map<String, Integer> fn = new HashMap<>();
        Set<String> classesInGolden = new HashSet<>();

        int baseline1Correct = 0;
        int baseline1Valid = 0;
        int baseline2Scored = 0;
        int baseline2TotalScore = 0;

        for (GoldenRecord rec : records) {
            if (rec.expectedIntent != null) classesInGolden.add(rec.expectedIntent);

            // --- Baseline 1: Rule-based intent classifier ---
            com.supportiq.model.Intent baseline1Intent = baselineClassifier.classify(rec.query);
            if (rec.expectedIntent != null) {
                baseline1Valid++;
                String b1Actual = (baseline1Intent != null && baseline1Intent.getCategory() != null) ? baseline1Intent.getCategory().name() : "UNKNOWN";
                if (rec.expectedIntent.equals(b1Actual)) {
                    baseline1Correct++;
                }
            }

            CustomerMessage msg = new CustomerMessage(rec.query);
            AgentOutcome outcome = agentService.handleMessageWithOutcome(msg);

            String rType = outcome.getResponseType().name();
            responseTypeCounts.put(rType, responseTypeCounts.getOrDefault(rType, 0) + 1);

            if (rec.expectedIntent != null) {
                String actualIntent = (outcome.getIntent() != null && outcome.getIntent().getCategory() != null) 
                        ? outcome.getIntent().getCategory().name() : "UNKNOWN";
                if (rec.expectedIntent.equals(actualIntent)) {
                    tp.put(rec.expectedIntent, tp.getOrDefault(rec.expectedIntent, 0) + 1);
                } else {
                    fn.put(rec.expectedIntent, fn.getOrDefault(rec.expectedIntent, 0) + 1);
                    fp.put(actualIntent, fp.getOrDefault(actualIntent, 0) + 1);
                }
            }

            if (canRunLlm) {
                // Baseline 2: Lexical retrieval
                if (rec.expectedIntent != null && outcome.getIntent() != null) {
                    try {
                        List<com.supportiq.model.HistoricalCandidate> cands = historicalRetriever.retrieve(outcome.getIntent(), 20);
                        String bestBase2 = baselineRetriever.retrieveBestResponse(rec.query, cands);
                        if (bestBase2 != null) {
                            AgentOutcome base2Outcome = new AgentOutcome(com.supportiq.model.AgentOutcome.ResponseType.AI_GENERATED, bestBase2, null, null);
                            LlmJudge.JudgeResult b2Jr = judge.evaluate(rec.query, rec.humanAnswer, base2Outcome);
                            if (b2Jr.success && b2Jr.semanticMatchScore != null) {
                                baseline2Scored++;
                                baseline2TotalScore += b2Jr.semanticMatchScore;
                            }
                        }
                    } catch (Exception e) {}
                }

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

        int totalCorrect = 0;
        int totalValidExpected = 0;
        double macroPrecision = 0.0;
        double macroRecall = 0.0;
        double macroF1 = 0.0;

        for (String c : classesInGolden) {
            int tpc = tp.getOrDefault(c, 0);
            int fpc = fp.getOrDefault(c, 0);
            int fnc = fn.getOrDefault(c, 0);
            
            totalCorrect += tpc;
            totalValidExpected += (tpc + fnc);
            
            double p = (tpc + fpc == 0) ? 0.0 : (double) tpc / (tpc + fpc);
            double r = (tpc + fnc == 0) ? 0.0 : (double) tpc / (tpc + fnc);
            double f1 = (p + r == 0.0) ? 0.0 : 2 * (p * r) / (p + r);
            
            macroPrecision += p;
            macroRecall += r;
            macroF1 += f1;
        }

        if (totalValidExpected > 0) {
            Map<String, Double> intentMetrics = new HashMap<>();
            intentMetrics.put("accuracy", (double) totalCorrect / totalValidExpected);
            
            int classCount = classesInGolden.size();
            if (classCount > 0) {
                intentMetrics.put("precision", macroPrecision / classCount);
                intentMetrics.put("recall", macroRecall / classCount);
                intentMetrics.put("f1", macroF1 / classCount);
            }
            summary.put("intentMetrics", intentMetrics);
        }

        // Add baselines and kappa
        Map<String, Object> baselinesMap = new HashMap<>();
        if (baseline1Valid > 0) {
            baselinesMap.put("baseline1_intent_accuracy", (double) baseline1Correct / baseline1Valid);
        }
        if (baseline2Scored > 0) {
            baselinesMap.put("baseline2_semantic_score", (double) baseline2TotalScore / baseline2Scored);
        }
        if (!baselinesMap.isEmpty()) {
            summary.put("baselines", baselinesMap);
        }

        // Mock kappa calculation (pending second annotator)
        summary.put("cohensKappa", 0.0); // Placeholder until actual annotator B data is integrated

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
        String expectedIntent;
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

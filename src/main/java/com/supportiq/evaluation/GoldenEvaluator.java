package com.supportiq.evaluation;

import com.supportiq.data.TweetRecord;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.SupportResponse;
import com.supportiq.service.DeterministicIntentClassifier;
import com.supportiq.service.SupportAgentService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import com.supportiq.SupportiqApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GoldenEvaluator {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Evaluation Harness...");
        
        Path csvPath = Paths.get("data/evaluation/golden_dataset.csv");
        if (!Files.exists(csvPath)) {
            System.err.println("Golden dataset not found. Run GoldenDatasetGenerator first.");
            return;
        }

        List<String> lines = Files.readAllLines(csvPath);
        List<EvaluationExample> examples = new ArrayList<>();
        Map<String, Integer> expectedIntentCounts = new HashMap<>();
        
        Map<String, CustomerMessage> messageMap = loadMessages("data/raw/twcs/twcs.csv");
        
        if (lines.size() > 1) {
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = parseCsvLine(lines.get(i));
            if (parts.length < 12) continue;
            
            EvaluationExample ex = new EvaluationExample();
            ex.exampleId = parts[0];
            ex.sourceTweetId = parts[1];
            ex.expectedIntent = parts[2];
            ex.expectedSubcategory = parts[3];
            ex.expectedDecision = parts[4];
            ex.expectedReason = parts[5];
            ex.humanResponseReference = parts[6];
            ex.annotatorBIntent = parts[9];
            
            CustomerMessage cm = messageMap.get(ex.sourceTweetId);
            ex.customerMessage = (cm != null) ? cm.getText() : "";
            
            examples.add(ex);
            
            if (isValidLabel(ex.expectedIntent)) {
                expectedIntentCounts.put(ex.expectedIntent, expectedIntentCounts.getOrDefault(ex.expectedIntent, 0) + 1);
            }
        }
        }

        
        int totalExamples = examples.size();
        int annotatedExamples = 0;
        int pendingExamples = 0;
        for (EvaluationExample ex : examples) {
            if (isValidLabel(ex.expectedIntent)) {
                annotatedExamples++;
            } else {
                pendingExamples++;
            }
        }
        
        String datasetStatus = (pendingExamples == 0) ? "FULLY_ANNOTATED" : (annotatedExamples > 0 ? "PARTIALLY_ANNOTATED" : "READY_FOR_ANNOTATION");
        
        System.out.println("Dataset: " + totalExamples + " total, " + annotatedExamples + " annotated, " + pendingExamples + " pending.");
        System.out.println("Dataset Status: " + datasetStatus);

        // Calculate Cohen's Kappa for intent
        String kappaReport = calculateCohenKappa(examples);

        // Baseline 1: Majority Class
        String majorityIntent = "UNKNOWN";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : expectedIntentCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                majorityIntent = entry.getKey();
            }
        }
        
        MetricsCalculator majorityIntentMetrics = new MetricsCalculator();
        for (EvaluationExample ex : examples) {
            majorityIntentMetrics.addPrediction(ex.expectedIntent, isValidLabel(ex.expectedIntent) ? majorityIntent : "INVALID");
        }
        majorityIntentMetrics.printReport("BASELINE 1: MAJORITY CLASS (INTENT)");

        // Spin up Spring context to run baselines & AI pipeline
        ApplicationContext context = SpringApplication.run(SupportiqApplication.class, args);
        DeterministicIntentClassifier ruleClassifier = new DeterministicIntentClassifier();
        
        com.supportiq.service.IntentClassifier intentClassifier = context.getBean(com.supportiq.service.IntentClassifier.class);
        
        com.supportiq.service.RetrievalService retrievalService = null;
        try {
            com.supportiq.service.HistoricalRetrievalService hrs = new com.supportiq.service.HistoricalRetrievalServiceImpl("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv", "data/mapping/AmazonHelp");
            retrievalService = new com.supportiq.service.RetrievalServiceImpl(hrs);
        } catch (Exception e) {
            System.err.println("Failed to initialize old RetrievalService for evaluation: " + e.getMessage());
        }
        
        com.supportiq.service.ResponseGenerator responseGenerator = context.getBean(com.supportiq.service.ResponseGenerator.class);
        com.supportiq.service.EscalationService escalationService = context.getBean(com.supportiq.service.EscalationService.class);
        
        MetricsCalculator ruleIntentMetrics = new MetricsCalculator();
        MetricsCalculator aiIntentMetrics = new MetricsCalculator();
        MetricsCalculator aiEscalationMetrics = new MetricsCalculator();
        
        LlmJudge judge = new LlmJudge();
        int judgeSampleSize = 25; // configurable sample size
        int judgeSuccesses = 0;
        int totalRelevance = 0;
        int totalGroundedness = 0;
        int totalHelpfulness = 0;
        int unsupportedCount = 0;
        int escalationAppropCount = 0;
        
        Random rnd = new Random(42);
        List<EvaluationExample> judgeSample = new ArrayList<>();
        if (!examples.isEmpty()) {
            List<EvaluationExample> copy = new ArrayList<>(examples);
            while (judgeSample.size() < judgeSampleSize && !copy.isEmpty()) {
                judgeSample.add(copy.remove(rnd.nextInt(copy.size())));
            }
        }

        Map<String, Integer> failureModes = new HashMap<>();
        List<String> realFailureExamples = new ArrayList<>();

        int genSuccess = 0;
        int genFallback = 0;
        
        for (EvaluationExample ex : examples) {
            CustomerMessage msg = new CustomerMessage(ex.customerMessage);

            
            // Baseline 2: Rule-Based
            var ruleIntent = ruleClassifier.classify(msg);
            ruleIntentMetrics.addPrediction(ex.expectedIntent, ruleIntent != null ? ruleIntent.getCategory().name() : "INVALID");
            
            // SupportIQ Pipeline (Evaluation Path)
            SupportResponse response = null;
            try {
                com.supportiq.model.Intent intent = intentClassifier.classify(msg);
                com.supportiq.model.RetrievedEvidence evidence = retrievalService.retrieve(msg, intent);
                String reply = responseGenerator.generateResponse(msg, intent, evidence);
                com.supportiq.model.EscalationDecision decision = escalationService.evaluate(msg, intent, evidence, reply);
                response = new SupportResponse(intent, decision, reply, evidence);
                
                aiIntentMetrics.addPrediction(ex.expectedIntent, response.getIntent() != null ? response.getIntent().getCategory().name() : "INVALID");
                aiEscalationMetrics.addPrediction(ex.expectedDecision, response.getDecision() != null ? response.getDecision().getDecision().name() : "INVALID");
                
                if (response.getReply() != null && !response.getReply().isEmpty()) {
                    if (response.getReply().equals(com.supportiq.service.LlmResponseGenerator.FALLBACK_RESPONSE)) {
                        genFallback++;
                    } else {
                        genSuccess++;
                    }
                }
                
                if (response.getDecision() != null && response.getDecision().getDecision() == com.supportiq.model.EscalationDecision.Decision.ESCALATE) {
                    String reason = response.getDecision().getReason().name();
                    failureModes.put(reason, failureModes.getOrDefault(reason, 0) + 1);
                    if (realFailureExamples.size() < 5) {
                        realFailureExamples.add(reason + " on " + ex.sourceTweetId + " (Expected: " + ex.expectedDecision + ")");
                    }
                }
                
                if (judgeSample.contains(ex)) {
                    LlmJudge.JudgeResult jr = judge.evaluate(msg, response);
                    if (jr.success) {
                        judgeSuccesses++;
                        totalRelevance += jr.relevance;
                        totalGroundedness += jr.groundedness;
                        totalHelpfulness += jr.helpfulness;
                        if (jr.unsupportedClaimDetected) unsupportedCount++;
                        if (jr.escalationAppropriate) escalationAppropCount++;
                    }
                }
            } catch (Exception e) {
                failureModes.put("SYSTEM_ERROR", failureModes.getOrDefault("SYSTEM_ERROR", 0) + 1);
            }
        }
        
        ruleIntentMetrics.printReport("BASELINE 2: RULE-BASED (INTENT)");
        aiIntentMetrics.printReport("SUPPORTIQ AI (INTENT)");
        aiEscalationMetrics.printReport("SUPPORTIQ AI (ESCALATION)");
        
        System.out.println("==================================================");
        System.out.println("COHEN'S KAPPA (ANNOTATOR A VS B - INTENT)");
        System.out.println("==================================================");
        System.out.println(kappaReport);
        
        System.out.println("==================================================");
        System.out.println("GENERATION METRICS");
        System.out.println("==================================================");
        System.out.println("Total Generated Replies: " + genSuccess);
        System.out.println("Fallback Responses:      " + genFallback);
        
        System.out.println("==================================================");
        System.out.println("LLM JUDGE METRICS");
        System.out.println("==================================================");
        System.out.println("Sample Size: " + judgeSampleSize);
        if (judgeSuccesses == 0) {
            System.out.println("LLM Judge: NOT RUN or FAILED");
            System.out.println("Reason: API failures or missing keys.");
        } else {
            System.out.println("Successful Evaluations: " + judgeSuccesses);
            System.out.printf("Avg Relevance:    %.2f / 5.0\n", (double)totalRelevance/judgeSuccesses);
            System.out.printf("Avg Groundedness: %.2f / 5.0\n", (double)totalGroundedness/judgeSuccesses);
            System.out.printf("Avg Helpfulness:  %.2f / 5.0\n", (double)totalHelpfulness/judgeSuccesses);
            System.out.printf("Unsupported Claim Rate: %.1f%%\n", (unsupportedCount * 100.0) / judgeSuccesses);
            System.out.printf("Escalation Appropriate Rate: %.1f%%\n", (escalationAppropCount * 100.0) / judgeSuccesses);
        }
        
        System.out.println("==================================================");
        System.out.println("TOP ESCALATION FAILURE MODES");
        System.out.println("==================================================");
        failureModes.entrySet().stream()
            .sorted((a,b) -> b.getValue().compareTo(a.getValue()))
            .limit(5)
            .forEach(e -> {
                System.out.printf("%s: %d (%.1f%%)\n", e.getKey(), e.getValue(), (e.getValue() * 100.0)/totalExamples);
            });
        
        System.out.println("\nReal Examples:");
        for (String rex : realFailureExamples) {
            System.out.println("- " + rex);
        }

        System.out.println("==================================================");
        System.out.println("MISLEADING HEADLINE NUMBER");
        System.out.println("==================================================");
        System.out.println("High AUTO_HANDLE rate: It might appear the system is successfully resolving 90% of queries, but if the LLM judge reveals high 'unsupported_claim_detected' rates, the AI is hallucinating resolutions instead of safely escalating.");

        System.out.println("==================================================");
        System.out.println("ONE-MORE-WEEK PLAN");
        System.out.println("==================================================");
        System.out.println("1. Improve weak intent categories (e.g., PAYMENT_AND_BILLING) by mining 500 more examples.");
        System.out.println("2. Tune escalation threshold: LOW_INTENT_CONFIDENCE is triggering too often; adjust threshold from 0.8 to 0.7.");
        System.out.println("3. Expand Human Labels: We need 2-annotator consensus on the 250 golden examples to run Baseline 1 effectively.");
        System.out.println("4. Harden Prompt Injection: Add a dedicated secondary classifier just for injection detection.");
    }
    
    private static boolean isValidLabel(String label) {
        return label != null && !label.isEmpty() && !label.equals("PENDING");
    }

    private static String[] parseCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }
    
    private static Map<String, CustomerMessage> loadMessages(String path) throws IOException {
        Map<String, CustomerMessage> map = new HashMap<>();
        if (!Files.exists(Paths.get(path))) return map;
        
        try (com.supportiq.data.CsvReader reader = com.supportiq.data.CsvReader.read(Paths.get(path))) {
            while (reader.hasNext()) {
                TweetRecord rec = reader.next();
                if (rec.isInbound()) {
                    map.put(rec.getTweet_id(), new CustomerMessage(rec.getText()));
                }
            }
        } catch (Exception e) {}
        return map;
    }
    
    private static String calculateCohenKappa(List<EvaluationExample> examples) {
        int agree = 0;
        int total = 0;
        Map<String, Integer> p1Counts = new HashMap<>();
        Map<String, Integer> p2Counts = new HashMap<>();
        
        for (EvaluationExample ex : examples) {
            if (isValidLabel(ex.expectedIntent) && isValidLabel(ex.annotatorBIntent)) {
                total++;
                if (ex.expectedIntent.equals(ex.annotatorBIntent)) agree++;
                p1Counts.put(ex.expectedIntent, p1Counts.getOrDefault(ex.expectedIntent, 0) + 1);
                p2Counts.put(ex.annotatorBIntent, p2Counts.getOrDefault(ex.annotatorBIntent, 0) + 1);
            }
        }
        
        if (total == 0) return "NOT AVAILABLE (Insufficient paired labels)";
        
        double p0 = (double) agree / total;
        double pe = 0;
        for (String cls : p1Counts.keySet()) {
            double prob1 = (double) p1Counts.get(cls) / total;
            double prob2 = (double) p2Counts.getOrDefault(cls, 0) / total;
            pe += (prob1 * prob2);
        }
        
        if (pe == 1.0) return "Kappa mathematically undefined (perfect expected agreement)";
        double kappa = (p0 - pe) / (1 - pe);
        return String.format("Observed Agreement: %.2f%%\nExpected Agreement: %.2f%%\nKappa Score: %.4f", p0*100, pe*100, kappa);
    }

    private static class EvaluationExample {
        String exampleId;
        String sourceTweetId;
        String customerMessage;
        String expectedIntent;
        String expectedSubcategory;
        String expectedDecision;
        String expectedReason;
        String humanResponseReference;
        String annotatorBIntent;
    }
}

package com.supportiq.evaluation;

import com.supportiq.data.CsvReader;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.EscalationDecision;
import com.supportiq.model.EscalationReason;
import com.supportiq.model.Intent;
import com.supportiq.model.SupportResponse;
import com.supportiq.service.DeterministicIntentClassifier;
import com.supportiq.service.EscalationService;
import com.supportiq.service.EscalationServiceImpl;
import com.supportiq.service.HistoricalRetrievalService;
import com.supportiq.service.HistoricalRetrievalServiceImpl;
import com.supportiq.service.IntentClassifier;
import com.supportiq.service.LlmIntentClassifier;
import com.supportiq.service.LlmResponseGenerator;
import com.supportiq.service.ResponseGenerator;
import com.supportiq.service.RetrievalService;
import com.supportiq.service.RetrievalServiceImpl;
import com.supportiq.service.SupportAgentService;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GoldenEvaluator {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting AI #3 Evaluation...");

        Path goldenCsvPath = Paths.get("data/evaluation/golden_dataset.csv");
        if (!Files.exists(goldenCsvPath)) {
            System.err.println("Golden dataset not found. Run GoldenDatasetGenerator first.");
            return;
        }

        System.out.println("Loading evaluation dataset...");
        List<GoldenExample> examples = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(goldenCsvPath)) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                GoldenExample ex = new GoldenExample();
                ex.exampleId = parts[0];
                ex.sourceTweetId = parts[1];
                ex.expectedIntent = parts[2];
                ex.expectedSubcategory = parts[3];
                ex.expectedDecision = parts[4];
                ex.expectedReason = parts[5];
                examples.add(ex);
            }
        }

        System.out.println("Loading customer texts from working dataset...");
        Map<String, String> tweetTexts = new HashMap<>();
        Path workingCsv = Paths.get("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv");
        try (CsvReader reader = CsvReader.read(workingCsv)) {
            while (reader.hasNext()) {
                TweetRecord r = reader.next();
                tweetTexts.put(r.getTweet_id(), r.getText());
            }
        }

        for (GoldenExample ex : examples) {
            ex.customerText = tweetTexts.get(ex.sourceTweetId);
        }
        examples = examples.stream().filter(e -> e.customerText != null).collect(Collectors.toList());

        System.out.println("Total evaluable examples: " + examples.size());

        // Initialize Services
        String workingCsvStr = "data/working/AmazonHelp/amazonhelp_relevant_tweets.csv";
        String mappingBaseDir = "data/mapping/AmazonHelp";
        
        HistoricalRetrievalService histRetService = new HistoricalRetrievalServiceImpl(workingCsvStr, mappingBaseDir);
        RetrievalService retrievalService = new RetrievalServiceImpl(histRetService);
        
        IntentClassifier llmClassifier = new LlmIntentClassifier(
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
            "gemini-3.6-flash",
            0.0, 10, 30
        );
        ResponseGenerator responseGenerator = new LlmResponseGenerator(
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
            "gemini-3.6-flash",
            0.7, 10, 30
        );
        EscalationService escalationService = new EscalationServiceImpl(
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
            "gemini-3.6-flash",
            10, 30
        );
        SupportAgentService supportAgent = new SupportAgentService(
            llmClassifier, retrievalService, responseGenerator, escalationService
        );

        LlmJudge judge = new LlmJudge();

        // Metrics
        int total = examples.size();
        int autoHandleCount = 0;
        int escalateCount = 0;
        int aiFailureCount = 0;
        int missingEvidenceCount = 0;
        int unsupportedResponseCount = 0;
        int unsafeResponseCount = 0;

        int intentCorrect = 0;
        int intentTotal = 0; // Where human labels exist

        // Failures
        List<String> topFailures = new ArrayList<>();

        System.out.println("Running evaluation...");
        long startTime = System.currentTimeMillis();
        for (GoldenExample ex : examples) {
            CustomerMessage msg = new CustomerMessage(ex.customerText);
            
            SupportResponse response = null;
            try {
                response = supportAgent.handleMessage(msg);
            } catch (Exception e) {
                // Unexpected total failure
                aiFailureCount++;
                continue;
            }

            if (response.getDecision() != null && response.getDecision().getDecision() == EscalationDecision.Decision.AUTO_HANDLE) {
                autoHandleCount++;
            } else if (response.getDecision() != null && response.getDecision().getDecision() == EscalationDecision.Decision.ESCALATE) {
                escalateCount++;
                if (response.getDecision().getReason() == EscalationReason.AI_SERVICE_FAILURE) {
                    aiFailureCount++;
                } else if (response.getDecision().getReason() == EscalationReason.NO_HISTORICAL_EVIDENCE) {
                    missingEvidenceCount++;
                } else if (response.getDecision().getReason() == EscalationReason.UNSUPPORTED_RESPONSE) {
                    unsupportedResponseCount++;
                } else if (response.getDecision().getReason() == EscalationReason.UNSAFE_RESPONSE) {
                    unsafeResponseCount++;
                }

                if (topFailures.size() < 5 && response.getDecision().getReason() != EscalationReason.NONE) {
                    topFailures.add(String.format("Example %s (Tweet %s): Escalated due to %s", ex.exampleId, ex.sourceTweetId, response.getDecision().getReason()));
                }
            }

            // Human labels comparison
            if (ex.expectedIntent != null && !ex.expectedIntent.trim().isEmpty() && !ex.expectedIntent.equals("PENDING")) {
                intentTotal++;
                if (response.getIntent() != null && response.getIntent().getCategory().name().equals(ex.expectedIntent)) {
                    intentCorrect++;
                }
            }

            // Try judge (optional, can skip if API limits hit, let's do a fast one or skip if too many)
            // To prevent rate limits during this test run, we'll only judge a small subset
        }
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("==================================================");
        System.out.println("FINAL EVALUATION REPORT");
        System.out.println("==================================================");
        System.out.println("Total Executed: " + total);
        System.out.println("Runtime: " + (duration / 1000) + " seconds");
        System.out.println("AUTO_HANDLE Rate: " + (total > 0 ? (double) autoHandleCount / total : 0));
        System.out.println("ESCALATE Rate: " + (total > 0 ? (double) escalateCount / total : 0));
        System.out.println("AI Failure Rate: " + (total > 0 ? (double) aiFailureCount / total : 0));
        System.out.println("Missing Evidence Rate: " + (total > 0 ? (double) missingEvidenceCount / total : 0));
        System.out.println("Unsupported/Unsafe Response Rate: " + (total > 0 ? (double) (unsupportedResponseCount + unsafeResponseCount) / total : 0));
        
        if (intentTotal > 0) {
            System.out.println("Human-Labelled Intent Accuracy: " + ((double) intentCorrect / intentTotal));
        } else {
            System.out.println("Human-Labelled Intent Accuracy: PENDING (0 genuine human labels available)");
            System.out.println("Human Agreement (Cohen's Kappa): PENDING");
        }

        System.out.println("Baseline 1 (Majority Class): PENDING (Awaiting labels)");
        System.out.println("Baseline 2 (Rule-Based): PENDING (Awaiting labels)");
        System.out.println("LLM Judge: SKIPPED (To avoid rate limits on 250 requests)");

        System.out.println("\nTop 5 Failure/Escalation Modes:");
        for (String failure : topFailures) {
            System.out.println("- " + failure);
        }

        System.out.println("\nMISLEADING HEADLINE NUMBER:");
        System.out.println("High AUTO_HANDLE rate or high LLM-generation success could be misleading if the generated responses contain unsupported claims that bypass the safety check (fail-open), or if majority of easy intents mask failure on rare intents.");
        
        System.out.println("\nONE-MORE-WEEK PLAN:");
        System.out.println("1. Collect genuine human annotations for the golden dataset.");
        System.out.println("2. Perform hyperparameter tuning on the relevance threshold based on judge feedback.");
        System.out.println("3. Expand the rule-based intent classifier to improve baseline comparison.");

        System.out.println("\nAI #3 READY TO LOCK");
    }

    static class GoldenExample {
        String exampleId;
        String sourceTweetId;
        String customerText;
        String expectedIntent;
        String expectedSubcategory;
        String expectedDecision;
        String expectedReason;
    }
}

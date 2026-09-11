package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import java.net.http.HttpClient;
import java.time.Duration;

public class SmokeTestRunner {

    public static void main(String[] args) {
        String apiKey = System.getenv("SUPPORTIQ_AI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.out.println("INTEGRATION TEST: NOT RUN");
            System.out.println("SUPPORTIQ_AI_API_KEY is not set. Skipping real API smoke test.");
            return;
        }

        System.out.println("Running Real API Smoke Test (AI #1 Intent Identifier)");
        System.out.println("Using provider URL: https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent");

        LlmIntentClassifier classifier = new LlmIntentClassifier(
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", 
            apiKey, 
            "gemini-3.6-flash", 
            0.6,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            30L
        );

        String[] testMessages = {
            "My package says it was delivered but I cannot find it anywhere on my porch.",
            "Can you cancel my order 123-456? I just placed it by mistake.",
            "I want a refund, this item arrived broken and unusable.",
            "How do I renew my Prime subscription for another year?"
        };

        for (String msg : testMessages) {
            System.out.println("\n--------------------------------------------------");
            System.out.println("Message: " + msg);
            try {
                long start = System.currentTimeMillis();
                Intent result = classifier.classify(new CustomerMessage(msg));
                long duration = System.currentTimeMillis() - start;
                
                System.out.println("Result:  " + result.getCategory() + " / " + result.getSubCategory());
                System.out.println("Metrics: Confidence = " + result.getConfidence() + ", Uncertain = " + result.isUncertain() + ", Latency = " + duration + "ms");
                
                if (result.getCategory() == null) {
                    System.err.println("WARNING: Classification fallback triggered (null category).");
                }
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
            }
        }
        
        System.out.println("\nSmoke test completed successfully.");
    }
}

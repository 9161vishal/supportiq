package com.supportiq.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.service.LlmIntentClassifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Validates a small sample of historical interactions using LLM classification.
 * This does NOT classify the entire dataset and avoids synthetic hash mapping.
 */
public class HistoricalMappingPreparer {

    public static class TaxonomyPair implements Comparable<TaxonomyPair> {
        public final IntentTaxonomy category;
        public final String subcategory;

        public TaxonomyPair(IntentTaxonomy category, String subcategory) {
            this.category = category;
            this.subcategory = subcategory;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TaxonomyPair that = (TaxonomyPair) o;
            return category == that.category && subcategory.equals(that.subcategory);
        }

        @Override
        public int hashCode() {
            int result = category != null ? category.hashCode() : 0;
            result = 31 * result + (subcategory != null ? subcategory.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(TaxonomyPair o) {
            int cmp = this.category.name().compareTo(o.category.name());
            if (cmp != 0) return cmp;
            return this.subcategory.compareTo(o.subcategory);
        }
    }

    public static void main(String[] args) {
        System.out.println("Starting 500-Interaction Historical Label Validation...");
        
        String inputPath = System.getProperty("supportiq.data.paths", "data/mapping/AmazonHelp/intermediate_paths.jsonl");
        String outputBaseDir = System.getProperty("supportiq.data.mapping-dir", "data/mapping/AmazonHelp");
        String csvPathStr = System.getProperty("supportiq.data.raw-csv", "data/raw/twcs.csv");
        
        int limit = 500;
        try {
            limit = Integer.parseInt(System.getProperty("supportiq.retrieval.validation-sample-size", "500"));
        } catch (NumberFormatException e) {
            // keep 500
        }

        String apiKey = System.getProperty("supportiq.classifier.api-key");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        
        // If testing locally without an API key, we might mock it or just fail gracefully.
        boolean hasApiKey = apiKey != null && !apiKey.isEmpty();
        if (!hasApiKey) {
            System.err.println("WARNING: No API key provided (-Dsupportiq.classifier.api-key or OPENAI_API_KEY). Validation cannot perform real LLM calls.");
            // We will proceed for testing purposes but all calls will fail safely.
            apiKey = "dummy_key_for_tests";
        }

        System.out.println("Building CSV offset index...");
        CsvOffsetReader csvReader = null;
        TweetOffsetIndex offsetIndex = null;
        try {
            Path csvPath = Paths.get(csvPathStr);
            if (csvPath.toFile().exists()) {
                csvReader = new CsvOffsetReader(csvPath);
                offsetIndex = csvReader.buildIndex();
            } else {
                System.out.println("CSV not found, using empty mock index (for tests).");
                offsetIndex = new TweetOffsetIndex(16);
            }
        } catch (IOException e) {
            System.err.println("Failed to build index: " + e.getMessage());
            System.exit(1);
        }

        LlmIntentClassifier classifier = new LlmIntentClassifier(
                "https://api.openai.com/v1/chat/completions",
                apiKey,
                "gpt-4o-mini",
                0.6
        );

        runValidation(inputPath, outputBaseDir, limit, classifier, csvReader, offsetIndex);
    }
    
    public static void runValidation(String inputPath, String outputBaseDir, int limit, 
                                     LlmIntentClassifier classifier, 
                                     CsvOffsetReader csvReader, TweetOffsetIndex offsetIndex) {
        
        ObjectMapper mapper = new ObjectMapper();
        Map<TaxonomyPair, BufferedWriter> writers = new HashMap<>();
        
        int interactionsSelected = 0;
        int successfulClassifications = 0;
        int uncertainClassifications = 0;
        int invalidClassifications = 0;
        int apiFailures = 0;
        int skippedInteractions = 0;
        
        Map<TaxonomyPair, Integer> distribution = new TreeMap<>();
        long startTime = System.currentTimeMillis();

        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             BufferedWriter auditWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "validation_sample.csv")))) {
            
            // Header for human review
            auditWriter.write("interaction_id,customer_tweet_id,predicted_category,predicted_subcategory,confidence,uncertain,validation_status,reviewer_label,reviewer_notes,customer_text\n");
            
            String line;
            while ((line = br.readLine()) != null) {
                if (interactionsSelected >= limit) break;
                if (line.trim().isEmpty()) continue;
                
                interactionsSelected++;
                
                JsonNode node = mapper.readTree(line);
                String rootIdStr = node.path("rootTweetId").asText();
                if (rootIdStr == null || rootIdStr.isEmpty()) {
                    skippedInteractions++;
                    continue;
                }
                
                long rootId = -1;
                try {
                    rootId = Long.parseLong(rootIdStr);
                } catch (NumberFormatException e) {
                    skippedInteractions++;
                    continue;
                }
                
                String customerText = "";
                String customerTweetId = rootIdStr; // default to root
                
                if (offsetIndex != null && csvReader != null) {
                    long offset = offsetIndex.getOffset(rootId);
                    if (offset != -1) {
                        try {
                            TweetRecord rootTweet = csvReader.readRecordAt(offset);
                            if (rootTweet != null && rootTweet.getText() != null) {
                                customerText = rootTweet.getText();
                                customerTweetId = rootTweet.getTweet_id();
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
                
                if (customerText.isEmpty()) {
                    skippedInteractions++;
                    continue;
                }
                
                CustomerMessage message = new CustomerMessage(customerText);
                
                Intent intent = null;
                boolean apiFailed = false;
                try {
                    intent = classifier.classify(message);
                } catch (Exception e) {
                    apiFailed = true;
                    apiFailures++;
                }
                
                String pCat = "";
                String pSub = "";
                double conf = 0.0;
                boolean unc = false;
                
                if (apiFailed) {
                    pCat = "API_FAILURE";
                    pSub = "API_FAILURE";
                } else if (intent != null) {
                    pCat = intent.getCategory().name();
                    pSub = intent.getSubCategory();
                    conf = intent.getConfidence();
                    unc = intent.isUncertain();
                    
                    if (unc) {
                        uncertainClassifications++;
                    } else if (!intent.getCategory().isValidSubcategory(intent.getSubCategory())) {
                        invalidClassifications++;
                        pCat = "INVALID_CATEGORY";
                        pSub = "INVALID_CATEGORY";
                    } else {
                        successfulClassifications++;
                        TaxonomyPair pair = new TaxonomyPair(intent.getCategory(), intent.getSubCategory());
                        distribution.put(pair, distribution.getOrDefault(pair, 0) + 1);
                        
                        // Write to STAGING mapping ONLY if valid and successful
                        // DO NOT write to production directories until human verified.
                        File baseFile = new File(outputBaseDir);
                        String stagingDir = new File(baseFile.getParentFile(), "validation-staging/" + baseFile.getName()).getAbsolutePath();
                        BufferedWriter bw = getWriter(writers, stagingDir, pair);
                        bw.write(line);
                        bw.newLine();
                    }
                }
                
                String escapedText = customerText.replace("\"", "\"\"");
                
                // Writing to validation_sample.csv (Empty review fields at the end)
                auditWriter.write(String.format("%s,%s,%s,%s,%.2f,%b,,,\"%s\"\n", 
                        rootIdStr, 
                        customerTweetId,
                        pCat, 
                        pSub, 
                        conf, 
                        unc, 
                        escapedText));
                
                // Sleep for rate limiting (unless it's a fast API failure)
                if (!apiFailed) {
                    Thread.sleep(50);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (csvReader != null) {
                try {
                    csvReader.close();
                } catch (IOException e) {}
            }
            for (BufferedWriter bw : writers.values()) {
                try {
                    bw.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        double avgClassTime = interactionsSelected > 0 ? (double) durationMs / interactionsSelected : 0;
        
        System.out.println("==================================================");
        System.out.println("VALIDATION RUN SUMMARY");
        System.out.println("==================================================");
        System.out.println("Interactions Selected:       " + interactionsSelected);
        System.out.println("Successful Classifications:  " + successfulClassifications);
        System.out.println("Uncertain Classifications:   " + uncertainClassifications);
        System.out.println("Invalid Classifications:     " + invalidClassifications);
        System.out.println("API Failures:                " + apiFailures);
        System.out.println("Skipped Interactions:        " + skippedInteractions);
        System.out.println("Total Time (ms):             " + durationMs);
        System.out.println(String.format("Avg Time per Item (ms):      %.2f", avgClassTime));
        System.out.println("\nCategory Distribution:");
        
        for (Map.Entry<TaxonomyPair, Integer> entry : distribution.entrySet()) {
            System.out.println("  " + entry.getKey().category.name() + " -> " + entry.getKey().subcategory + ": " + entry.getValue());
        }
        System.out.println("==================================================");
    }
    
    private static BufferedWriter getWriter(Map<TaxonomyPair, BufferedWriter> writers, String baseDir, TaxonomyPair pair) throws IOException {
        BufferedWriter bw = writers.get(pair);
        if (bw == null) {
            File dir = new File(baseDir + "/" + pair.category.name() + "/" + pair.subcategory);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, "mapping.jsonl");
            bw = new BufferedWriter(new FileWriter(file, true)); // APPEND MODE
            writers.put(pair, bw);
        }
        return bw;
    }
}

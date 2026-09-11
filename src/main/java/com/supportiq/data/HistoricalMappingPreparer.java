package com.supportiq.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.service.IntentClassifier;
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
            apiKey = System.getenv("GEMINI_API_KEY");
        }
        
        // If testing locally without an API key, we might mock it or just fail gracefully.
        boolean hasApiKey = apiKey != null && !apiKey.isEmpty();
        if (!hasApiKey) {
            System.err.println("WARNING: No API key provided (-Dsupportiq.classifier.api-key or GEMINI_API_KEY). Validation cannot perform real LLM calls.");
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

        String model = System.getProperty("supportiq.classifier.model", "gemini-3.6-flash");

        LlmIntentClassifier classifier = new LlmIntentClassifier(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
                apiKey,
                model,
                0.6
        );

        runValidation(inputPath, outputBaseDir, limit, classifier, csvReader, offsetIndex);
    }
    
    public static void runValidation(String inputPath, String outputBaseDir, int limit, 
                                     IntentClassifier classifier, 
                                     CsvOffsetReader csvReader, TweetOffsetIndex offsetIndex) {
        
        File baseFile = new File(outputBaseDir);
        
        // If limit is -1 (full run), write directly to production, else use staging
        boolean isFullRun = (limit == -1);
        File stagingDirFile = isFullRun ? baseFile : new File(baseFile.getParentFile(), "validation-staging/" + baseFile.getName());
        
        // Task 7: Rerun Safety - clear the target directory before starting to prevent duplication
        if (stagingDirFile.exists()) {
            try {
                // Do NOT delete the entire directory if it's the base directory (it contains intermediate_paths.jsonl)
                // Just delete taxonomy subdirectories (where mapping.jsonl lives) and audit files
                java.nio.file.Files.list(stagingDirFile.toPath()).forEach(p -> {
                    File f = p.toFile();
                    if (f.isDirectory() || f.getName().startsWith("audit_") || f.getName().equals("mapping_audit.json") || f.getName().equals("validation_sample.csv")) {
                        try {
                            if (f.isDirectory()) {
                                java.nio.file.Files.walk(f.toPath())
                                    .sorted(java.util.Comparator.reverseOrder())
                                    .map(java.nio.file.Path::toFile)
                                    .forEach(File::delete);
                            } else {
                                f.delete();
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                });
            } catch (IOException e) {
                System.err.println("Failed to clean target directory: " + e.getMessage());
            }
        }
        
        ObjectMapper mapper = new ObjectMapper();
        Map<TaxonomyPair, BufferedWriter> writers = new HashMap<>();
        
        int interactionsSelected = 0;
        int successfulClassifications = 0;
        int uncertainClassifications = 0;
        int unmappedClassifications = 0;
        int invalidClassifications = 0;
        int apiFailures = 0;
        int skippedInteractions = 0;
        int duplicatePaths = 0;
        
        java.util.Set<Long> processedRootIds = new java.util.HashSet<>();
        
        Map<TaxonomyPair, Integer> distribution = new TreeMap<>();
        long startTime = System.currentTimeMillis();

        if (!baseFile.exists()) {
            baseFile.mkdirs();
        }

        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             BufferedWriter auditWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "validation_sample.csv")));
             BufferedWriter uncertainWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "audit_uncertain.jsonl")));
             BufferedWriter unmappedWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "audit_unmapped.jsonl")));
             BufferedWriter invalidWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "audit_invalid.jsonl")));
             BufferedWriter apiFailureWriter = new BufferedWriter(new FileWriter(new File(outputBaseDir, "audit_api_failure.jsonl")))) {
            
            // Header for human review
            auditWriter.write("interaction_id,customer_tweet_id,predicted_category,predicted_subcategory,confidence,uncertain,validation_status,reviewer_label,reviewer_notes,customer_text\n");
            
            String line;
            while ((line = br.readLine()) != null) {
                if (limit != -1 && interactionsSelected >= limit) break;
                if (line.trim().isEmpty()) continue;
                
                interactionsSelected++;
                
                // Logging progress every 50 items (Task 6)
                if (interactionsSelected % 50 == 0) {
                    System.out.println("Processed " + interactionsSelected + "/" + limit);
                }
                
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
                
                if (!processedRootIds.add(rootId)) {
                    // Duplicate protection
                    duplicatePaths++;
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
                } catch (IllegalStateException e) {
                    System.err.println("Fatal configuration error: " + e.getMessage());
                    throw e; // Fail fast for 404 or missing API key
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
                    apiFailureWriter.write(line);
                    apiFailureWriter.newLine();
                } else if (intent == null) {
                    pCat = "UNMAPPED";
                    pSub = "UNMAPPED";
                    unmappedClassifications++;
                    unmappedWriter.write(line);
                    unmappedWriter.newLine();
                } else {
                    pCat = intent.getCategory().name();
                    pSub = intent.getSubCategory();
                    conf = intent.getConfidence();
                    unc = intent.isUncertain();
                    
                    if (unc) {
                        uncertainClassifications++;
                        uncertainWriter.write(line);
                        uncertainWriter.newLine();
                    } else if (!intent.getCategory().isValidSubcategory(intent.getSubCategory())) {
                        invalidClassifications++;
                        pCat = "INVALID_CATEGORY";
                        pSub = "INVALID_CATEGORY";
                        invalidWriter.write(line);
                        invalidWriter.newLine();
                    } else {
                        successfulClassifications++;
                        TaxonomyPair pair = new TaxonomyPair(intent.getCategory(), intent.getSubCategory());
                        distribution.put(pair, distribution.getOrDefault(pair, 0) + 1);
                        
                        // Write to staging or production mapping ONLY if valid and successful
                        String stagingDir = stagingDirFile.getAbsolutePath();
                        BufferedWriter bw = getWriter(writers, stagingDir, pair);
                        bw.write(line); // Writes EXACT paths array, no fragmentation, no text
                        bw.newLine();
                    }
                }
                
                String escapedText = customerText.replace("\"", "\"\"");
                
                // Writing to validation_sample.csv (Empty review fields at the end)
                // Fields: 
                // 1. interaction_id (%s)
                // 2. customer_tweet_id (%s)
                // 3. predicted_category (%s)
                // 4. predicted_subcategory (%s)
                // 5. confidence (%.2f)
                // 6. uncertain (%b)
                // 7. validation_status (empty)
                // 8. reviewer_label (empty)
                // 9. reviewer_notes (empty)
                // 10. customer_text ("%s")
                auditWriter.write(String.format("%s,%s,%s,%s,%.2f,%b,,,,\"%s\"\n", 
                        rootIdStr, 
                        customerTweetId,
                        pCat, 
                        pSub, 
                        conf, 
                        unc, 
                        escapedText));
                
                // Sleep for rate limiting ONLY if we are using an API-based classifier
                if (!apiFailed && (classifier instanceof com.supportiq.service.LlmIntentClassifier)) {
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
        System.out.println("Unmapped Classifications:    " + unmappedClassifications);
        System.out.println("Invalid Classifications:     " + invalidClassifications);
        System.out.println("Duplicate Interactions:      " + duplicatePaths);
        System.out.println("API Failures:                " + apiFailures);
        System.out.println("Skipped Interactions:        " + skippedInteractions);
        System.out.println("Total Time (ms):             " + durationMs);
        System.out.println(String.format("Avg Time per Item (ms):      %.2f", avgClassTime));
        System.out.println("\nCategory Distribution:");
        
        for (Map.Entry<TaxonomyPair, Integer> entry : distribution.entrySet()) {
            System.out.println("  " + entry.getKey().category.name() + " -> " + entry.getKey().subcategory + ": " + entry.getValue());
        }
        System.out.println("==================================================");
        
        // Write mapping_audit.json
        try {
            Map<String, Object> auditJson = new HashMap<>();
            auditJson.put("total_candidates", interactionsSelected);
            auditJson.put("confidently_mapped", successfulClassifications);
            auditJson.put("unmapped", unmappedClassifications);
            auditJson.put("ambiguous", uncertainClassifications);
            auditJson.put("duplicate_paths", duplicatePaths);
            auditJson.put("missing_ids", skippedInteractions);
            auditJson.put("api_failures", apiFailures);
            auditJson.put("runtime_ms", durationMs);
            
            Map<String, Integer> distMap = new TreeMap<>();
            for (Map.Entry<TaxonomyPair, Integer> entry : distribution.entrySet()) {
                distMap.put(entry.getKey().category.name() + "->" + entry.getKey().subcategory, entry.getValue());
            }
            auditJson.put("taxonomy_distribution", distMap);
            
            File auditJsonFile = new File(outputBaseDir, "mapping_audit.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(auditJsonFile, auditJson);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

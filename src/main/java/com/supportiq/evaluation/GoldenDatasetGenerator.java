package com.supportiq.evaluation;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.service.HistoricalRetrievalService;
import com.supportiq.service.HistoricalRetrievalServiceImpl;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GoldenDatasetGenerator {
    
    public static void main(String[] args) throws Exception {
        String workingCsv = "data/working/AmazonHelp/amazonhelp_relevant_tweets.csv";
        String mappingBaseDir = "data/mapping/AmazonHelp";
        Path outCsv = Paths.get("data/evaluation/golden_dataset.csv");
        
        if (!Files.exists(Paths.get(workingCsv))) {
            System.err.println("Working dataset not found at " + workingCsv);
            return;
        }
        
        Files.createDirectories(outCsv.getParent());
        
        System.out.println("Scanning for candidate tweets via validated mappings...");
        HistoricalRetrievalService retrievalService = new HistoricalRetrievalServiceImpl(workingCsv, mappingBaseDir);
        
        List<GoldenExample> candidates = new ArrayList<>();
        
        // Distribute across 20 categories
        for (IntentTaxonomy category : IntentTaxonomy.values()) {
            for (String subcat : category.getSubcategories()) {
                // Request up to 15 per subcategory to ensure we get enough
                List<HistoricalConversation> convs = retrievalService.retrieve(category, subcat, 15);
                for (HistoricalConversation conv : convs) {
                    GoldenExample ex = new GoldenExample();
                    ex.sourceTweetId = conv.getRootTweetId();
                    ex.expectedIntent = category.name();
                    ex.expectedSubcategory = subcat;
                    
                    // The first response from AmazonHelp is the reference
                    if (conv.getPaths() != null && !conv.getPaths().isEmpty() && conv.getPaths().get(0).size() >= 2) {
                        TweetRecord agentResponse = conv.getPaths().get(0).get(1);
                        ex.humanResponseReference = agentResponse.getText().replace("\"", "\"\"");
                    } else {
                        ex.humanResponseReference = "";
                    }
                    candidates.add(ex);
                }
            }
        }
        
        System.out.println("Found " + candidates.size() + " potential candidates across taxonomy. Sampling up to 250...");
        
        // Deduplicate by source tweet id
        List<GoldenExample> uniqueCandidates = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();
        for (GoldenExample ex : candidates) {
            if (!seenIds.contains(ex.sourceTweetId)) {
                seenIds.add(ex.sourceTweetId);
                uniqueCandidates.add(ex);
            }
        }
        
        Random rnd = new Random(42); // deterministic seed
        List<GoldenExample> sampled = new ArrayList<>();
        while (sampled.size() < 250 && !uniqueCandidates.isEmpty()) {
            int idx = rnd.nextInt(uniqueCandidates.size());
            sampled.add(uniqueCandidates.remove(idx));
        }
        
        // Write using safe CSV formatting
        try (BufferedWriter writer = Files.newBufferedWriter(outCsv)) {
            writer.write("example_id,source_tweet_id,expected_intent,expected_subcategory,expected_escalation_decision,expected_escalation_reason,human_response_reference,annotator_id,annotation_timestamp,annotator_b_intent,annotator_b_decision,annotator_b_reason\n");
            
            int count = 1;
            for (GoldenExample ex : sampled) {
                // Write safely. Only reference text needs quotes, but let's be safe.
                String safeRef = "\"" + ex.humanResponseReference + "\"";
                String line = String.format("EVAL_%03d,%s,%s,%s,,,%s,PENDING,,,,,", 
                        count, ex.sourceTweetId, ex.expectedIntent, ex.expectedSubcategory, safeRef);
                writer.write(line + "\n");
                count++;
            }
        }
        
        System.out.println("Golden dataset template created at " + outCsv.toAbsolutePath());
    }
    
    private static class GoldenExample {
        String sourceTweetId;
        String expectedIntent;
        String expectedSubcategory;
        String humanResponseReference;
    }
}

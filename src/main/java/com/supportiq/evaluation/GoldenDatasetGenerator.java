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
        Path reportFile = Paths.get("data/evaluation/golden_dataset_candidate_report.md");
        
        if (!Files.exists(Paths.get(workingCsv))) {
            System.err.println("Working dataset not found at " + workingCsv);
            return;
        }
        
        long linesCount = Files.lines(Paths.get(workingCsv)).count();
        if (linesCount < 100) {
            System.err.println("ERROR: The current repository only contains the mock fixture (" + linesCount + " rows). Cannot resolve real mapped IDs.");
            System.err.println("FAIL CLEARLY rather than fabricating or silently generating fake candidates.");
            return;
        }
        
        Files.createDirectories(outCsv.getParent());
        
        System.out.println("Scanning for candidate tweets via validated mappings...");
        HistoricalRetrievalService retrievalService = new HistoricalRetrievalServiceImpl(workingCsv, mappingBaseDir);
        
        List<GoldenExample> candidates = new ArrayList<>();
        int skippedCount = 0;
        
        // Distribute across 20 categories
        for (IntentTaxonomy category : IntentTaxonomy.values()) {
            for (String subcat : category.getSubcategories()) {
                // Request up to 30 per subcategory to ensure we get enough for a 400 candidate pool
                List<HistoricalConversation> convs = retrievalService.retrieve(category, subcat, 30);
                for (HistoricalConversation conv : convs) {
                    GoldenExample ex = new GoldenExample();
                    ex.sourceTweetId = conv.getRootTweetId();
                    ex.candidateSourceCategory = category.name();
                    ex.candidateSourceSubcategory = subcat;
                    ex.conversationRootId = conv.getRootTweetId();
                    
                    if (conv.getPaths() != null && !conv.getPaths().isEmpty() && conv.getPaths().get(0).size() >= 1) {
                        TweetRecord rootMessage = conv.getPaths().get(0).get(0);
                        if (rootMessage != null && rootMessage.isInbound() && rootMessage.getText() != null && !rootMessage.getText().trim().isEmpty()) {
                            ex.customerMessage = rootMessage.getText().replace("\"", "\"\"");
                            candidates.add(ex);
                        } else {
                            skippedCount++;
                        }
                    } else {
                        skippedCount++;
                    }
                }
            }
        }
        
        System.out.println("Found " + candidates.size() + " potential candidates across taxonomy. (Skipped " + skippedCount + ")");
        
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
        while (sampled.size() < 400 && !uniqueCandidates.isEmpty()) {
            int idx = rnd.nextInt(uniqueCandidates.size());
            sampled.add(uniqueCandidates.remove(idx));
        }
        
        // Write using safe CSV formatting
        try (BufferedWriter writer = Files.newBufferedWriter(outCsv)) {
            writer.write("example_id,source_tweet_id,customer_message,human_gold_intent,human_gold_subcategory,human_gold_escalation_decision,human_gold_escalation_reason,annotator_id,annotation_timestamp,annotation_status,conversation_root_id,candidate_source_category,candidate_source_subcategory,annotator_b_intent\n");
            
            int count = 1;
            for (GoldenExample ex : sampled) {
                // Write safely. Only reference text needs quotes, but let's be safe.
                String safeMsg = "\"" + ex.customerMessage + "\"";
                String line = String.format("EVAL_%03d,%s,%s,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,PENDING,%s,%s,%s,PENDING", 
                        count, ex.sourceTweetId, safeMsg, ex.conversationRootId, ex.candidateSourceCategory, ex.candidateSourceSubcategory);
                writer.write(line + "\n");
                count++;
            }
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(reportFile)) {
            writer.write("# Golden Dataset Candidate Report\n\n");
            writer.write("- Source dataset used: " + workingCsv + "\n");
            writer.write("- Total candidates considered: " + candidates.size() + "\n");
            writer.write("- Skipped candidates: " + skippedCount + " (missing root message or empty)\n");
            writer.write("- Selected candidate pool size: " + sampled.size() + "\n");
            writer.write("- Duplicate source_tweet_ids removed: " + (candidates.size() - (sampled.size() + uniqueCandidates.size())) + "\n");
            writer.write("- Deterministic sampling: YES (Seed = 42)\n");
            writer.write("- Gold fields: PENDING (Provenance preserved in metadata columns)\n");
            writer.write("- Original TWCS unchanged: YES\n");
        }
        
        System.out.println("Golden dataset candidate pool created at " + outCsv.toAbsolutePath());
    }
    
    private static class GoldenExample {
        String sourceTweetId;
        String customerMessage;
        String conversationRootId;
        String candidateSourceCategory;
        String candidateSourceSubcategory;
    }
}

package com.supportiq.evaluation;

import com.supportiq.data.CsvReader;
import com.supportiq.data.TweetRecord;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GoldenDatasetGenerator {
    
    public static void main(String[] args) throws Exception {
        Path rawCsv = Paths.get("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv");
        Path outCsv = Paths.get("data/evaluation/golden_dataset.csv");
        
        if (!Files.exists(rawCsv)) {
            System.err.println("Working dataset not found at " + rawCsv);
            return;
        }
        
        Files.createDirectories(outCsv.getParent());
        
        System.out.println("Scanning for candidate tweets...");
        List<TweetRecord> candidates = new ArrayList<>();
        try (CsvReader reader = CsvReader.read(rawCsv)) {
            while (reader.hasNext()) {
                TweetRecord r = reader.next();
                if (r.isInbound()) {
                    // Check if it's a first message (no in_response_to)
                    if (r.getIn_response_to_tweet_id() == null || r.getIn_response_to_tweet_id().trim().isEmpty()) {
                        candidates.add(r);
                        if (candidates.size() > 50000) break; // limit memory
                    }
                }
            }
        }
        
        System.out.println("Found " + candidates.size() + " potential candidates. Sampling 250...");
        Random rnd = new Random(42); // deterministic
        List<TweetRecord> sampled = new ArrayList<>();
        while (sampled.size() < 250 && !candidates.isEmpty()) {
            int idx = rnd.nextInt(candidates.size());
            sampled.add(candidates.remove(idx));
        }
        
        try (BufferedWriter writer = Files.newBufferedWriter(outCsv)) {
            writer.write("example_id,source_tweet_id,expected_intent,expected_subcategory,expected_escalation_decision,expected_escalation_reason,human_response_reference,annotator_id,annotation_timestamp,annotator_b_intent,annotator_b_decision,annotator_b_reason\n");
            
            int count = 1;
            for (TweetRecord r : sampled) {
                // Generated empty structure to be labelled by humans
                String line = String.format("EVAL_%03d,%s,,,,,,PENDING,,,,", count, r.getTweet_id());
                writer.write(line + "\n");
                count++;
            }
        }
        
        System.out.println("Golden dataset template created at " + outCsv.toAbsolutePath());
    }
}

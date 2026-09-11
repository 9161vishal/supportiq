package com.supportiq.data;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class IntentDiscoveryAnalyzerTest {

    @Test
    public void testDiscoveryAnalyzer() throws Exception {
        Path tempDir = Files.createTempDirectory("discovery-test");
        Path tempCsv = tempDir.resolve("test_twcs.csv");
        Path tempJsonl = tempDir.resolve("intermediate_paths.jsonl");
        Path outDir = tempDir.resolve("analysis");

        String csvContent = "tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id\n"
            + "1,CustomerA,True,1,Where is my delivery?,, \n" // Match: DELIVERY_AND_TRACKING
            + "2,CustomerB,True,2,My package arrived broken and I want a refund.,, \n" // Match: PRODUCT_PROBLEM, RETURNS_AND_REFUNDS (Ambiguous)
            + "3,CustomerC,True,3,The sky is blue,, \n" // Uncovered
            + "4,AmazonHelp,False,4,Let us check,,1\n";
            
        Files.writeString(tempCsv, csvContent);
        
        String jsonlContent = "{\"rootTweetId\":\"1\",\"paths\":[[\"1\",\"4\"]]}\n"
            + "{\"rootTweetId\":\"2\",\"paths\":[[\"2\"]]}\n"
            + "{\"rootTweetId\":\"3\",\"paths\":[[\"3\"]]}\n"
            + "{\"rootTweetId\":\"999\",\"paths\":[[\"999\"]]}\n"; // Missing ID
            
        Files.writeString(tempJsonl, jsonlContent);
        
        IntentDiscoveryAnalyzer analyzer = new IntentDiscoveryAnalyzer(tempCsv, tempJsonl, outDir.toString(), -1);
        analyzer.run();
        
        Path summaryFile = outDir.resolve("summary_report.txt");
        assertTrue(Files.exists(summaryFile), "Summary report must be created");
        
        String summary = Files.readString(summaryFile);
        assertTrue(summary.contains("Total Interactions Analyzed: 4"));
        assertTrue(summary.contains("Valid Initial Customer Messages Found: 3"));
        assertTrue(summary.contains("Messages with Missing/Unresolvable IDs: 1"));
        
        Path categoryFile = outDir.resolve("taxonomy_evidence.json");
        assertTrue(Files.exists(categoryFile));
        String categoryJson = Files.readString(categoryFile);
        assertTrue(categoryJson.contains("DELIVERY_AND_TRACKING"));
        assertTrue(categoryJson.contains("Where is my delivery?"));
        
        Path uncoveredFile = outDir.resolve("ambiguous_and_uncovered.json");
        assertTrue(Files.exists(uncoveredFile));
        String uncoveredJson = Files.readString(uncoveredFile);
        assertTrue(uncoveredJson.contains("The sky is blue"));
        assertTrue(uncoveredJson.contains("My package arrived broken and I want a refund"));
    }
}

package com.supportiq.evaluation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

class GoldenDatasetTest {

    @Test
    void testGoldenDatasetIntegrity() throws Exception {
        Path goldenCsvPath = Paths.get("data/evaluation/golden_dataset.csv");
        if (!Files.exists(goldenCsvPath)) return;
        
        List<String> lines = Files.readAllLines(goldenCsvPath);
        assertTrue(lines.size() > 0, "Golden dataset should have at least a header");
        
        String header = lines.get(0);
        assertTrue(header.contains("example_id"));
        assertTrue(header.contains("source_tweet_id"));
        assertTrue(header.contains("human_gold_intent"));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = parseCsvLine(lines.get(i));
            assertEquals(14, parts.length, "Row should have exactly 14 columns");
            assertFalse(parts[0].trim().isEmpty(), "example_id cannot be empty");
            assertFalse(parts[1].trim().isEmpty(), "source_tweet_id cannot be empty");
            
            // Check for customer_message
            assertFalse(parts[2].trim().isEmpty(), "customer_message cannot be empty");
            assertFalse(parts[2].contains("Mock message for"), "customer_message must not be synthetic");
            
            // Assert all gold truth is PENDING
            assertEquals("PENDING", parts[3], "human_gold_intent must be PENDING");
            assertEquals("PENDING", parts[4], "human_gold_subcategory must be PENDING");
        }
    }
    
    private String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }
    
    @Test
    void testJudgeResponseParsing() {
        LlmJudge.JudgeResult res = new LlmJudge().evaluate(new com.supportiq.model.CustomerMessage("test"), new com.supportiq.model.SupportResponse(null, null, null, null));
        assertFalse(res.success);
    }
}

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
        assertTrue(header.contains("expected_intent"));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(",", -1);
            assertEquals(12, parts.length, "Row should have exactly 12 columns");
            assertFalse(parts[0].trim().isEmpty(), "example_id cannot be empty");
            assertFalse(parts[1].trim().isEmpty(), "source_tweet_id cannot be empty");
        }
    }
    
    @Test
    void testJudgeResponseParsing() {
        LlmJudge.JudgeResult res = new LlmJudge().evaluate(null, null);
        assertFalse(res.success);
    }
}

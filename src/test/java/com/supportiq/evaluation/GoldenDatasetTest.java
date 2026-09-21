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
        assertTrue(Files.exists(goldenCsvPath), "Golden dataset file must exist");
        
        List<String> lines = Files.readAllLines(goldenCsvPath);
        assertTrue(lines.size() > 0, "Golden dataset should have at least a header");
        
        String header = lines.get(0);
        assertTrue(header.contains("example_id"));
        assertTrue(header.contains("source_tweet_id"));
        assertTrue(header.contains("expected_intent"));
        assertTrue(header.contains("expected_subcategory"));
        assertTrue(header.contains("expected_escalation_decision"));
        assertTrue(header.contains("expected_escalation_reason"));
        assertTrue(header.contains("human_response_reference"));
        assertTrue(header.contains("annotator_id"));
        assertTrue(header.contains("annotation_timestamp"));
        assertTrue(header.contains("annotator_b_intent"));
        assertTrue(header.contains("annotator_b_decision"));
        assertTrue(header.contains("annotator_b_reason"));
        
        for (int i = 1; i < lines.size(); i++) {
            String[] parts = parseCsvLine(lines.get(i));
            assertEquals(12, parts.length, "Row should have exactly 12 columns");
            assertFalse(parts[0].trim().isEmpty(), "example_id cannot be empty");
            assertFalse(parts[1].trim().isEmpty(), "source_tweet_id cannot be empty");
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

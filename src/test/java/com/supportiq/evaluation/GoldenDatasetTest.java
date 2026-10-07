package com.supportiq.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GoldenDatasetTest {

    @Test
    void testGoldenDatasetIntegrity() throws Exception {
        Path goldenJsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        assertTrue(Files.exists(goldenJsonlPath), "Golden dataset JSONL file must exist");
        
        List<String> lines = Files.readAllLines(goldenJsonlPath);
        assertTrue(lines.size() >= 150 && lines.size() <= 200, "Golden dataset should have between 150 and 200 cases");
        
        ObjectMapper mapper = new ObjectMapper();
        Set<String> ids = new HashSet<>();
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            JsonNode node = mapper.readTree(line);
            
            // Check required fields
            assertTrue(node.has("id"), "Record must have an id");
            assertTrue(node.has("pair"), "Record must have a pair");
            
            // Ensure no extra fields at root
            assertEquals(2, node.size(), "Record must have exactly 2 root fields (id, pair)");
            
            JsonNode pair = node.get("pair");
            assertTrue(pair.has("query"), "pair must have a query");
            assertTrue(pair.has("humanAnswer"), "pair must have a humanAnswer");
            
            // Ensure no extra fields in pair (now 3 fields: query, humanAnswer, expectedIntent)
            assertEquals(3, pair.size(), "pair must have exactly 3 fields (query, humanAnswer, expectedIntent)");
            assertTrue(pair.has("expectedIntent"), "pair must have an expectedIntent");
            
            String id = node.get("id").asText();
            assertFalse(id.trim().isEmpty(), "id cannot be empty");
            assertTrue(ids.add(id), "Duplicate id found: " + id);
            
            String query = pair.get("query").asText();
            assertFalse(query.trim().isEmpty(), "query cannot be empty");
            
            String humanAnswer = pair.get("humanAnswer").asText();
            assertFalse(humanAnswer.trim().isEmpty(), "humanAnswer cannot be empty");
            
            String expectedIntent = pair.get("expectedIntent").asText();
            assertFalse(expectedIntent.trim().isEmpty(), "expectedIntent cannot be empty");
            
            try {
                com.supportiq.data.IntentTaxonomy.valueOf(expectedIntent);
            } catch (IllegalArgumentException e) {
                fail("expectedIntent is not a valid taxonomy category: " + expectedIntent);
            }
        }
    }
}

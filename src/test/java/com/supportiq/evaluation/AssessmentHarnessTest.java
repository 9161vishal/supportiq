package com.supportiq.evaluation;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentHarnessTest {

    @Test
    void testGoldenDatasetValidation() throws Exception {
        Path jsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        assertTrue(Files.exists(jsonlPath), "Golden dataset must exist");
        
        List<String> lines = Files.readAllLines(jsonlPath);
        int count = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                count++;
                assertTrue(line.contains("\"id\""), "Must have ID");
                assertTrue(line.contains("\"query\""), "Must have query");
                assertTrue(line.contains("\"humanAnswer\""), "Must have humanAnswer");
            }
        }
        assertTrue(count >= 150 && count <= 250, "Dataset count must be 150-250");
    }

    @Test
    void testNoDuplicateRawDatasetGeneration() {
        Path newCsvPath = Paths.get("data/evaluation/twcs.csv");
        assertFalse(Files.exists(newCsvPath), "Must not duplicate raw dataset");
    }

    @Test
    void testNonWebStartup() throws Exception {
        Path harnessPath = Paths.get("src/main/java/com/supportiq/evaluation/AssessmentHarness.java");
        if (Files.exists(harnessPath)) {
            String content = Files.readString(harnessPath);
            assertTrue(content.contains("WebApplicationType.NONE"), 
                "AssessmentHarness must set WebApplicationType.NONE to avoid port 8080 conflicts");
        }
    }
}

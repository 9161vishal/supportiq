package com.supportiq.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaxonomyEvidenceTest {

    private Path tempCsvPath;
    private Path tempPathsJsonl;
    private Path tempOutputDir;

    @BeforeEach
    void setUp() throws Exception {
        tempCsvPath = Files.createTempFile("test_twcs", ".csv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempCsvPath.toFile()))) {
            bw.write("tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id\n");
            bw.write("100,Customer1,True,time,Where is my tracking number update?,200,\n");
            bw.write("300,Customer2,True,time,Cancel my order,400,\n");
        }

        tempPathsJsonl = Files.createTempFile("test_intermediate", ".jsonl");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempPathsJsonl.toFile()))) {
            bw.write("{\"rootTweetId\":\"100\",\"paths\":[[\"100\",\"200\"]]}\n");
            bw.write("{\"rootTweetId\":\"300\",\"paths\":[[\"300\",\"400\"]]}\n");
        }

        tempOutputDir = Files.createTempDirectory("test_reports");
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempCsvPath);
        Files.deleteIfExists(tempPathsJsonl);
        Files.walk(tempOutputDir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    @Test
    void testDiscoveryAnalyzerGeneratesEvidenceFiles() throws Exception {
        IntentDiscoveryAnalyzer analyzer = new IntentDiscoveryAnalyzer(
                tempCsvPath,
                tempPathsJsonl,
                tempOutputDir.toString(),
                2 // limit
        );

        analyzer.run();

        // Check summary report
        File summary = new File(tempOutputDir.toFile(), "summary_report.txt");
        assertTrue(summary.exists(), "summary_report.txt should be generated");

        // Check taxonomy evidence
        File evidence = new File(tempOutputDir.toFile(), "taxonomy_evidence.json");
        assertTrue(evidence.exists(), "taxonomy_evidence.json should be generated");
        List<String> evidenceLines = Files.readAllLines(evidence.toPath());
        String evidenceContent = String.join("\n", evidenceLines);
        assertTrue(evidenceContent.contains("DELIVERY_AND_TRACKING"));
        assertTrue(evidenceContent.contains("TRACKING_NOT_UPDATED"));

        // Check taxonomy decision
        File decision = new File(tempOutputDir.toFile(), "taxonomy_decision.json");
        assertTrue(decision.exists(), "taxonomy_decision.json should be generated");
        List<String> decisionLines = Files.readAllLines(decision.toPath());
        String decisionContent = String.join("\n", decisionLines);
        assertTrue(decisionContent.contains("SUPPORTED") || decisionContent.contains("WEAK_EVIDENCE") || decisionContent.contains("MISSING_EVIDENCE"));
    }
}

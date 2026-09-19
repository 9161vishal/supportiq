package com.supportiq;

import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.HistoricalMappingPreparer;
import com.supportiq.data.MappingReconciliationValidator;
import com.supportiq.data.MappingQualitySampler;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.service.DeterministicIntentClassifier;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RunDeterministicMapping {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Phase 2B Step 3 Offline Deterministic Mapping...");

        String inputPath = "data/mapping/AmazonHelp/intermediate_paths.jsonl";
        String outputBaseDir = "data/mapping/AmazonHelp";
        String csvPathStr = "data/working/AmazonHelp/amazonhelp_relevant_tweets.csv";

        System.out.println("Building CSV offset index...");
        CsvOffsetReader csvReader = null;
        TweetOffsetIndex offsetIndex = null;
        try {
            Path csvPath = Paths.get(csvPathStr);
            if (csvPath.toFile().exists()) {
                csvReader = new CsvOffsetReader(csvPath);
                offsetIndex = csvReader.buildIndex();
            } else {
                System.out.println("CSV not found. Exiting.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Failed to build index: " + e.getMessage());
            System.exit(1);
        }

        DeterministicIntentClassifier classifier = new DeterministicIntentClassifier();

        // Run with limit = -1 for full dataset processing
        HistoricalMappingPreparer.runValidation(
                inputPath,
                outputBaseDir,
                -1,
                classifier,
                csvReader,
                offsetIndex);

        System.out.println("Deterministic Mapping Complete.");

        System.out.println("Running mapping reconciliation validation...");
        try {
            new MappingReconciliationValidator().validate("data/mapping/AmazonHelp", csvPathStr);
            new MappingQualitySampler().generateSampleReport("data/mapping/AmazonHelp", csvPathStr);
        } catch (Exception e) {
            System.err.println("Validation FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

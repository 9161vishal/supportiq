package com.supportiq;

import com.supportiq.data.AmazonHelpPipeline;
import com.supportiq.data.MappingReconciliationValidator;
import com.supportiq.data.TaxonomyEvidenceReporter;
import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.service.DeterministicIntentClassifier;
import com.supportiq.data.HistoricalMappingPreparer;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Formatter;

public class LockDataExtractionModule {

    private static String calculateSHA256(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        byte[] bytes = digest.digest();
        try (Formatter formatter = new Formatter()) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
            return formatter.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("STARTING LOCKED DATA EXTRACTION & MAPPING MODULE");
        System.out.println("==================================================");

        Path csvPath = Paths.get("data/raw/twcs.csv").toAbsolutePath();
        String outputDir = Paths.get("data/mapping/AmazonHelp").toAbsolutePath().toString();
        String workingCsv = Paths.get("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv").toAbsolutePath().toString();
        
        System.out.println("1. Verifying Source Immutability...");
        String initialHash = calculateSHA256(csvPath);
        System.out.println("Source SHA-256: " + initialHash);

        System.out.println("\n2. Executing Working Dataset Extraction (AmazonHelpPipeline)...");
        AmazonHelpPipeline pipeline = new AmazonHelpPipeline(csvPath, outputDir, workingCsv);
        pipeline.process();

        System.out.println("\n3. Building Offline Deterministic Mapping (RunDeterministicMapping equivalent)...");
        System.out.println("Building CSV offset index using Working Dataset...");
        CsvOffsetReader csvReader = new CsvOffsetReader(Paths.get(workingCsv));
        TweetOffsetIndex offsetIndex = csvReader.buildIndex();

        DeterministicIntentClassifier classifier = new DeterministicIntentClassifier();
        
        String inputPathsFile = "data/mapping/AmazonHelp/intermediate_paths.jsonl";
        HistoricalMappingPreparer.runValidation(
                inputPathsFile,
                outputDir,
                -1,
                classifier,
                csvReader,
                offsetIndex
        );

        System.out.println("\n4. Running Quality Gates and Reconciliations...");
        MappingReconciliationValidator validator = new MappingReconciliationValidator();
        validator.validate(outputDir, workingCsv);
        
        System.out.println("\n5. Generating Taxonomy Evidence Report...");
        TaxonomyEvidenceReporter taxonomyReporter = new TaxonomyEvidenceReporter();
        taxonomyReporter.generateReport(outputDir);

        System.out.println("\n6. Final Immutability Check...");
        String finalHash = calculateSHA256(csvPath);
        System.out.println("Final Source SHA-256: " + finalHash);
        if (!initialHash.equals(finalHash)) {
            System.err.println("FATAL ERROR: ORIGINAL CSV WAS MODIFIED!");
            System.exit(1);
        }

        System.out.println("\n==================================================");
        System.out.println("ALL QUALITY GATES PASSED.");
        System.out.println("DATA EXTRACTION + AMAZONHELP INTERACTION + MAPPING MODULE LOCKED.");
        System.out.println("==================================================");
    }
}

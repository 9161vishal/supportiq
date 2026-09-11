package com.supportiq.data;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.service.IntentClassifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class HistoricalMappingPreparerTest {

    private Path tempInputJsonl;
    private Path tempOutputDir;
    private IntentClassifier mockClassifier;
    private CsvOffsetReader mockReader;
    private TweetOffsetIndex mockIndex;

    @BeforeEach
    void setUp() throws Exception {
        tempInputJsonl = Files.createTempFile("test_intermediate", ".jsonl");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempInputJsonl.toFile()))) {
            // Write 3 fake roots, plus one duplicate
            bw.write("{\"rootTweetId\":\"100\",\"paths\":[[\"100\",\"101\"]]}\n");
            bw.write("{\"rootTweetId\":\"200\",\"paths\":[[\"200\",\"201\"]]}\n");
            bw.write("{\"rootTweetId\":\"300\",\"paths\":[[\"300\",\"301\"]]}\n");
            bw.write("{\"rootTweetId\":\"100\",\"paths\":[[\"100\",\"102\"]]}\n"); // Duplicate root ID
        }

        tempOutputDir = Files.createTempDirectory("test_output");
        
        mockClassifier = mock(IntentClassifier.class);
        mockReader = mock(CsvOffsetReader.class);
        mockIndex = mock(TweetOffsetIndex.class);

        // Setup mock index -> mock reader
        when(mockIndex.getOffset(100L)).thenReturn(1000L);
        when(mockIndex.getOffset(200L)).thenReturn(2000L);
        when(mockIndex.getOffset(300L)).thenReturn(3000L);

        TweetRecord r100 = new TweetRecord();
        r100.setTweet_id("100");
        r100.setText("Where is my package?");
        
        TweetRecord r200 = new TweetRecord();
        r200.setTweet_id("200");
        r200.setText("Echo is broken");
        
        TweetRecord r300 = new TweetRecord();
        r300.setTweet_id("300");
        r300.setText("Cancel my order please");

        when(mockReader.readRecordAt(1000L)).thenReturn(r100);
        when(mockReader.readRecordAt(2000L)).thenReturn(r200);
        when(mockReader.readRecordAt(3000L)).thenReturn(r300);
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempInputJsonl);
        Files.walk(tempOutputDir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
             
        File stagingDir = new File(tempOutputDir.toFile().getParentFile(), "validation-staging/" + tempOutputDir.toFile().getName());
        if (stagingDir.exists()) {
            Files.walk(stagingDir.toPath())
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }

    @Test
    void testValidationLimitAndMapping() throws Exception {
        // Intent 100 -> DELIVERY_LATE
        when(mockClassifier.classify(argThat(msg -> msg != null && msg.getText() != null && msg.getText().contains("package"))))
            .thenReturn(new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "DELIVERY_LATE", 0.9, false));
        
        // Intent 200 -> UNCERTAIN (should not map)
        when(mockClassifier.classify(argThat(msg -> msg != null && msg.getText() != null && msg.getText().contains("Echo"))))
            .thenReturn(new Intent(IntentTaxonomy.AMAZON_DEVICES, "ECHO_PROBLEM", 0.3, true));
        
        // Intent 300 -> CANCEL_ORDER
        when(mockClassifier.classify(argThat(msg -> msg != null && msg.getText() != null && msg.getText().contains("Cancel"))))
            .thenReturn(new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 0.8, false));

        // Limit is 3, process all
        HistoricalMappingPreparer.runValidation(
                tempInputJsonl.toString(),
                tempOutputDir.toString(),
                3,
                mockClassifier,
                mockReader,
                mockIndex
        );

        // Check audit log exists
        File auditFile = new File(tempOutputDir.toFile(), "validation_sample.csv");
        assertTrue(auditFile.exists());
        
        List<String> auditLines = Files.readAllLines(auditFile.toPath());
        assertEquals(4, auditLines.size()); // Header + 3 records (duplicate is skipped)
        assertTrue(auditLines.get(1).contains("100"));
        assertTrue(auditLines.get(1).contains("DELIVERY_LATE"));
        assertTrue(auditLines.get(2).contains("200"));
        assertTrue(auditLines.get(2).contains("true")); // uncertain
        
        // Check actual mappings (now in staging directory because limit != -1)
        File stagingDir = new File(tempOutputDir.toFile().getParentFile(), "validation-staging/" + tempOutputDir.toFile().getName());
        
        File delDir = new File(stagingDir, "DELIVERY_AND_TRACKING/DELIVERY_LATE/mapping.jsonl");
        assertTrue(delDir.exists());
        List<String> delLines = Files.readAllLines(delDir.toPath());
        assertEquals(1, delLines.size());
        assertTrue(delLines.get(0).contains("100"));
        
        File orderDir = new File(stagingDir, "ORDER_MANAGEMENT/CANCEL_ORDER/mapping.jsonl");
        assertTrue(orderDir.exists());
        List<String> orderLines = Files.readAllLines(orderDir.toPath());
        assertEquals(1, orderLines.size());
        assertTrue(orderLines.get(0).contains("300"));
        
        File echoDir = new File(stagingDir, "AMAZON_DEVICES/ECHO_PROBLEM/mapping.jsonl");
        assertFalse(echoDir.exists(), "Uncertain intents should NOT be mapped into staging directories.");
        
        // Check uncertain log exists
        File uncertainFile = new File(tempOutputDir.toFile(), "audit_uncertain.jsonl");
        assertTrue(uncertainFile.exists());
        List<String> uncertainLines = Files.readAllLines(uncertainFile.toPath());
        assertEquals(1, uncertainLines.size());
        assertTrue(uncertainLines.get(0).contains("200"));
        
        // Check mapping_audit.json exists
        File auditJson = new File(tempOutputDir.toFile(), "mapping_audit.json");
        assertTrue(auditJson.exists());
    }
    
    @Test
    void testUnmappedLogging() throws Exception {
        when(mockClassifier.classify(any(CustomerMessage.class)))
            .thenReturn(null);
            
        HistoricalMappingPreparer.runValidation(
                tempInputJsonl.toString(),
                tempOutputDir.toString(),
                1,
                mockClassifier,
                mockReader,
                mockIndex
        );

        File unmappedFile = new File(tempOutputDir.toFile(), "audit_unmapped.jsonl");
        assertTrue(unmappedFile.exists());
        List<String> unmappedLines = Files.readAllLines(unmappedFile.toPath());
        assertEquals(1, unmappedLines.size());
        assertTrue(unmappedLines.get(0).contains("100"));
    }
    
    @Test
    void testGeneralInformationIsValid() throws Exception {
        when(mockClassifier.classify(any(CustomerMessage.class)))
            .thenReturn(new Intent(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, "PRAISE_OR_APPRECIATION", 0.9, false));
            
        HistoricalMappingPreparer.runValidation(
                tempInputJsonl.toString(),
                tempOutputDir.toString(),
                1,
                mockClassifier,
                mockReader,
                mockIndex
        );

        File stagingDir = new File(tempOutputDir.toFile().getParentFile(), "validation-staging/" + tempOutputDir.toFile().getName());
        File generalDir = new File(stagingDir, "GENERAL_INFORMATION_AND_NON_SUPPORT/PRAISE_OR_APPRECIATION/mapping.jsonl");
        assertTrue(generalDir.exists());
    }
    @Test
    void testSampleLimitStrictness() throws Exception {
        when(mockClassifier.classify(any(CustomerMessage.class)))
            .thenReturn(new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "DELIVERY_LATE", 0.9, false));
            
        // Limit is 1, even though 3 items exist
        HistoricalMappingPreparer.runValidation(
                tempInputJsonl.toString(),
                tempOutputDir.toString(),
                1,
                mockClassifier,
                mockReader,
                mockIndex
        );

        File auditFile = new File(tempOutputDir.toFile(), "validation_sample.csv");
        List<String> auditLines = Files.readAllLines(auditFile.toPath());
        assertEquals(2, auditLines.size()); // Header + 1 record
    }
    
    @Test
    void testApiFailureLogging() throws Exception {
        when(mockClassifier.classify(any(CustomerMessage.class)))
            .thenThrow(new RuntimeException("API Timeout"));
            
        HistoricalMappingPreparer.runValidation(
                tempInputJsonl.toString(),
                tempOutputDir.toString(),
                1,
                mockClassifier,
                mockReader,
                mockIndex
        );

        File failureFile = new File(tempOutputDir.toFile(), "audit_api_failure.jsonl");
        assertTrue(failureFile.exists());
        List<String> failureLines = Files.readAllLines(failureFile.toPath());
        assertEquals(1, failureLines.size());
        assertTrue(failureLines.get(0).contains("100"));
    }
}

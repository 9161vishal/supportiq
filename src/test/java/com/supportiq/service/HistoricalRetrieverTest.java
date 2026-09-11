package com.supportiq.service;

import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.model.HistoricalCandidate;
import com.supportiq.model.Intent;
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

import static org.junit.jupiter.api.Assertions.*;

public class HistoricalRetrieverTest {

    private Path tempCsv;
    private Path tempMappingDir;
    private HistoricalRetriever retriever;
    private CsvOffsetReader csvReader;

    @BeforeEach
    void setUp() throws Exception {
        // Create synthetic CSV
        tempCsv = Files.createTempFile("test_twcs", ".csv");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempCsv.toFile()))) {
            bw.write("tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id\n");
            // Simple chain
            bw.write("1,customerA,True,date,\"Where is my package?\",2,\n");
            bw.write("2,AmazonHelp,False,date,\"It is late.\",3,1\n");
            bw.write("3,customerA,True,date,\"Thanks.\",,2\n");
            
            // Branching chain
            bw.write("10,customerB,True,date,\"Broken item\",11,\n");
            bw.write("11,AmazonHelp,False,date,\"Sorry about that.\",\"12,13\",10\n");
            bw.write("12,customerB,True,date,\"I want a refund\",,11\n");
            bw.write("13,customerB,True,date,\"Or a replacement\",,11\n");
            
            // Missing ID chain
            bw.write("20,customerC,True,date,\"Help\",21,\n");
            // 21 is missing
        }

        // Create synthetic mapping directory
        tempMappingDir = Files.createTempDirectory("test_mapping");
        
        File dirDelivery = new File(tempMappingDir.toFile(), "DELIVERY_AND_TRACKING/DELIVERY_LATE");
        dirDelivery.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dirDelivery, "mapping.jsonl")))) {
            // Write chain 1
            bw.write("{\"rootTweetId\":\"1\",\"paths\":[[\"1\",\"2\",\"3\"]]}\n");
            // Write missing ID chain
            bw.write("{\"rootTweetId\":\"20\",\"paths\":[[\"20\",\"21\"]]}\n");
        }
        
        File dirProduct = new File(tempMappingDir.toFile(), "PRODUCT_PROBLEM/DAMAGED_PRODUCT");
        dirProduct.mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(dirProduct, "mapping.jsonl")))) {
            // Write branching chain
            bw.write("{\"rootTweetId\":\"10\",\"paths\":[[\"10\",\"11\",\"12\"],[\"10\",\"11\",\"13\"]]}\n");
        }

        csvReader = new CsvOffsetReader(tempCsv);
        TweetOffsetIndex index = csvReader.buildIndex();
        
        retriever = new HistoricalRetriever(tempCsv.toString(), tempMappingDir.toString(), 10);
        retriever.setCsvOffsetReaderAndIndex(csvReader, index);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (csvReader != null) csvReader.close();
        Files.deleteIfExists(tempCsv);
        // Delete temp mapping dir
        Files.walk(tempMappingDir)
             .sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    @Test
    void testRetrieveValidCategorySubcategory() {
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "DELIVERY_LATE", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        
        // 1 valid path in DELIVERY_LATE (the missing ID path is skipped)
        assertEquals(1, candidates.size());
        HistoricalCandidate candidate = candidates.get(0);
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, candidate.getCategory());
        assertEquals("DELIVERY_LATE", candidate.getSubcategory());
        assertEquals(3, candidate.getInteractionPath().size());
        assertEquals("1", candidate.getInteractionPath().get(0).getTweet_id());
        assertEquals("2", candidate.getInteractionPath().get(1).getTweet_id());
        assertEquals("3", candidate.getInteractionPath().get(2).getTweet_id());
    }

    @Test
    void testRetrieveBranchingRelationship() {
        Intent intent = new Intent(IntentTaxonomy.PRODUCT_PROBLEM, "DAMAGED_PRODUCT", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        
        // 2 branches = 2 candidates
        assertEquals(2, candidates.size());
        assertEquals("12", candidates.get(0).getInteractionPath().get(2).getTweet_id());
        assertEquals("13", candidates.get(1).getInteractionPath().get(2).getTweet_id());
    }

    @Test
    void testRetrieveEmptyResultForMissingDirectory() {
        Intent intent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "CANCEL_ORDER", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void testRetrieveInvalidSubcategory() {
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "INVALID_SUB", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void testRetrieveMismatchedCategorySubcategory() {
        Intent intent = new Intent(IntentTaxonomy.ORDER_MANAGEMENT, "DELIVERY_LATE", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        assertTrue(candidates.isEmpty());
    }

    @Test
    void testMissingSourceIdIsSafelySkipped() {
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "DELIVERY_LATE", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        // The path [20, 21] is skipped because 21 is missing
        assertEquals(1, candidates.size()); 
    }

    @Test
    void testCandidateLimit() throws Exception {
        retriever = new HistoricalRetriever(tempCsv.toString(), tempMappingDir.toString(), 1);
        retriever.setCsvOffsetReaderAndIndex(csvReader, csvReader.buildIndex()); // recreate for limit
        
        Intent intent = new Intent(IntentTaxonomy.PRODUCT_PROBLEM, "DAMAGED_PRODUCT", 1.0, false);
        List<HistoricalCandidate> candidates = retriever.retrieve(intent);
        
        // Only 1 branch should be returned due to limit=1
        assertEquals(1, candidates.size());
    }
    
    @Test
    void testNullIntentSafelyHandled() {
        assertTrue(retriever.retrieve(null).isEmpty());
    }
}

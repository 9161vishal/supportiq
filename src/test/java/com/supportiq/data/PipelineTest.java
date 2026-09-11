package com.supportiq.data;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineTest {

    @Test
    public void testAmazonHelpPipeline() throws Exception {
        Path tempDir = Files.createTempDirectory("pipeline-test");
        Path tempCsv = tempDir.resolve("test_twcs.csv");
        
        // Construct a synthetic CSV testing all required scenarios.
        // Format: tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id
        
        String csvContent = "tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id\n"
            // Scenario 1: Direct AmazonHelp response (1 -> 2)
            + "1,CustomerA,True,1,Hello Amazon,, \n"
            + "2,AmazonHelp,False,2,Hi there!,3,1\n"
            
            // Scenario 2: Multi-turn (1 -> 2 -> 3 -> 4)
            + "3,CustomerA,True,3,My package is late.,4,2\n"
            + "4,AmazonHelp,False,4,Let me check.,,3\n"
            
            // Scenario 3: Unrelated support company branch (1 -> 5)
            + "5,AppleSupport,False,5,Are you talking to us?,,1\n" // This branch should be ignored
            
            // Scenario 4: Branching interaction (2 -> 6)
            + "6,CustomerB,True,6,I have a similar issue.,7,2\n"
            + "7,AmazonHelp,False,7,DM us.,,6\n"
            
            // Scenario 5: Sibling of AmazonHelp (1 -> 8), not part of AH interaction
            + "8,CustomerC,True,8,Me too.,,1\n"
            
            // Scenario 6: Missing parent (9 -> 10, but 9 is missing)
            + "10,AmazonHelp,False,10,We can help with that.,,9\n"
            
            // Scenario 7: Cycle protection (11 -> 12 -> 11)
            + "11,CustomerD,True,11,Cycle start,,12\n"
            + "12,AmazonHelp,False,12,Cycle end,,11\n"
            
            // Scenario 8: Missing response (13 has response 14, but 14 is missing)
            + "13,AmazonHelp,False,13,Response missing,14, \n";

        Files.writeString(tempCsv, csvContent);
        
        AmazonHelpPipeline pipeline = new AmazonHelpPipeline(tempCsv, tempDir.toString());
        pipeline.process();
        
        Path outputFile = tempDir.resolve("intermediate_paths.jsonl");
        assertTrue(Files.exists(outputFile), "Output file should be created");
        
        List<String> lines = Files.readAllLines(outputFile);
        
        // Output should not contain text
        for (String line : lines) {
            assertFalse(line.contains("Hello Amazon"), "Output must not contain tweet text");
        }
        
        // Check paths for root 1
        // Expected paths from 1:
        // [1, 2, 3, 4]
        // [1, 2, 6, 7]
        // Should NOT contain 5 (AppleSupport) or 8 (CustomerC)
        boolean foundMultiTurn = false;
        boolean foundBranch = false;
        for (String line : lines) {
            if (line.contains("\"rootTweetId\":\"1\"")) {
                foundMultiTurn = line.contains("[\"1\",\"2\",\"3\",\"4\"]");
                foundBranch = line.contains("[\"1\",\"2\",\"6\",\"7\"]");
                assertFalse(line.contains("\"5\""), "Must exclude AppleSupport");
                assertFalse(line.contains("\"8\""), "Must exclude unrelated sibling");
            }
        }
        assertTrue(foundMultiTurn, "Multi-turn path must be found");
        assertTrue(foundBranch, "Branching path must be found");
        
        // Check missing parent scenario
        boolean foundMissingParent = false;
        for (String line : lines) {
            if (line.contains("\"rootTweetId\":\"9\"")) {
                foundMissingParent = line.contains("[\"9\",\"10\"]");
            }
        }
        assertTrue(foundMissingParent, "Missing parent root must be processed");
        
        // Check cycle (isolated cycles have no root and are dropped)
        boolean foundCycle = false;
        for (String line : lines) {
            if (line.contains("\"rootTweetId\":\"11\"") || line.contains("\"rootTweetId\":\"12\"") || line.contains("\"rootTweetId\":\"99\"")) {
                foundCycle = true;
            }
        }
        assertFalse(foundCycle, "Isolated cycle must be safely ignored");
    }
}

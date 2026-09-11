package com.supportiq.data;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import com.supportiq.model.Intent;
import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.DeliverySubCategory;
import com.supportiq.model.intent.AccountSubCategory;

public class InfrastructureTest {

    @Test
    public void testTweetRecord() {
        TweetRecord record = new TweetRecord();
        record.setTweet_id("123");
        record.setAuthor_id("AmazonHelp");
        record.setInbound(false);
        record.setCreated_at("Wed Oct 11 20:22:45 +0000 2017");
        record.setText("Hello!");
        record.setResponse_tweet_id("124");
        record.setIn_response_to_tweet_id("122");

        assertEquals("123", record.getTweet_id());
        assertEquals("AmazonHelp", record.getAuthor_id());
        assertFalse(record.isInbound());
        assertEquals("Hello!", record.getText());
    }

    @Test
    public void testAmazonHelpFilter() {
        AmazonHelpFilter filter = new AmazonHelpFilter();
        
        TweetRecord amazonRecord = new TweetRecord();
        amazonRecord.setAuthor_id("AmazonHelp");
        assertTrue(filter.isAmazonHelp(amazonRecord));

        TweetRecord otherRecord = new TweetRecord();
        otherRecord.setAuthor_id("AppleSupport");
        assertFalse(filter.isAmazonHelp(otherRecord));
        
        TweetRecord nullRecord = new TweetRecord();
        assertFalse(filter.isAmazonHelp(nullRecord));
    }

    private TweetRecord createRec(String id, String responseTo, String date) {
        TweetRecord r = new TweetRecord();
        r.setTweet_id(id);
        r.setIn_response_to_tweet_id(responseTo);
        r.setCreated_at(date);
        return r;
    }

    @Test
    public void testConversationBuilder() {
        TweetRecord r1 = createRec("1", null, "2017-10-31 22:10:01");
        TweetRecord r2 = createRec("2", "1", "2017-10-31 22:15:00");
        TweetRecord r3 = createRec("3", "2", "2017-10-31 22:15:01"); // Ties timestamp but smaller ID
        TweetRecord r4 = createRec("4", "2", "2017-10-31 22:15:02"); // Should be second branch
        
        ConversationBuilder builder = new ConversationBuilder();
        builder.addRecord(r1.getTweet_id(), r1.getIn_response_to_tweet_id(), r1.getCreated_at());
        builder.addRecord(r2.getTweet_id(), r2.getIn_response_to_tweet_id(), r2.getCreated_at());
        builder.addRecord(r3.getTweet_id(), r3.getIn_response_to_tweet_id(), r3.getCreated_at());
        builder.addRecord(r4.getTweet_id(), r4.getIn_response_to_tweet_id(), r4.getCreated_at());
        
        // Cycle test setup
        TweetRecord c1 = createRec("10", "11", "2017-10-31 22:10:00");
        TweetRecord c2 = createRec("11", "10", "2017-10-31 22:10:01");
        builder.addRecord(c1.getTweet_id(), c1.getIn_response_to_tweet_id(), c1.getCreated_at());
        builder.addRecord(c2.getTweet_id(), c2.getIn_response_to_tweet_id(), c2.getCreated_at());
        
        // Duplicate child
        List<List<String>> paths = builder.extractPaths("1");
        assertEquals(2, paths.size());
        assertEquals(List.of("1", "2", "3"), paths.get(0));
        assertEquals(List.of("1", "2", "4"), paths.get(1));

        List<List<String>> cyclePaths = builder.extractPaths("10");
        assertEquals(1, cyclePaths.size());
        assertEquals(List.of("10", "11"), cyclePaths.get(0));
    }

    @Test
    public void testMappingWriter() throws IOException {
        Path tempDir = Files.createTempDirectory("mapping-test");
        MappingWriter writer = new MappingWriter(tempDir.toString());

        List<List<String>> paths = Arrays.asList(
            Arrays.asList("272", "269", "270")
        );

        writer.writeMapping(MainCategory.DELIVERY_AND_TRACKING, DeliverySubCategory.DELIVERY_LATE, "272", paths);

        Path mappingFile = tempDir.resolve("DELIVERY_AND_TRACKING").resolve("DELIVERY_LATE").resolve("mapping.jsonl");
        assertTrue(Files.exists(mappingFile));

        String content = Files.readString(mappingFile);
        assertTrue(content.contains("\"rootTweetId\":\"272\""));
        assertFalse(content.contains("text"));
        
        // Validation testing
        assertThrows(IllegalArgumentException.class, () -> {
            writer.writeMapping(MainCategory.DELIVERY_AND_TRACKING, DeliverySubCategory.DELIVERY_LATE, "", paths);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            writer.writeMapping(MainCategory.DELIVERY_AND_TRACKING, DeliverySubCategory.DELIVERY_LATE, "999", paths);
        });
    }

    @Test
    public void testIntentValidation() {
        assertDoesNotThrow(() -> {
            new Intent(MainCategory.DELIVERY_AND_TRACKING, DeliverySubCategory.DELIVERY_LATE, 1.0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new Intent(MainCategory.DELIVERY_AND_TRACKING, AccountSubCategory.CANNOT_LOGIN, 1.0);
        });
    }

    @Test
    public void testCsvReader() throws Exception {
        Path tempCsv = Files.createTempFile("test", ".csv");
        String content = "tweet_id,author_id,inbound,created_at,text,response_tweet_id,in_response_to_tweet_id\n" +
                         "1,AmazonHelp,False,date,\"text, with comma\",2,\n" +
                         "2,User,True,date,\"text with \"\"escaped\"\" quote\",,1\n" +
                         "3,AmazonHelp,False,date,,,\n";
        Files.writeString(tempCsv, content);
        
        try (CsvReader reader = CsvReader.read(tempCsv)) {
            assertTrue(reader.hasNext());
            TweetRecord r1 = reader.next();
            assertEquals("1", r1.getTweet_id());
            assertEquals("AmazonHelp", r1.getAuthor_id());
            assertFalse(r1.isInbound());
            assertEquals("text, with comma", r1.getText());
            assertEquals("2", r1.getResponse_tweet_id());
            
            assertTrue(reader.hasNext());
            TweetRecord r2 = reader.next();
            assertEquals("2", r2.getTweet_id());
            assertEquals("User", r2.getAuthor_id());
            assertTrue(r2.isInbound());
            assertEquals("text with \"escaped\" quote", r2.getText());
            assertEquals("", r2.getResponse_tweet_id());
            
            assertTrue(reader.hasNext());
            TweetRecord r3 = reader.next();
            assertEquals("3", r3.getTweet_id());
            assertEquals("", r3.getText());
            
            assertFalse(reader.hasNext());
        }
    }
}

package com.supportiq.data;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.DeliverySubCategory;

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
    }

    @Test
    public void testConversationBuilderBranching() {
        ConversationBuilder builder = new ConversationBuilder();
        
        TweetRecord r269 = new TweetRecord();
        r269.setTweet_id("269");
        r269.setIn_response_to_tweet_id("272");

        TweetRecord r270 = new TweetRecord();
        r270.setTweet_id("270");
        r270.setIn_response_to_tweet_id("269");

        TweetRecord r271 = new TweetRecord();
        r271.setTweet_id("271");
        r271.setIn_response_to_tweet_id("269");

        builder.addRecord(r269);
        builder.addRecord(r270);
        builder.addRecord(r271);

        List<List<String>> paths = builder.extractPaths("272");
        assertEquals(2, paths.size());
        assertTrue(paths.contains(Arrays.asList("272", "269", "270")));
        assertTrue(paths.contains(Arrays.asList("272", "269", "271")));
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
    }
}

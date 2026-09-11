package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.HistoricalConversation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HistoricalRetrievalServiceTest {

    private static HistoricalRetrievalService service;
    
    @BeforeAll
    public static void setup() throws IOException {
        String workingCsv = Paths.get("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv").toAbsolutePath().toString();
        String mappingBaseDir = Paths.get("data/mapping/AmazonHelp").toAbsolutePath().toString();
        service = new HistoricalRetrievalServiceImpl(workingCsv, mappingBaseDir);
    }

    @Test
    public void testA_ValidCategoryLookup() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", 5);
        assertNotNull(res);
        assertTrue(res.size() <= 5);
        if (!res.isEmpty()) {
            assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, res.get(0).getCategory());
        }
    }

    @Test
    public void testB_UnknownCategory() {
        // Enforced by Java compiler since IntentTaxonomy is an enum. But we test null.
        List<HistoricalConversation> res = service.retrieve(null, "PACKAGE_MARKED_DELIVERED", 5);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testC_UnknownSubcategory() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "NON_EXISTENT_SUBCAT", 5);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testD_EmptyInput() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "", 5);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testE_ZeroLimit() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", 0);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testF_NegativeLimit() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", -5);
        assertTrue(res.isEmpty());
    }

    @Test
    public void testG_MaximumLimit() {
        List<HistoricalConversation> res = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", Integer.MAX_VALUE);
        assertNotNull(res);
    }

    @Test
    public void testT_RepeatedRetrievalIdentical() {
        List<HistoricalConversation> res1 = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", 10);
        List<HistoricalConversation> res2 = service.retrieve(IntentTaxonomy.DELIVERY_AND_TRACKING, "PACKAGE_MARKED_DELIVERED", 10);
        assertEquals(res1.size(), res2.size());
        for (int i = 0; i < res1.size(); i++) {
            assertEquals(res1.get(i).getRootTweetId(), res2.get(i).getRootTweetId());
        }
    }
}

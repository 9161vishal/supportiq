package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DeterministicIntentClassifierTest {

    private final DeterministicIntentClassifier classifier = new DeterministicIntentClassifier();

    @Test
    void testNullMessage() {
        assertNull(classifier.classify(null));
        assertNull(classifier.classify(new CustomerMessage(null)));
    }

    @Test
    void testConfidentMatch() {
        Intent intent = classifier.classify(new CustomerMessage("Where is my delayed package?"));
        assertNotNull(intent);
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("DELIVERY_LATE", intent.getSubCategory()); // "delayed" triggers this
        assertEquals(1.0, intent.getConfidence());
        assertFalse(intent.isUncertain());
    }

    @Test
    void testAmbiguousSubcategory() {
        // "tracking" + "late" triggers multiple subcategories inside DELIVERY_AND_TRACKING
        // "stuck" (tracking_not_updated) and "late" (delivery_late)
        Intent intent = classifier.classify(new CustomerMessage("My tracking is stuck and it is late"));
        assertNotNull(intent);
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("AMBIGUOUS_SUBCATEGORY", intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    @Test
    void testAmbiguousCategory() {
        // "package" triggers DELIVERY_AND_TRACKING, "cancel" triggers ORDER_MANAGEMENT
        Intent intent = classifier.classify(new CustomerMessage("Cancel my package delivery"));
        assertNotNull(intent);
        assertEquals("AMBIGUOUS_CATEGORY", intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    @Test
    void testUnmapped() {
        Intent intent = classifier.classify(new CustomerMessage("The weather is nice today."));
        assertNull(intent);
    }
}

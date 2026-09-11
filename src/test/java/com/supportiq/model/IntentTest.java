package com.supportiq.model;

import com.supportiq.data.IntentTaxonomy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntentTest {
    @Test
    void intentValidatesMatchingSubcategory() {
        Intent intent = new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "DELIVERY_LATE", 0.95, false);
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("DELIVERY_LATE", intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence(), 0.001);
        assertFalse(intent.isUncertain());
    }
}

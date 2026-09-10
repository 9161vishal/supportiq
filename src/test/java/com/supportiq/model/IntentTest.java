package com.supportiq.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntentTest {
    @Test
    public void testMainCategoryValuesExist() {
        assertNotNull(MainCategory.valueOf("DELIVERY_AND_TRACKING"));
        assertNotNull(MainCategory.valueOf("GENERAL_INFORMATION_AND_NON_SUPPORT"));
    }

    @Test
    public void testSubCategoryValuesExist() {
        assertNotNull(SubCategory.valueOf("DELIVERY_LATE"));
        assertNotNull(SubCategory.valueOf("OTHER_NON_ACTIONABLE_MESSAGE"));
    }

    @Test
    public void testIntentCreation() {
        Intent intent = new Intent(MainCategory.DELIVERY_AND_TRACKING, SubCategory.DELIVERY_LATE, 0.95);
        assertEquals(MainCategory.DELIVERY_AND_TRACKING, intent.getMainCategory());
        assertEquals(SubCategory.DELIVERY_LATE, intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence(), 0.001);
    }
}

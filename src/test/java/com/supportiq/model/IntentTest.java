package com.supportiq.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.DeliverySubCategory;
import com.supportiq.model.intent.GeneralInformationSubCategory;

public class IntentTest {
    @Test
    public void testMainCategoryValuesExist() {
        assertNotNull(MainCategory.valueOf("DELIVERY_AND_TRACKING"));
        assertNotNull(MainCategory.valueOf("GENERAL_INFORMATION_AND_NON_SUPPORT"));
    }

    @Test
    public void testSubCategoryValuesExist() {
        assertNotNull(DeliverySubCategory.valueOf("DELIVERY_LATE"));
        assertNotNull(GeneralInformationSubCategory.valueOf("OTHER_NON_ACTIONABLE_MESSAGE"));
    }

    @Test
    public void testIntentCreation() {
        Intent intent = new Intent(MainCategory.DELIVERY_AND_TRACKING, DeliverySubCategory.DELIVERY_LATE, 0.95);
        assertEquals(MainCategory.DELIVERY_AND_TRACKING, intent.getMainCategory());
        assertEquals(DeliverySubCategory.DELIVERY_LATE, intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence(), 0.001);
    }
}

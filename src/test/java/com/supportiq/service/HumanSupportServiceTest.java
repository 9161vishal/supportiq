package com.supportiq.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class HumanSupportServiceTest {

    @Test
    void testConfiguredMessageReturned() {
        String expectedMessage = "Please contact us at 1-800-AMAZON.";
        HumanSupportService service = new HumanSupportServiceImpl(expectedMessage);
        
        assertEquals(expectedMessage, service.getHandoffMessage());
    }
}

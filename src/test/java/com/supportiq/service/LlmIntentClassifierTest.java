package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class LlmIntentClassifierTest {

    private HttpClient mockHttpClient;
    private HttpResponse<String> mockResponse;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        mockHttpClient = Mockito.mock(HttpClient.class);
        mockResponse = (HttpResponse<String>) Mockito.mock(HttpResponse.class);
        
        Mockito.when(mockResponse.statusCode()).thenReturn(200);
        Mockito.when(mockHttpClient.send(ArgumentMatchers.any(HttpRequest.class), ArgumentMatchers.any(HttpResponse.BodyHandler.class)))
               .thenReturn(mockResponse);
    }

    private LlmIntentClassifier createClassifier(String apiKey, double threshold) {
        return new LlmIntentClassifier("http://localhost/v1", apiKey, "test-model", threshold, mockHttpClient);
    }

    private void mockJsonResponse(String jsonContent) {
        String fullResponse = "{ \"choices\": [ { \"message\": { \"content\": \"" + jsonContent.replace("\"", "\\\"").replace("\n", "\\n") + "\" } } ] }";
        Mockito.when(mockResponse.body()).thenReturn(fullResponse);
    }

    @Test
    void classify_validResponse() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"LATE\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Where is my stuff?"));
        
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("LATE", intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence());
        assertFalse(intent.isUncertain());
    }

    @Test
    void classify_invalidCategoryFallsBackToGeneral() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"MADE_UP_CATEGORY\", \"subcategory\": \"FOO\", \"confidence\": 0.8, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_lowConfidenceTriggersUncertain() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.8);
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL\", \"confidence\": 0.6, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Maybe cancel?"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("CANCEL", intent.getSubCategory());
        assertEquals(0.6, intent.getConfidence());
        assertTrue(intent.isUncertain()); // Because 0.6 < threshold 0.8
    }

    @Test
    void classify_missingKeyThrowsException() {
        LlmIntentClassifier classifier = createClassifier("", 0.6);
        assertThrows(IllegalStateException.class, () -> {
            classifier.classify(new CustomerMessage("Hello"));
        });
    }

    @Test
    void classify_emptyInputReturnsUnknown() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        Intent intent = classifier.classify(new CustomerMessage("   "));
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_malformedResponseHandledGracefully() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        Mockito.when(mockResponse.body()).thenReturn("invalid json!!!");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_nullInputReturnsUnknown() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        Intent intent = classifier.classify(new CustomerMessage(null));
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertTrue(intent.isUncertain());
    }
}

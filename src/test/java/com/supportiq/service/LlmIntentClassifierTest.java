package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class LlmIntentClassifierTest {

    private HttpServer server;
    private String apiUrl;
    private String nextResponse = "";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", (HttpExchange exchange) -> {
            String fullResponse;
            if (nextResponse.startsWith("{") && nextResponse.endsWith("}")) {
                fullResponse = "{ \"choices\": [ { \"message\": { \"content\": \"" + nextResponse.replace("\"", "\\\"").replace("\n", "\\n") + "\" } } ] }";
            } else {
                fullResponse = nextResponse; // Return verbatim if malformed
            }
            byte[] responseBytes = fullResponse.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.setExecutor(null);
        server.start();

        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LlmIntentClassifier createClassifier(String apiKey, double threshold) {
        return new LlmIntentClassifier(apiUrl, apiKey, "test-model", threshold);
    }

    private void mockJsonResponse(String jsonContent) {
        this.nextResponse = jsonContent;
    }

    @Test
    void classify_validResponse() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"TRACKING_NOT_UPDATED\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Where is my stuff?"));
        
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("TRACKING_NOT_UPDATED", intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence());
        assertFalse(intent.isUncertain());
    }

    @Test
    void classify_validCategoryWrongSubcategoryFallsBack() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"PRIME_TRIAL\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_validCategoryInventedSubcategoryFallsBack() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"PACKAGE_IS_SOMEWHERE\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_invalidCategoryFallsBackToGeneral() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"MADE_UP_CATEGORY\", \"subcategory\": \"FOO\", \"confidence\": 0.8, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_lowConfidenceTriggersUncertain() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.8);
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": 0.6, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Maybe cancel?"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("CANCEL_ORDER", intent.getSubCategory());
        assertEquals(0.6, intent.getConfidence());
        assertTrue(intent.isUncertain()); // Because 0.6 < threshold 0.8
    }

    @Test
    void classify_explicitUncertainIsTrue() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"ORDER_STATUS\", \"confidence\": 0.9, \"uncertain\": true }");
        
        Intent intent = classifier.classify(new CustomerMessage("Is my order cancelled or late?"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("ORDER_STATUS", intent.getSubCategory());
        assertEquals(0.9, intent.getConfidence());
        assertTrue(intent.isUncertain());
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
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_whitespaceInputReturnsUnknown() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        Intent intent = classifier.classify(new CustomerMessage("\n\t  "));
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_malformedResponseHandledGracefully() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        mockJsonResponse("invalid json!!!");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }

    @Test
    void classify_nullInputReturnsUnknown() {
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6);
        Intent intent = classifier.classify(new CustomerMessage(null));
        assertEquals(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, intent.getCategory());
        assertEquals("UNKNOWN", intent.getSubCategory());
        assertEquals(0.0, intent.getConfidence());
        assertTrue(intent.isUncertain());
    }
}

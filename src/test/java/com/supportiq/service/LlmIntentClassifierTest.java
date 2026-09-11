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
        server.createContext("/v1beta/models/test-model:generateContent", (HttpExchange exchange) -> {
            String fullResponse;
            if (nextResponse.startsWith("{") && nextResponse.endsWith("}")) {
                fullResponse = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"" + nextResponse.replace("\"", "\\\"").replace("\n", "\\n") + "\" } ] } } ] }";
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

        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/%s:generateContent";
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

    @Test
    void classify_retriesOn429() throws Exception {
        // Create a custom server context that returns 429 on the first try and 200 on the second
        HttpServer retryServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] attempts = {0};
        retryServer.createContext("/v1beta/models/test-model:generateContent", (HttpExchange exchange) -> {
            attempts[0]++;
            if (attempts[0] == 1) {
                String response = "Too Many Requests";
                exchange.sendResponseHeaders(429, response.getBytes("UTF-8").length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes("UTF-8"));
                }
            } else {
                String fullResponse = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"{ \\\"category\\\": \\\"ORDER_MANAGEMENT\\\", \\\"subcategory\\\": \\\"CANCEL_ORDER\\\", \\\"confidence\\\": 0.9, \\\"uncertain\\\": false }\" } ] } } ] }";
                byte[] responseBytes = fullResponse.getBytes("UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        });
        retryServer.setExecutor(null);
        retryServer.start();

        String retryUrl = "http://127.0.0.1:" + retryServer.getAddress().getPort() + "/v1beta/models/%s:generateContent";
        LlmIntentClassifier classifier = new LlmIntentClassifier(retryUrl, "fake-key", "test-model", 0.6);

        // This will block due to exponential backoff (1000ms delay for the first retry)
        Intent intent = classifier.classify(new CustomerMessage("Cancel my order"));

        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("CANCEL_ORDER", intent.getSubCategory());
        assertEquals(0.9, intent.getConfidence());
        assertFalse(intent.isUncertain());
        assertEquals(2, attempts[0], "Should have retried exactly once");

        retryServer.stop(0);
    }

    @Test
    void classify_404FailsFast() throws Exception {
        HttpServer failServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int[] attempts = {0};
        failServer.createContext("/v1beta/models/test-model:generateContent", (HttpExchange exchange) -> {
            attempts[0]++;
            String response = "Not Found";
            exchange.sendResponseHeaders(404, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
        });
        failServer.setExecutor(null);
        failServer.start();

        String failUrl = "http://127.0.0.1:" + failServer.getAddress().getPort() + "/v1beta/models/%s:generateContent";
        LlmIntentClassifier classifier = new LlmIntentClassifier(failUrl, "fake-key", "test-model", 0.6);

        assertThrows(IllegalStateException.class, () -> {
            classifier.classify(new CustomerMessage("Cancel my order"));
        });

        assertEquals(1, attempts[0], "Should not have retried on 404");

        failServer.stop(0);
    }
}

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
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class LlmIntentClassifierTest {

    private HttpServer server;
    private String apiUrl;
    private volatile String nextResponse = "";
    private volatile int customStatusCode = 200;
    private volatile int requestDelayMs = 0;
    private final AtomicInteger callCount = new AtomicInteger(0);
    private volatile String expectedApiKey = "fake-key";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/test-model:generateContent", (HttpExchange exchange) -> {
            callCount.incrementAndGet();
            
            if (requestDelayMs > 0) {
                try {
                    Thread.sleep(requestDelayMs);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
            
            // Assert x-goog-api-key exists, equals expected, and ?key= does NOT exist in URI
            if (exchange.getRequestURI().toString().contains("?key=")) {
                customStatusCode = 400;
                nextResponse = "URL contains secret!";
            } else if (!exchange.getRequestHeaders().containsKey("x-goog-api-key")) {
                customStatusCode = 401;
                nextResponse = "Missing x-goog-api-key";
            } else if (!expectedApiKey.equals(exchange.getRequestHeaders().getFirst("x-goog-api-key"))) {
                customStatusCode = 401;
                nextResponse = "Invalid x-goog-api-key";
            }
            
            String fullResponse;
            if (nextResponse.equals("EMPTY_BODY")) {
                fullResponse = "";
            } else if (nextResponse.equals("NULL_RESPONSE")) {
                fullResponse = "null";
            } else if (nextResponse.equals("URL contains secret!") || nextResponse.equals("Missing x-goog-api-key")) {
                fullResponse = nextResponse;
            } else if (nextResponse.startsWith("{") && nextResponse.contains("\"malformed_api_response\"")) {
                fullResponse = nextResponse;
            } else {
                fullResponse = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"" + nextResponse.replace("\"", "\\\"").replace("\n", "\\n") + "\" } ] } } ] }";
            }
            
            byte[] responseBytes = fullResponse.getBytes("UTF-8");
            exchange.sendResponseHeaders(customStatusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/models/%s:generateContent";
        callCount.set(0);
        requestDelayMs = 0;
        customStatusCode = 200;
        expectedApiKey = "fake-key";
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private LlmIntentClassifier createClassifier(String apiKey, double threshold, int connectTimeoutSec, int requestTimeoutSec) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            this.expectedApiKey = apiKey;
        }
        return new LlmIntentClassifier(apiUrl, apiKey, "test-model", threshold, 
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeoutSec)).build(), requestTimeoutSec);
    }

    private LlmIntentClassifier createDefaultClassifier() {
        return createClassifier("fake-key", 0.6, 2, 2);
    }

    private void mockJsonResponse(String jsonContent) {
        this.nextResponse = jsonContent;
        this.customStatusCode = 200;
    }

    private void mockHttpResponse(int code, String body) {
        this.customStatusCode = code;
        this.nextResponse = body;
    }

    // A, B, C: valid category, subcategory, and combination
    @Test
    void testA_B_C_ValidCategoryAndSubcategoryCombination() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"TRACKING_NOT_UPDATED\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Where is my stuff?"));
        
        assertEquals(IntentTaxonomy.DELIVERY_AND_TRACKING, intent.getCategory());
        assertEquals("TRACKING_NOT_UPDATED", intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence());
        assertFalse(intent.isUncertain());
    }

    // D: unknown category
    @Test
    void testD_UnknownCategoryReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"MADE_UP_CATEGORY\", \"subcategory\": \"FOO\", \"confidence\": 0.8, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertNull(intent.getCategory());
        assertNull(intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    // E, F: unknown subcategory, category/subcategory mismatch
    @Test
    void testE_F_InvalidSubcategoryMismatchReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"subcategory\": \"PRIME_TRIAL\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertNull(intent.getCategory());
        assertNull(intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    // G: missing category
    @Test
    void testG_MissingCategoryReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"subcategory\": \"TRACKING_NOT_UPDATED\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertNull(intent.getCategory());
        assertNull(intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    // H: missing subcategory
    @Test
    void testH_MissingSubcategoryReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"DELIVERY_AND_TRACKING\", \"confidence\": 0.95, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        
        assertNull(intent.getCategory());
        assertNull(intent.getSubCategory());
        assertTrue(intent.isUncertain());
    }

    // I, J: null response, empty response
    @Test
    void testI_J_NullOrEmptyResponseReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("EMPTY_BODY");
        
        Intent intent = classifier.classify(new CustomerMessage("Blah"));
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());

        mockJsonResponse("NULL_RESPONSE");
        Intent intent2 = classifier.classify(new CustomerMessage("Blah"));
        assertNull(intent2.getCategory());
        assertTrue(intent2.isUncertain());
    }

    // K: malformed JSON
    @Test
    void testK_MalformedJsonReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("invalid json!!!");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
    }

    // L: JSON inside markdown fences
    @Test
    void testL_JsonInsideMarkdownFences() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("```json\\n{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": 0.95, \"uncertain\": false }\\n```");
        
        Intent intent = classifier.classify(new CustomerMessage("Cancel my order"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("CANCEL_ORDER", intent.getSubCategory());
        assertEquals(0.95, intent.getConfidence());
    }

    // M: extra JSON fields
    @Test
    void testM_ExtraJsonFieldsHandled() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": 0.95, \"uncertain\": false, \"extra_field\": 123 }");
        
        Intent intent = classifier.classify(new CustomerMessage("Cancel my order"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals("CANCEL_ORDER", intent.getSubCategory());
    }

    // N: wrong field types
    @Test
    void testN_WrongFieldTypesReturnsFallback() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": 123, \"subcategory\": \"CANCEL_ORDER\", \"confidence\": \"high\", \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Cancel my order"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
    }

    // O, P: invalid confidence, confidence outside bounds
    @Test
    void testO_P_ConfidenceOutOfBoundsClamped() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": 1.5, \"uncertain\": false }");
        
        Intent intent = classifier.classify(new CustomerMessage("Cancel my order"));
        assertEquals(1.0, intent.getConfidence()); // Should clamp to 1.0

        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": -0.5, \"uncertain\": false }");
        Intent intent2 = classifier.classify(new CustomerMessage("Cancel my order"));
        assertEquals(0.0, intent2.getConfidence()); // Should clamp to 0.0
    }

    // Q: missing API key
    @Test
    void testQ_MissingApiKeyThrowsException() {
        LlmIntentClassifier classifier = createClassifier(null, 0.6, 2, 2);
        assertThrows(IllegalStateException.class, () -> {
            classifier.classify(new CustomerMessage("Hello"));
        });
        
        LlmIntentClassifier classifier2 = createClassifier("   ", 0.6, 2, 2);
        assertThrows(IllegalStateException.class, () -> {
            classifier2.classify(new CustomerMessage("Hello"));
        });
    }

    // R, S: HTTP 401, HTTP 403
    @Test
    void testR_S_Http401And403ThrowsException() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        
        mockHttpResponse(401, "Unauthorized");
        assertThrows(IllegalStateException.class, () -> {
            classifier.classify(new CustomerMessage("Hello"));
        });
        assertEquals(1, callCount.get()); // No retries for 401
        
        callCount.set(0);
        mockHttpResponse(403, "Forbidden");
        assertThrows(IllegalStateException.class, () -> {
            classifier.classify(new CustomerMessage("Hello"));
        });
        assertEquals(1, callCount.get()); // No retries for 403
    }

    // T: HTTP 429
    @Test
    void testT_Http429RetriesAndFailsSafely() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockHttpResponse(429, "Too Many Requests");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
        assertEquals(3, callCount.get()); // Should retry 3 times
    }

    // U: HTTP 500
    @Test
    void testU_Http500RetriesAndFailsSafely() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockHttpResponse(500, "Internal Server Error");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
        assertEquals(3, callCount.get()); // Should retry 3 times
    }

    // V: Network Timeout
    @Test
    void testV_NetworkTimeoutRetries() {
        // Set request timeout to 1 second
        LlmIntentClassifier classifier = createClassifier("fake-key", 0.6, 2, 1);
        
        // Delay response by 2 seconds to force timeout
        this.requestDelayMs = 2000;
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
        assertEquals(3, callCount.get()); // Retries 3 times due to HttpTimeoutException
    }

    // W: malformed provider response
    @Test
    void testW_MalformedProviderResponse() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockHttpResponse(200, "{\"malformed_api_response\": true}");
        
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertNull(intent.getCategory());
        assertTrue(intent.isUncertain());
    }

    // X, Y: retry behavior, retry limit
    @Test
    void testX_Y_RetryBehaviorAndLimit() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        mockHttpResponse(503, "Service Unavailable");
        
        classifier.classify(new CustomerMessage("Hello"));
        
        assertEquals(3, callCount.get()); // Retry limit is exactly 3.
    }

    // Z: Secret-safe error logging
    @Test
    void testZ_SecretSafeErrorLogging() {
        String secretKey = "SUPER_SECRET_KEY_12345";
        LlmIntentClassifier classifier = createClassifier(secretKey, 0.6, 2, 2);
        
        // Cause a 401 error
        mockHttpResponse(401, "Unauthorized access to " + secretKey);
        
        try {
            classifier.classify(new CustomerMessage("Hello"));
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            assertFalse(msg.contains(secretKey), "Exception message leaked the secret key!");
        }
        
        // Cause a 404 error
        mockHttpResponse(404, "Model not found");
        try {
            classifier.classify(new CustomerMessage("Hello"));
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            assertFalse(msg.contains(secretKey), "Exception message leaked the secret key!");
        }
    }

    // AA: Empty/Null CustomerMessage tests
    @Test
    void testAA_NullOrEmptyMessageReturnsFallbackWithoutApiCall() {
        LlmIntentClassifier classifier = createDefaultClassifier();
        
        Intent intent1 = classifier.classify(null);
        assertNull(intent1.getCategory());
        assertTrue(intent1.isUncertain());

        Intent intent2 = classifier.classify(new CustomerMessage(null));
        assertNull(intent2.getCategory());
        assertTrue(intent2.isUncertain());

        Intent intent3 = classifier.classify(new CustomerMessage("   "));
        assertNull(intent3.getCategory());
        assertTrue(intent3.isUncertain());
        
        assertEquals(0, callCount.get()); // No API calls should be made
    }

    // AB: Header Security Test
    @Test
    void testAB_HeaderSecurityAndUrlSecurity() {
        String secretKey = "HEADER_SECRET_99999";
        LlmIntentClassifier classifier = createClassifier(secretKey, 0.6, 2, 2);
        mockJsonResponse("{ \"category\": \"ORDER_MANAGEMENT\", \"subcategory\": \"CANCEL_ORDER\", \"confidence\": 0.95, \"uncertain\": false }");
        
        // Our mock server is configured to check for ?key= in URI and x-goog-api-key in headers.
        Intent intent = classifier.classify(new CustomerMessage("Hello"));
        
        assertEquals(IntentTaxonomy.ORDER_MANAGEMENT, intent.getCategory());
        assertEquals(1, callCount.get());
    }
}

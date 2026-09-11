package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class SmokeTestRunner {

    private static String nextResponse = "";

    public static void main(String[] args) throws Exception {
        System.out.println("Running Intent Classifier Smoke Tests (HttpServer Fake)");
        
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", (HttpExchange exchange) -> {
            String fullResponse = "{ \"choices\": [ { \"message\": { \"content\": \"" + nextResponse.replace("\"", "\\\"") + "\" } } ] }";
            byte[] responseBytes = fullResponse.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.setExecutor(null);
        server.start();

        String apiUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";

        LlmIntentClassifier classifier = new LlmIntentClassifier(
            apiUrl, 
            "dummy-key", 
            "test-model", 
            0.6
        );

        try {
            runTest(classifier, "delivery/tracking", "My order is late, where is it?", "{\"category\":\"DELIVERY_AND_TRACKING\",\"subcategory\":\"DELIVERY_LATE\",\"confidence\":0.95,\"uncertain\":false}");
            runTest(classifier, "order cancellation", "Please cancel my order 123.", "{\"category\":\"ORDER_MANAGEMENT\",\"subcategory\":\"CANCEL_ORDER\",\"confidence\":0.92,\"uncertain\":false}");
            runTest(classifier, "refund", "I need my money back for this defective item.", "{\"category\":\"RETURNS_AND_REFUNDS\",\"subcategory\":\"REFUND_REQUEST\",\"confidence\":0.88,\"uncertain\":false}");
            runTest(classifier, "product problem", "The screen is cracked.", "{\"category\":\"PRODUCT_PROBLEM\",\"subcategory\":\"DAMAGED_PRODUCT\",\"confidence\":0.90,\"uncertain\":false}");
            runTest(classifier, "payment", "My credit card was charged twice.", "{\"category\":\"PAYMENT_AND_BILLING\",\"subcategory\":\"CHARGE_OR_BILLING_PROBLEM\",\"confidence\":0.89,\"uncertain\":false}");
            runTest(classifier, "Prime", "How do I renew my Prime subscription?", "{\"category\":\"PRIME_MEMBERSHIP\",\"subcategory\":\"PRIME_RENEWAL\",\"confidence\":0.95,\"uncertain\":false}");
            runTest(classifier, "account/login", "I forgot my password.", "{\"category\":\"ACCOUNT_AND_LOGIN\",\"subcategory\":\"PASSWORD_PROBLEM\",\"confidence\":0.99,\"uncertain\":false}");
            runTest(classifier, "gift card", "My gift card balance says zero.", "{\"category\":\"GIFT_CARDS\",\"subcategory\":\"GIFT_CARD_BALANCE\",\"confidence\":0.93,\"uncertain\":false}");
            runTest(classifier, "device", "Alexa is not responding to my voice.", "{\"category\":\"AMAZON_DEVICES\",\"subcategory\":\"ECHO_PROBLEM\",\"confidence\":0.91,\"uncertain\":false}");
            runTest(classifier, "general/non-support", "Thanks for the quick help!", "{\"category\":\"GENERAL_INFORMATION_AND_NON_SUPPORT\",\"subcategory\":\"PRAISE_OR_APPRECIATION\",\"confidence\":0.98,\"uncertain\":false}");
            runTest(classifier, "non-English Unicode", "Mi paquete no ha llegado hoy.", "{\"category\":\"DELIVERY_AND_TRACKING\",\"subcategory\":\"DELIVERY_LATE\",\"confidence\":0.85,\"uncertain\":false}");
            
            System.out.println("--- NEGATIVE TESTS ---");
            runTest(classifier, "invalid subcategory rejection", "Where is my package?", "{\"category\":\"DELIVERY_AND_TRACKING\",\"subcategory\":\"PACKAGE_IS_SOMEWHERE\",\"confidence\":0.95,\"uncertain\":false}");
            
        } finally {
            server.stop(0);
        }
    }
    
    private static void runTest(LlmIntentClassifier classifier, String description, String message, String jsonResponse) {
        nextResponse = jsonResponse;
        
        Intent result = classifier.classify(new CustomerMessage(message));
        System.out.println("Test: " + description);
        System.out.println("  Message: " + message);
        System.out.println("  Result:  " + result.getCategory() + " / " + result.getSubCategory() + " (Confidence: " + result.getConfidence() + ", Uncertain: " + result.isUncertain() + ")\n");
    }
}

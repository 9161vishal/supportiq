package com.supportiq.evaluation;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.Intent;

public class BaselineClassifier {
    
    public Intent classify(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new Intent(null, null, 0.0, true);
        }
        
        String lower = text.toLowerCase();
        
        if (lower.contains("order") || lower.contains("package") || lower.contains("item")) {
            return new Intent(IntentTaxonomy.DELIVERY_AND_TRACKING, "GENERAL_DELIVERY_QUESTION", 1.0, false);
        }
        if (lower.contains("return") || lower.contains("refund") || lower.contains("money back")) {
            return new Intent(IntentTaxonomy.RETURNS_AND_REFUNDS, "RETURN_STATUS", 1.0, false);
        }
        if (lower.contains("prime") || lower.contains("subscribe") || lower.contains("membership")) {
            return new Intent(IntentTaxonomy.PRIME_MEMBERSHIP, "CANCEL_PRIME", 1.0, false);
        }
        if (lower.contains("kindle") || lower.contains("alexa") || lower.contains("echo") || lower.contains("fire tv")) {
            return new Intent(IntentTaxonomy.AMAZON_DEVICES, "GENERAL_DEVICE_QUESTION", 1.0, false);
        }
        if (lower.contains("payment") || lower.contains("card") || lower.contains("charge") || lower.contains("gift card")) {
            return new Intent(IntentTaxonomy.PAYMENT_AND_BILLING, "GENERAL_PAYMENT_PROBLEM", 1.0, false);
        }
        if (lower.contains("account") || lower.contains("password") || lower.contains("login") || lower.contains("hack")) {
            return new Intent(IntentTaxonomy.ACCOUNT_AND_LOGIN, "CANNOT_LOGIN", 1.0, false);
        }
        if (lower.contains("fraud") || lower.contains("scam") || lower.contains("phishing")) {
            return new Intent(IntentTaxonomy.PRIVACY_AND_SECURITY, "FRAUD_CONCERN", 1.0, false);
        }
        if (lower.contains("seller") || lower.contains("fake") || lower.contains("counterfeit")) {
            return new Intent(IntentTaxonomy.MARKETPLACE_AND_SELLERS, "THIRD_PARTY_SELLER_PROBLEM", 1.0, false);
        }
        if (lower.contains("app") || lower.contains("website") || lower.contains("error") || lower.contains("glitch")) {
            return new Intent(IntentTaxonomy.PRIME_VIDEO_AND_DIGITAL_CONTENT, "PLAYBACK_OR_TECHNICAL_PROBLEM", 1.0, false);
        }
        
        return new Intent(IntentTaxonomy.GENERAL_INFORMATION_AND_NON_SUPPORT, "UNKNOWN", 1.0, false);
    }
}

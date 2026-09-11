package com.supportiq.data;

import java.util.List;

public enum IntentTaxonomy {
    DELIVERY_AND_TRACKING(List.of("delivery", "tracking", "late", "arrived", "where is", "shipped", "delivered")),
    ORDER_MANAGEMENT(List.of("order", "cancel", "change", "update", "status")),
    RETURNS_AND_REFUNDS(List.of("return", "refund", "exchange", "money back", "defective")),
    PRODUCT_PROBLEM(List.of("broken", "damaged", "doesn't work", "missing part", "quality")),
    PAYMENT_AND_BILLING(List.of("charge", "charged", "billing", "invoice", "credit card", "payment")),
    PRIME_MEMBERSHIP(List.of("prime", "membership", "subscribe", "subscription", "renew")),
    PRIME_VIDEO_AND_DIGITAL_CONTENT(List.of("video", "movie", "stream", "watch", "music", "app")),
    AMAZON_DEVICES(List.of("echo", "alexa", "fire tv", "fire stick", "device")),
    KINDLE_AND_READING(List.of("kindle", "book", "read", "ebook")),
    AMAZON_PAY(List.of("amazon pay", "wallet")),
    ACCOUNT_AND_LOGIN(List.of("account", "login", "password", "locked", "email")),
    ADDRESS_AND_DELIVERY_PREFERENCES(List.of("address", "locker", "instruction", "door")),
    PROMOTIONS_AND_DISCOUNTS(List.of("discount", "promo", "code", "offer", "sale")),
    GIFT_CARDS(List.of("gift card", "redeem", "balance")),
    MARKETPLACE_AND_SELLERS(List.of("seller", "third party", "marketplace", "contact seller")),
    PRODUCT_INFORMATION_AND_PRICING(List.of("price", "info", "stock", "available", "when will", "specs")),
    SHIPPING_PACKAGING_AND_LOGISTICS(List.of("box", "packaging", "courier", "driver", "carrier")),
    CUSTOMER_SERVICE_EXPERIENCE(List.of("agent", "support", "rude", "helpful", "call", "chat")),
    PRIVACY_AND_SECURITY(List.of("privacy", "hack", "scam", "phishing", "fake")),
    GENERAL_INFORMATION_AND_NON_SUPPORT(List.of("thanks", "love", "great", "hello", "hi"));

    private final List<String> keywords;

    IntentTaxonomy(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}

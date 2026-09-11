package com.supportiq.data;

import java.util.List;

public enum IntentTaxonomy {
    DELIVERY_AND_TRACKING(
        List.of("delivery", "tracking", "late", "arrived", "where is", "shipped", "delivered"),
        List.of("DELIVERY_LATE", "DELIVERY_DATE", "TRACKING_NOT_UPDATED", "PACKAGE_MARKED_DELIVERED", "DELIVERY_ATTEMPT_FAILED", "GENERAL_DELIVERY_QUESTION", "COURIER_OR_DRIVER_PROBLEM")
    ),
    ORDER_MANAGEMENT(
        List.of("order", "cancel", "change", "update", "status"),
        List.of("ORDER_STATUS", "CANCEL_ORDER", "CHANGE_ORDER", "PLACE_ORDER", "ORDER_CONFIRMATION", "PRE_ORDER", "DUPLICATE_ORDER", "GENERAL_ORDER_QUESTION")
    ),
    RETURNS_AND_REFUNDS(
        List.of("return", "refund", "exchange", "money back", "defective"),
        List.of("RETURN_REQUEST", "RETURN_STATUS", "RETURN_ELIGIBILITY", "RETURN_SHIPPING", "REFUND_REQUEST", "REFUND_PENDING", "REFUND_AMOUNT", "REFUND_AFTER_CANCELLATION_OR_RETURN")
    ),
    PRODUCT_PROBLEM(
        List.of("broken", "damaged", "doesn't work", "missing part", "quality"),
        List.of("DAMAGED_PRODUCT", "DEFECTIVE_OR_NOT_WORKING", "WRONG_PRODUCT", "MISSING_ITEM_OR_COMPONENT", "REPLACEMENT_REQUEST", "PRODUCT_QUALITY_PROBLEM", "OTHER_PRODUCT_PROBLEM")
    ),
    PAYMENT_AND_BILLING(
        List.of("charge", "charged", "billing", "invoice", "credit card", "payment"),
        List.of("PAYMENT_FAILED", "PAYMENT_DECLINED", "PAYMENT_VERIFICATION", "UNEXPECTED_CHARGE", "CHARGE_OR_BILLING_PROBLEM", "CARD_CHARGED", "GENERAL_PAYMENT_PROBLEM")
    ),
    PRIME_MEMBERSHIP(
        List.of("prime", "membership", "subscribe", "subscription", "renew"),
        List.of("GENERAL_PRIME_QUESTION", "PRIME_MEMBERSHIP_OR_BENEFITS", "PRIME_TRIAL", "PRIME_RENEWAL", "CANCEL_PRIME", "PRIME_MEMBERSHIP_CHARGE", "PRIME_DELIVERY_BENEFIT")
    ),
    PRIME_VIDEO_AND_DIGITAL_CONTENT(
        List.of("video", "movie", "stream", "watch", "music", "app"),
        List.of("PRIME_VIDEO_GENERAL", "PLAYBACK_OR_TECHNICAL_PROBLEM", "CONTENT_AVAILABILITY", "MOVIE_OR_SHOW_PURCHASE_OR_RENTAL", "DIGITAL_CONTENT_ACCESS", "DIGITAL_SUBSCRIPTION_PROBLEM")
    ),
    AMAZON_DEVICES(
        List.of("echo", "alexa", "fire tv", "fire stick", "device"),
        List.of("ECHO_PROBLEM", "ALEXA_PROBLEM", "FIRE_TV_OR_FIRE_TV_STICK_PROBLEM", "DEVICE_SETUP", "DEVICE_CONNECTIVITY", "DEVICE_NOT_WORKING", "GENERAL_DEVICE_QUESTION")
    ),
    KINDLE_AND_READING(
        List.of("kindle", "book", "read", "ebook"),
        List.of("KINDLE_OR_READING_GENERAL", "KINDLE_OR_EBOOK_ISSUE", "KINDLE_BOOK_PURCHASE_OR_PRICING", "KINDLE_BOOK_DOWNLOAD", "KINDLE_BOOK_ACCESS", "KINDLE_DEVICE_PROBLEM")
    ),
    AMAZON_PAY(
        List.of("amazon pay", "wallet"),
        List.of("AMAZON_PAY_PAYMENT_OR_TRANSACTION", "AMAZON_PAY_BALANCE", "AMAZON_PAY_CASHBACK_OR_REWARDS", "AMAZON_PAY_REFUND", "AMAZON_PAY_ACCOUNT", "AMAZON_PAY_FRAUD_OR_SUSPICIOUS_TRANSACTION")
    ),
    ACCOUNT_AND_LOGIN(
        List.of("account", "login", "password", "locked", "email"),
        List.of("CANNOT_LOGIN", "PASSWORD_PROBLEM", "ACCOUNT_ACCESS_OR_DETAILS", "EMAIL_OR_PHONE_CHANGE", "ACCOUNT_LOCKED", "ACCOUNT_VERIFICATION", "HACKED_OR_COMPROMISED_ACCOUNT")
    ),
    ADDRESS_AND_DELIVERY_PREFERENCES(
        List.of("address", "locker", "instruction", "door"),
        List.of("ADDRESS_PROBLEM_OR_CHANGE", "ADD_OR_REMOVE_ADDRESS", "WRONG_DELIVERY_ADDRESS", "DELIVERY_LOCATION_OR_SAFE_PLACE", "DELIVERY_PREFERENCE")
    ),
    PROMOTIONS_AND_DISCOUNTS(
        List.of("discount", "promo", "code", "offer", "sale"),
        List.of("PROMOTION_OR_DISCOUNT", "COUPON_PROBLEM", "DISCOUNT_NOT_APPLIED", "OFFER_ELIGIBILITY", "CASHBACK_OR_PROMOTIONAL_OFFER")
    ),
    GIFT_CARDS(
        List.of("gift card", "redeem", "balance"),
        List.of("GIFT_CARD_PURCHASE", "GIFT_CARD_REDEMPTION", "GIFT_CARD_BALANCE", "GIFT_CARD_NOT_RECEIVED", "GIFT_CARD_NOT_WORKING", "GIFT_CARD_CHARGE_OR_PAYMENT")
    ),
    MARKETPLACE_AND_SELLERS(
        List.of("seller", "third party", "marketplace", "contact seller"),
        List.of("THIRD_PARTY_SELLER_PROBLEM", "SELLER_COMMUNICATION", "SELLER_ORDER_OR_DELIVERY", "SELLER_RETURN_OR_REFUND", "SELLER_ACCOUNT", "SELLER_FRAUD_OR_COUNTERFEIT")
    ),
    PRODUCT_INFORMATION_AND_PRICING(
        List.of("price", "info", "stock", "available", "when will", "specs"),
        List.of("PRODUCT_PRICE_OR_PRICING", "PRODUCT_AVAILABILITY", "PRODUCT_INFORMATION", "PRODUCT_SPECIFICATION", "PRICE_DIFFERENCE_OR_PRICE_CHANGE", "GENERAL_PRODUCT_QUESTION")
    ),
    SHIPPING_PACKAGING_AND_LOGISTICS(
        List.of("box", "packaging", "courier", "driver", "carrier"),
        List.of("DAMAGED_PACKAGE", "OPEN_OR_TAMPERED_PACKAGE", "PACKAGING_PROBLEM", "SHIPPING_CARRIER_PROBLEM", "GENERAL_LOGISTICS_PROBLEM")
    ),
    CUSTOMER_SERVICE_EXPERIENCE(
        List.of("agent", "support", "rude", "helpful", "call", "chat"),
        List.of("POOR_CUSTOMER_SERVICE", "NO_OR_DELAYED_RESPONSE", "REPEATED_CONTACT", "UNRESOLVED_ISSUE", "GENERAL_SERVICE_COMPLAINT", "PRAISE_OR_APPRECIATION")
    ),
    PRIVACY_AND_SECURITY(
        List.of("privacy", "hack", "scam", "phishing", "fake"),
        List.of("ACCOUNT_SECURITY", "SUSPICIOUS_ACTIVITY", "UNAUTHORIZED_ACCESS", "FRAUD_CONCERN", "PERSONAL_INFORMATION_CONCERN", "SECURITY_OR_VERIFICATION_PROBLEM")
    ),
    GENERAL_INFORMATION_AND_NON_SUPPORT(
        List.of("thanks", "love", "great", "hello", "hi"),
        List.of("GENERAL_AMAZON_QUESTION", "GENERAL_INFORMATION", "GENERAL_COMPLAINT", "FEEDBACK", "PRAISE_OR_APPRECIATION", "SOCIAL_OR_CONVERSATIONAL", "OTHER_NON_ACTIONABLE_MESSAGE", "UNKNOWN")
    );

    private final List<String> keywords;
    private final List<String> subcategories;

    IntentTaxonomy(List<String> keywords, List<String> subcategories) {
        this.keywords = keywords;
        this.subcategories = subcategories;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getSubcategories() {
        return subcategories;
    }

    public boolean isValidSubcategory(String subcategory) {
        if (subcategory == null) return false;
        for (String sub : subcategories) {
            if (sub.equalsIgnoreCase(subcategory)) {
                return true;
            }
        }
        return false;
    }
}

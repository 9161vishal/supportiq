package com.supportiq.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum IntentTaxonomy {
    DELIVERY_AND_TRACKING(
        List.of("delivery", "tracking", "late", "arrived", "where is", "shipped", "delivered"),
        Map.of(
            "DELIVERY_LATE", List.of("late", "delayed", "not arrived"),
            "DELIVERY_DATE", List.of("when", "what time", "date", "expect"),
            "TRACKING_NOT_UPDATED", List.of("tracking", "update", "stuck", "no movement"),
            "PACKAGE_MARKED_DELIVERED", List.of("says delivered", "marked delivered", "not here", "didn't receive"),
            "DELIVERY_ATTEMPT_FAILED", List.of("attempt", "failed", "missed"),
            "GENERAL_DELIVERY_QUESTION", List.of("delivery", "package"),
            "COURIER_OR_DRIVER_PROBLEM", List.of("driver", "courier", "van", "left", "stole")
        )
    ),
    ORDER_MANAGEMENT(
        List.of("order", "cancel", "change", "update", "status"),
        Map.of(
            "ORDER_STATUS", List.of("status", "progress"),
            "CANCEL_ORDER", List.of("cancel", "stop"),
            "CHANGE_ORDER", List.of("change", "modify", "update"),
            "PLACE_ORDER", List.of("place", "buy"),
            "ORDER_CONFIRMATION", List.of("confirm", "confirmation"),
            "PRE_ORDER", List.of("preorder", "pre-order"),
            "DUPLICATE_ORDER", List.of("duplicate", "twice", "two times"),
            "GENERAL_ORDER_QUESTION", List.of("order")
        )
    ),
    RETURNS_AND_REFUNDS(
        List.of("return", "refund", "exchange", "money back", "defective"),
        Map.of(
            "RETURN_REQUEST", List.of("want to return", "how to return"),
            "RETURN_STATUS", List.of("return status", "received my return"),
            "RETURN_ELIGIBILITY", List.of("can i return", "eligible"),
            "RETURN_SHIPPING", List.of("shipping label", "postage", "drop off"),
            "REFUND_REQUEST", List.of("want a refund", "refund me", "money back"),
            "REFUND_PENDING", List.of("where is my refund", "still waiting", "refund status"),
            "REFUND_AMOUNT", List.of("wrong amount", "less", "partial"),
            "REFUND_AFTER_CANCELLATION_OR_RETURN", List.of("cancelled", "returned")
        )
    ),
    PRODUCT_PROBLEM(
        List.of("broken", "damaged", "doesn't work", "missing part", "quality"),
        Map.of(
            "DAMAGED_PRODUCT", List.of("broken", "damaged", "smashed", "scratched"),
            "DEFECTIVE_OR_NOT_WORKING", List.of("doesn't work", "defective", "faulty", "wont turn on"),
            "WRONG_PRODUCT", List.of("wrong", "incorrect", "not what i ordered"),
            "MISSING_ITEM_OR_COMPONENT", List.of("missing", "empty", "part"),
            "REPLACEMENT_REQUEST", List.of("replace", "send another"),
            "PRODUCT_QUALITY_PROBLEM", List.of("quality", "poor", "fake", "cheap"),
            "OTHER_PRODUCT_PROBLEM", List.of("issue")
        )
    ),
    PAYMENT_AND_BILLING(
        List.of("charge", "charged", "billing", "invoice", "credit card", "payment"),
        Map.of(
            "PAYMENT_FAILED", List.of("failed", "decline"),
            "PAYMENT_DECLINED", List.of("declined", "rejected"),
            "PAYMENT_VERIFICATION", List.of("verify", "verification", "check"),
            "UNEXPECTED_CHARGE", List.of("didn't authorize", "unexpected", "fraud", "scam"),
            "CHARGE_OR_BILLING_PROBLEM", List.of("charged twice", "wrong amount", "invoice"),
            "CARD_CHARGED", List.of("charged my card"),
            "GENERAL_PAYMENT_PROBLEM", List.of("payment", "pay")
        )
    ),
    PRIME_MEMBERSHIP(
        List.of("prime", "membership", "subscribe", "subscription", "renew"),
        Map.of(
            "GENERAL_PRIME_QUESTION", List.of("prime"),
            "PRIME_MEMBERSHIP_OR_BENEFITS", List.of("benefit", "video", "music", "reading"),
            "PRIME_TRIAL", List.of("trial", "free"),
            "PRIME_RENEWAL", List.of("renew", "auto"),
            "CANCEL_PRIME", List.of("cancel", "stop", "end"),
            "PRIME_MEMBERSHIP_CHARGE", List.of("charge", "fee"),
            "PRIME_DELIVERY_BENEFIT", List.of("delivery", "shipping", "2 day", "next day")
        )
    ),
    PRIME_VIDEO_AND_DIGITAL_CONTENT(
        List.of("video", "movie", "stream", "watch", "music", "app"),
        Map.of(
            "PRIME_VIDEO_GENERAL", List.of("video", "movie", "show", "tv"),
            "PLAYBACK_OR_TECHNICAL_PROBLEM", List.of("play", "buffer", "error", "won't load"),
            "CONTENT_AVAILABILITY", List.of("available", "missing", "removed", "region"),
            "MOVIE_OR_SHOW_PURCHASE_OR_RENTAL", List.of("buy", "rent", "purchase"),
            "DIGITAL_CONTENT_ACCESS", List.of("access", "login", "account"),
            "DIGITAL_SUBSCRIPTION_PROBLEM", List.of("subscribe", "channel")
        )
    ),
    AMAZON_DEVICES(
        List.of("echo", "alexa", "fire tv", "fire stick", "device"),
        Map.of(
            "ECHO_PROBLEM", List.of("echo", "dot", "show"),
            "ALEXA_PROBLEM", List.of("alexa", "voice", "understand"),
            "FIRE_TV_OR_FIRE_TV_STICK_PROBLEM", List.of("fire tv", "stick", "remote"),
            "DEVICE_SETUP", List.of("setup", "install", "connect"),
            "DEVICE_CONNECTIVITY", List.of("wifi", "network", "offline"),
            "DEVICE_NOT_WORKING", List.of("broken", "won't turn on", "dead"),
            "GENERAL_DEVICE_QUESTION", List.of("device", "hardware")
        )
    ),
    KINDLE_AND_READING(
        List.of("kindle", "book", "read", "ebook"),
        Map.of(
            "KINDLE_OR_READING_GENERAL", List.of("kindle", "reading"),
            "KINDLE_OR_EBOOK_ISSUE", List.of("book", "ebook", "format"),
            "KINDLE_BOOK_PURCHASE_OR_PRICING", List.of("buy", "price", "charge"),
            "KINDLE_BOOK_DOWNLOAD", List.of("download", "sync", "deliver"),
            "KINDLE_BOOK_ACCESS", List.of("open", "read", "library"),
            "KINDLE_DEVICE_PROBLEM", List.of("screen", "battery", "charge")
        )
    ),
    AMAZON_PAY(
        List.of("amazon pay", "wallet"),
        Map.of(
            "AMAZON_PAY_PAYMENT_OR_TRANSACTION", List.of("pay", "transaction", "payment"),
            "AMAZON_PAY_BALANCE", List.of("balance", "funds"),
            "AMAZON_PAY_CASHBACK_OR_REWARDS", List.of("cashback", "reward", "points"),
            "AMAZON_PAY_REFUND", List.of("refund", "return"),
            "AMAZON_PAY_ACCOUNT", List.of("account", "login", "locked"),
            "AMAZON_PAY_FRAUD_OR_SUSPICIOUS_TRANSACTION", List.of("fraud", "scam", "unauthorized")
        )
    ),
    ACCOUNT_AND_LOGIN(
        List.of("account", "login", "password", "locked", "email"),
        Map.of(
            "CANNOT_LOGIN", List.of("login", "sign in", "access"),
            "PASSWORD_PROBLEM", List.of("password", "reset", "forgot"),
            "ACCOUNT_ACCESS_OR_DETAILS", List.of("details", "update", "info"),
            "EMAIL_OR_PHONE_CHANGE", List.of("email", "phone", "change"),
            "ACCOUNT_LOCKED", List.of("locked", "suspended", "closed"),
            "ACCOUNT_VERIFICATION", List.of("verify", "code", "otp"),
            "HACKED_OR_COMPROMISED_ACCOUNT", List.of("hacked", "stolen", "compromised")
        )
    ),
    ADDRESS_AND_DELIVERY_PREFERENCES(
        List.of("address", "locker", "instruction", "door"),
        Map.of(
            "ADDRESS_PROBLEM_OR_CHANGE", List.of("change", "wrong", "update"),
            "ADD_OR_REMOVE_ADDRESS", List.of("add", "remove", "delete"),
            "WRONG_DELIVERY_ADDRESS", List.of("delivered to wrong", "neighbor"),
            "DELIVERY_LOCATION_OR_SAFE_PLACE", List.of("safe place", "porch", "garage"),
            "DELIVERY_PREFERENCE", List.of("preference", "instruction", "gate code")
        )
    ),
    PROMOTIONS_AND_DISCOUNTS(
        List.of("discount", "promo", "code", "offer", "sale"),
        Map.of(
            "PROMOTION_OR_DISCOUNT", List.of("promotion", "discount"),
            "COUPON_PROBLEM", List.of("coupon", "voucher"),
            "DISCOUNT_NOT_APPLIED", List.of("didn't apply", "not working", "invalid"),
            "OFFER_ELIGIBILITY", List.of("eligible", "qualify"),
            "CASHBACK_OR_PROMOTIONAL_OFFER", List.of("cashback", "reward")
        )
    ),
    GIFT_CARDS(
        List.of("gift card", "redeem", "balance"),
        Map.of(
            "GIFT_CARD_PURCHASE", List.of("buy", "purchase"),
            "GIFT_CARD_REDEMPTION", List.of("redeem", "claim", "apply"),
            "GIFT_CARD_BALANCE", List.of("balance", "remaining", "how much"),
            "GIFT_CARD_NOT_RECEIVED", List.of("didn't receive", "where is"),
            "GIFT_CARD_NOT_WORKING", List.of("invalid", "error", "won't work"),
            "GIFT_CARD_CHARGE_OR_PAYMENT", List.of("charge", "pay")
        )
    ),
    MARKETPLACE_AND_SELLERS(
        List.of("seller", "third party", "marketplace", "contact seller"),
        Map.of(
            "THIRD_PARTY_SELLER_PROBLEM", List.of("third party", "seller"),
            "SELLER_COMMUNICATION", List.of("contact", "message", "reply"),
            "SELLER_ORDER_OR_DELIVERY", List.of("ship", "deliver", "late"),
            "SELLER_RETURN_OR_REFUND", List.of("return", "refund", "deny"),
            "SELLER_ACCOUNT", List.of("store", "profile"),
            "SELLER_FRAUD_OR_COUNTERFEIT", List.of("fake", "scam", "counterfeit")
        )
    ),
    PRODUCT_INFORMATION_AND_PRICING(
        List.of("price", "info", "stock", "available", "when will", "specs"),
        Map.of(
            "PRODUCT_PRICE_OR_PRICING", List.of("price", "cost", "expensive"),
            "PRODUCT_AVAILABILITY", List.of("stock", "available", "when will"),
            "PRODUCT_INFORMATION", List.of("info", "details", "description"),
            "PRODUCT_SPECIFICATION", List.of("specs", "size", "color", "weight"),
            "PRICE_DIFFERENCE_OR_PRICE_CHANGE", List.of("dropped", "changed", "difference"),
            "GENERAL_PRODUCT_QUESTION", List.of("question", "product")
        )
    ),
    SHIPPING_PACKAGING_AND_LOGISTICS(
        List.of("box", "packaging", "courier", "driver", "carrier"),
        Map.of(
            "DAMAGED_PACKAGE", List.of("box", "crushed", "torn", "wet"),
            "OPEN_OR_TAMPERED_PACKAGE", List.of("open", "tampered", "cut"),
            "PACKAGING_PROBLEM", List.of("too big", "no padding", "waste"),
            "SHIPPING_CARRIER_PROBLEM", List.of("usps", "ups", "fedex", "royal mail", "dpd"),
            "GENERAL_LOGISTICS_PROBLEM", List.of("logistics", "warehouse")
        )
    ),
    CUSTOMER_SERVICE_EXPERIENCE(
        List.of("agent", "support", "rude", "helpful", "call", "chat"),
        Map.of(
            "POOR_CUSTOMER_SERVICE", List.of("rude", "unhelpful", "useless", "terrible", "bad"),
            "NO_OR_DELAYED_RESPONSE", List.of("waiting", "ignore", "no reply", "hold"),
            "REPEATED_CONTACT", List.of("again", "times", "keep asking", "loop"),
            "UNRESOLVED_ISSUE", List.of("still", "not fixed", "unresolved"),
            "GENERAL_SERVICE_COMPLAINT", List.of("complaint", "service", "support"),
            "PRAISE_OR_APPRECIATION", List.of("thanks", "great", "helpful", "awesome", "good")
        )
    ),
    PRIVACY_AND_SECURITY(
        List.of("privacy", "hack", "scam", "phishing", "fake"),
        Map.of(
            "ACCOUNT_SECURITY", List.of("secure", "password", "protect"),
            "SUSPICIOUS_ACTIVITY", List.of("suspicious", "weird", "unusual"),
            "UNAUTHORIZED_ACCESS", List.of("unauthorized", "login", "access"),
            "FRAUD_CONCERN", List.of("fraud", "scam", "fake"),
            "PERSONAL_INFORMATION_CONCERN", List.of("data", "privacy", "sell"),
            "SECURITY_OR_VERIFICATION_PROBLEM", List.of("verify", "code", "otp")
        )
    ),
    GENERAL_INFORMATION_AND_NON_SUPPORT(
        List.of("thanks", "love", "great", "hello", "hi"),
        Map.of(
            "GENERAL_AMAZON_QUESTION", List.of("amazon", "question"),
            "GENERAL_INFORMATION", List.of("info", "details"),
            "GENERAL_COMPLAINT", List.of("sucks", "hate", "terrible"),
            "FEEDBACK", List.of("suggest", "idea", "feedback"),
            "PRAISE_OR_APPRECIATION", List.of("love", "great", "awesome", "amazing"),
            "SOCIAL_OR_CONVERSATIONAL", List.of("hi", "hello", "hey", "how are you"),
            "OTHER_NON_ACTIONABLE_MESSAGE", List.of("lol", "lmao", "haha"),
            "UNKNOWN", List.of()
        )
    );

    private final List<String> keywords;
    private final Map<String, List<String>> subcategories;

    IntentTaxonomy(List<String> keywords, Map<String, List<String>> subcategories) {
        this.keywords = keywords;
        this.subcategories = subcategories;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public Map<String, List<String>> getSubcategoryKeywords() {
        return subcategories;
    }

    public List<String> getSubcategories() {
        return new ArrayList<>(subcategories.keySet());
    }

    public boolean isValidSubcategory(String subcategory) {
        if (subcategory == null) return false;
        for (String sub : subcategories.keySet()) {
            if (sub.equalsIgnoreCase(subcategory)) {
                return true;
            }
        }
        return false;
    }
}

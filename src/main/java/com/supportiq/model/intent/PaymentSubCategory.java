package com.supportiq.model.intent;

public enum PaymentSubCategory implements SubCategory {
    PAYMENT_FAILED,
    PAYMENT_DECLINED,
    PAYMENT_VERIFICATION,
    UNEXPECTED_CHARGE,
    CHARGE_OR_BILLING_PROBLEM,
    CARD_CHARGED,
    GENERAL_PAYMENT_PROBLEM
}
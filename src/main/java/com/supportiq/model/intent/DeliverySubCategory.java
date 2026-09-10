package com.supportiq.model.intent;

public enum DeliverySubCategory implements SubCategory {
    DELIVERY_LATE,
    DELIVERY_DATE,
    TRACKING_NOT_UPDATED,
    PACKAGE_MARKED_DELIVERED,
    DELIVERY_ATTEMPT_FAILED,
    GENERAL_DELIVERY_QUESTION,
    COURIER_OR_DRIVER_PROBLEM
}
package com.supportiq.model;

import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.SubCategory;

public class Intent {
    private MainCategory mainCategory;
    private SubCategory subCategory;
    private double confidence;

    public Intent(MainCategory mainCategory, SubCategory subCategory, double confidence) {
        if (mainCategory != null && subCategory != null) {
            boolean valid = false;
            switch (mainCategory) {
                case DELIVERY_AND_TRACKING: valid = subCategory instanceof com.supportiq.model.intent.DeliverySubCategory; break;
                case ORDER_MANAGEMENT: valid = subCategory instanceof com.supportiq.model.intent.OrderSubCategory; break;
                case RETURNS_AND_REFUNDS: valid = subCategory instanceof com.supportiq.model.intent.ReturnsSubCategory; break;
                case PRODUCT_PROBLEM: valid = subCategory instanceof com.supportiq.model.intent.ProductProblemSubCategory; break;
                case PAYMENT_AND_BILLING: valid = subCategory instanceof com.supportiq.model.intent.PaymentSubCategory; break;
                case PRIME_MEMBERSHIP: valid = subCategory instanceof com.supportiq.model.intent.PrimeMembershipSubCategory; break;
                case PRIME_VIDEO_AND_DIGITAL_CONTENT: valid = subCategory instanceof com.supportiq.model.intent.PrimeVideoSubCategory; break;
                case AMAZON_DEVICES: valid = subCategory instanceof com.supportiq.model.intent.AmazonDevicesSubCategory; break;
                case KINDLE_AND_READING: valid = subCategory instanceof com.supportiq.model.intent.KindleSubCategory; break;
                case AMAZON_PAY: valid = subCategory instanceof com.supportiq.model.intent.AmazonPaySubCategory; break;
                case ACCOUNT_AND_LOGIN: valid = subCategory instanceof com.supportiq.model.intent.AccountSubCategory; break;
                case ADDRESS_AND_DELIVERY_PREFERENCES: valid = subCategory instanceof com.supportiq.model.intent.AddressSubCategory; break;
                case PROMOTIONS_AND_DISCOUNTS: valid = subCategory instanceof com.supportiq.model.intent.PromotionsSubCategory; break;
                case GIFT_CARDS: valid = subCategory instanceof com.supportiq.model.intent.GiftCardSubCategory; break;
                case MARKETPLACE_AND_SELLERS: valid = subCategory instanceof com.supportiq.model.intent.MarketplaceSubCategory; break;
                case PRODUCT_INFORMATION_AND_PRICING: valid = subCategory instanceof com.supportiq.model.intent.ProductInformationSubCategory; break;
                case SHIPPING_PACKAGING_AND_LOGISTICS: valid = subCategory instanceof com.supportiq.model.intent.ShippingSubCategory; break;
                case CUSTOMER_SERVICE_EXPERIENCE: valid = subCategory instanceof com.supportiq.model.intent.CustomerServiceSubCategory; break;
                case PRIVACY_AND_SECURITY: valid = subCategory instanceof com.supportiq.model.intent.PrivacySubCategory; break;
                case GENERAL_INFORMATION_AND_NON_SUPPORT: valid = subCategory instanceof com.supportiq.model.intent.GeneralInformationSubCategory; break;
            }
            if (!valid) {
                throw new IllegalArgumentException("Invalid subcategory " + subCategory.getClass().getSimpleName() + " for main category " + mainCategory);
            }
        }
        this.mainCategory = mainCategory;
        this.subCategory = subCategory;
        this.confidence = confidence;
    }

    public MainCategory getMainCategory() {
        return mainCategory;
    }

    public SubCategory getSubCategory() {
        return subCategory;
    }

    public double getConfidence() {
        return confidence;
    }
}

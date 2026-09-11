package com.supportiq.model;

import com.supportiq.data.IntentTaxonomy;

public class Intent {
    private IntentTaxonomy category;
    private String subCategory;
    private double confidence;
    private boolean uncertain;

    public Intent(IntentTaxonomy category, String subCategory, double confidence, boolean uncertain) {
        this.category = category;
        this.subCategory = subCategory;
        this.confidence = confidence;
        this.uncertain = uncertain;
    }

    public IntentTaxonomy getCategory() {
        return category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isUncertain() {
        return uncertain;
    }

    @Override
    public String toString() {
        return "Intent{" +
                "category=" + category +
                ", subCategory='" + subCategory + '\'' +
                ", confidence=" + confidence +
                ", uncertain=" + uncertain +
                '}';
    }
}

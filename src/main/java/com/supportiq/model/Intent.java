package com.supportiq.model;

import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.SubCategory;

public class Intent {
    private MainCategory mainCategory;
    private SubCategory subCategory;
    private double confidence;

    public Intent(MainCategory mainCategory, SubCategory subCategory, double confidence) {
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

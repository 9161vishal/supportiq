package com.supportiq.model;

public class Intent {
    private String name;
    private double confidence;

    public Intent(String name, double confidence) {
        this.name = name;
        this.confidence = confidence;
    }

    public String getName() {
        return name;
    }

    public double getConfidence() {
        return confidence;
    }
}

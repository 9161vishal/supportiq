package com.supportiq.evaluation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MetricsCalculator {
    private final Map<String, Integer> truePositives = new HashMap<>();
    private final Map<String, Integer> falsePositives = new HashMap<>();
    private final Map<String, Integer> falseNegatives = new HashMap<>();
    private final Map<String, Integer> support = new HashMap<>();
    
    private final Map<String, Map<String, Integer>> confusionMatrix = new HashMap<>();
    
    private int correct = 0;
    private int totalValid = 0;
    private int totalInvalid = 0;

    public void addPrediction(String expected, String predicted) {
        if (expected == null || expected.isEmpty() || expected.equals("PENDING")) {
            return;
        }
        
        support.put(expected, support.getOrDefault(expected, 0) + 1);
        
        if (predicted == null || predicted.isEmpty() || predicted.equals("INVALID") || predicted.equals("NULL")) {
            totalInvalid++;
            falseNegatives.put(expected, falseNegatives.getOrDefault(expected, 0) + 1);
            return;
        }
        
        totalValid++;
        
        confusionMatrix.putIfAbsent(expected, new HashMap<>());
        confusionMatrix.get(expected).put(predicted, confusionMatrix.get(expected).getOrDefault(predicted, 0) + 1);

        if (expected.equals(predicted)) {
            correct++;
            truePositives.put(expected, truePositives.getOrDefault(expected, 0) + 1);
        } else {
            falseNegatives.put(expected, falseNegatives.getOrDefault(expected, 0) + 1);
            falsePositives.put(predicted, falsePositives.getOrDefault(predicted, 0) + 1);
        }
    }

    public void printReport(String title) {
        System.out.println("==================================================");
        System.out.println("METRICS: " + title);
        System.out.println("==================================================");
        int totalExamples = totalValid + totalInvalid;
        System.out.println("Valid Predictions:   " + totalValid);
        System.out.println("Invalid Predictions: " + totalInvalid);
        System.out.println("Total Evaluated:     " + totalExamples);
        
        if (totalExamples == 0) {
            System.out.println("Accuracy: NOT AVAILABLE (Insufficient valid human labels)");
            return;
        }
        
        double accuracy = (double) correct / totalExamples;
        System.out.printf("Overall Accuracy: %.4f\n", accuracy);
        
        Set<String> allClasses = new HashSet<>();
        allClasses.addAll(truePositives.keySet());
        allClasses.addAll(falsePositives.keySet());
        allClasses.addAll(falseNegatives.keySet());
        allClasses.addAll(support.keySet());
        
        double sumPrecision = 0;
        double sumRecall = 0;
        double sumF1 = 0;
        int numClasses = allClasses.size();
        
        if (numClasses > 0) {
            System.out.println("\nPer-Category Metrics:");
            System.out.printf("%-35s %-10s %-10s %-10s %-10s\n", "Category", "Precision", "Recall", "F1", "Support");
            System.out.println("--------------------------------------------------------------------------------");
            for (String cls : allClasses) {
                int tp = truePositives.getOrDefault(cls, 0);
                int fp = falsePositives.getOrDefault(cls, 0);
                int fn = falseNegatives.getOrDefault(cls, 0);
                int sup = support.getOrDefault(cls, 0);
                
                double precision = (tp + fp == 0) ? 0 : (double) tp / (tp + fp);
                double recall = (tp + fn == 0) ? 0 : (double) tp / (tp + fn);
                double f1 = (precision + recall == 0) ? 0 : 2 * (precision * recall) / (precision + recall);
                
                sumPrecision += precision;
                sumRecall += recall;
                sumF1 += f1;
                
                System.out.printf("%-35s %-10.4f %-10.4f %-10.4f %-10d\n", cls, precision, recall, f1, sup);
            }
            
            System.out.println("--------------------------------------------------------------------------------");
            System.out.printf("%-35s %-10.4f %-10.4f %-10.4f\n", "MACRO AVERAGE", sumPrecision / numClasses, sumRecall / numClasses, sumF1 / numClasses);
        }
        
        System.out.println("\nConfusion Matrix (Row=Expected, Col=Predicted):");
        for (String expected : confusionMatrix.keySet()) {
            System.out.print(expected + " -> ");
            for (Map.Entry<String, Integer> entry : confusionMatrix.get(expected).entrySet()) {
                System.out.print(entry.getKey() + ":" + entry.getValue() + "  ");
            }
            System.out.println();
        }
    }
}

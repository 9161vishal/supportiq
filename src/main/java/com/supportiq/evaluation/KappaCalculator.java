package com.supportiq.evaluation;

public class KappaCalculator {
    public static double calculate(int[][] confusionMatrix) {
        int total = 0;
        int agreement = 0;
        int[] rowSums = new int[confusionMatrix.length];
        int[] colSums = new int[confusionMatrix.length];

        for (int i = 0; i < confusionMatrix.length; i++) {
            for (int j = 0; j < confusionMatrix.length; j++) {
                total += confusionMatrix[i][j];
                rowSums[i] += confusionMatrix[i][j];
                colSums[j] += confusionMatrix[i][j];
                if (i == j) {
                    agreement += confusionMatrix[i][j];
                }
            }
        }

        if (total == 0) return 0.0;

        double po = (double) agreement / total;
        double pe = 0.0;
        for (int i = 0; i < confusionMatrix.length; i++) {
            pe += ((double) rowSums[i] * colSums[i]) / (total * total);
        }

        if (pe == 1.0) return 1.0;
        return (po - pe) / (1.0 - pe);
    }
}

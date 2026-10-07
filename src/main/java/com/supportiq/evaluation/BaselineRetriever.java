package com.supportiq.evaluation;

import com.supportiq.model.HistoricalCandidate;
import com.supportiq.data.TweetRecord;
import java.util.*;

public class BaselineRetriever {

    public String retrieveBestResponse(String query, List<HistoricalCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        Map<String, Double> queryTfIdf = computeTfIdf(query, Collections.singletonList(query));

        double bestScore = -1.0;
        String bestResponse = null;

        for (HistoricalCandidate candidate : candidates) {
            if (candidate.getInteractionPath() == null || candidate.getInteractionPath().isEmpty()) continue;
            
            String candidateQuery = candidate.getInteractionPath().get(0).getText();
            Map<String, Double> candTfIdf = computeTfIdf(candidateQuery, Collections.singletonList(candidateQuery)); // Simplified TF-IDF (essentially word overlap weighted by length)
            
            double score = cosineSimilarity(queryTfIdf, candTfIdf);
            if (score > bestScore) {
                bestScore = score;
                // Find first outbound response
                for (TweetRecord tr : candidate.getInteractionPath()) {
                    if (!tr.isInbound()) {
                        bestResponse = tr.getText();
                        break;
                    }
                }
            }
        }
        
        return bestResponse;
    }

    private Map<String, Double> computeTfIdf(String text, List<String> corpus) {
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        Map<String, Integer> tf = new HashMap<>();
        for (String w : words) {
            if (!w.isEmpty()) tf.put(w, tf.getOrDefault(w, 0) + 1);
        }
        
        Map<String, Double> tfIdf = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double termFreq = (double) e.getValue() / words.length;
            // IDF would require full corpus, simplify to 1.0 for this baseline
            tfIdf.put(e.getKey(), termFreq * 1.0);
        }
        return tfIdf;
    }

    private double cosineSimilarity(Map<String, Double> vec1, Map<String, Double> vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String key : vec1.keySet()) {
            double v1 = vec1.get(key);
            double v2 = vec2.getOrDefault(key, 0.0);
            dotProduct += v1 * v2;
            norm1 += v1 * v1;
        }

        for (double v2 : vec2.values()) {
            norm2 += v2 * v2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}

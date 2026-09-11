package com.supportiq.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class DiscoveryReportWriter {
    private final Path outDir;

    public DiscoveryReportWriter(String outDirPath) throws IOException {
        this.outDir = Paths.get(outDirPath);
        Files.createDirectories(outDir);
    }

    public void writeSummary(long totalInteractions, long validMessages, long missingIds, 
                             Map<String, Integer> categoryCounts) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("INTENT DISCOVERY SUMMARY REPORT\n");
        sb.append("==================================================\n");
        sb.append("Total Interactions Analyzed: ").append(totalInteractions).append("\n");
        sb.append("Valid Initial Customer Messages Found: ").append(validMessages).append("\n");
        sb.append("Messages with Missing/Unresolvable IDs: ").append(missingIds).append("\n\n");
        
        sb.append("Category Frequency (Heuristic Matches):\n");
        categoryCounts.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .forEach(e -> sb.append(String.format(" - %-35s : %d\n", e.getKey(), e.getValue())));

        Files.writeString(outDir.resolve("summary_report.txt"), sb.toString());
    }

    public void writeCategoryValidation(Map<IntentTaxonomy, List<String>> examples) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"categories\": [\n");
        
        IntentTaxonomy[] values = IntentTaxonomy.values();
        for (int i = 0; i < values.length; i++) {
            IntentTaxonomy tax = values[i];
            sb.append("    {\n");
            sb.append("      \"category\": \"").append(tax.name()).append("\",\n");
            sb.append("      \"examples\": [\n");
            List<String> exs = examples.get(tax);
            if (exs != null) {
                for (int j = 0; j < exs.size(); j++) {
                    sb.append("        \"").append(escapeJson(exs.get(j))).append("\"");
                    if (j < exs.size() - 1) sb.append(",");
                    sb.append("\n");
                }
            }
            sb.append("      ]\n");
            sb.append("    }");
            if (i < values.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        Files.writeString(outDir.resolve("category_validation.json"), sb.toString());
    }

    public void writeAmbiguousAndUncovered(List<String> ambiguous, List<String> uncovered) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        
        sb.append("  \"UNCOVERED_CASES\": [\n");
        for (int i = 0; i < uncovered.size(); i++) {
            sb.append("    \"").append(escapeJson(uncovered.get(i))).append("\"");
            if (i < uncovered.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        sb.append("  \"AMBIGUOUS_CASES\": [\n");
        for (int i = 0; i < ambiguous.size(); i++) {
            sb.append("    \"").append(escapeJson(ambiguous.get(i))).append("\"");
            if (i < ambiguous.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        
        sb.append("}\n");
        Files.writeString(outDir.resolve("ambiguous_and_uncovered.json"), sb.toString());
    }

    public void writeDiscoveredTerms(Map<String, Integer> termCounts) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"top_terms\": [\n");
        
        List<Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(termCounts.entrySet());
        sorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        
        int limit = Math.min(100, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            sb.append("    { \"term\": \"").append(escapeJson(e.getKey())).append("\", \"count\": ").append(e.getValue()).append(" }");
            if (i < limit - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        Files.writeString(outDir.resolve("discovered_terms.json"), sb.toString());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}

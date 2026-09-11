package com.supportiq.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentDiscoveryAnalyzer {

    private final Path csvPath;
    private final Path pathsJsonl;
    private final DiscoveryReportWriter writer;
    private final int limit;

    // Report stats
    private long totalInteractions = 0;
    private long validMessages = 0;
    private long missingIds = 0;
    
    private final Map<String, Integer> categoryCounts = new HashMap<>();
    private final Map<String, Integer> subcategoryCounts = new HashMap<>();
    
    // For taxonomy_evidence.json
    private final Map<String, Map<String, Object>> taxonomyEvidence = new HashMap<>();
    // Format: CategoryName -> { "examples": [], "confusing_intents": [], "subcategories": { SubCategoryName -> { "examples": [], "confusing_intents": [] } } }

    private final List<String> ambiguousCases = new ArrayList<>();
    private final List<String> uncoveredCases = new ArrayList<>();

    private final Map<String, Integer> termCounts = new HashMap<>();

    private static final Set<String> STOP_WORDS = Set.of(
        "the", "and", "a", "to", "of", "in", "i", "is", "that", "it", "on", "you", 
        "this", "for", "but", "with", "are", "have", "be", "at", "or", "as", "was", 
        "so", "if", "out", "not", "my", "your", "we", "me", "they", "just", "it's",
        "i'm", "can", "do", "how", "from", "when", "about", "would", "like", "get",
        "what", "up", "an", "will", "has", "no", "why", "there", "don't", "been",
        "please", "amazon", "amazonhelp", "hi", "hello", "thanks", "help", "order"
    );

    private static class RootInfo {
        int bestDepth = Integer.MAX_VALUE;
        String text = null;
        long tweetId = -1;
    }

    public IntentDiscoveryAnalyzer(Path csvPath, Path pathsJsonl, String outDirPath, int limit) throws IOException {
        this.csvPath = csvPath;
        this.pathsJsonl = pathsJsonl;
        this.writer = new DiscoveryReportWriter(outDirPath);
        this.limit = limit;
        
        for (IntentTaxonomy tax : IntentTaxonomy.values()) {
            categoryCounts.put(tax.name(), 0);
            
            Map<String, Object> catEvidence = new HashMap<>();
            catEvidence.put("examples", new ArrayList<String>());
            catEvidence.put("confusing_intents", new ArrayList<String>());
            
            Map<String, Map<String, Object>> subEvidenceMap = new HashMap<>();
            for (String sub : tax.getSubcategories()) {
                subcategoryCounts.put(sub, 0);
                Map<String, Object> subEvidence = new HashMap<>();
                subEvidence.put("examples", new ArrayList<String>());
                subEvidence.put("confusing_intents", new ArrayList<String>());
                subEvidenceMap.put(sub, subEvidence);
            }
            catEvidence.put("subcategories", subEvidenceMap);
            taxonomyEvidence.put(tax.name(), catEvidence);
        }
        categoryCounts.put("UNCOVERED_CASES", 0);
        categoryCounts.put("AMBIGUOUS_CASES", 0);
    }

    public void run() throws Exception {
        System.out.println("Starting Intent Discovery Analysis...");
        
        TweetPathIndex index = new TweetPathIndex(500_000);
        Map<Long, RootInfo> roots = new HashMap<>(100_000);
        
        loadPaths(index, roots);
        System.out.println("Loaded " + totalInteractions + " interaction roots into index.");
        
        extractAndAnalyzeTexts(index, roots);
        
        System.out.println("Writing discovery reports...");
        writer.writeSummary(totalInteractions, validMessages, missingIds, categoryCounts, subcategoryCounts);
        writer.writeTaxonomyEvidence(taxonomyEvidence);
        writer.writeTaxonomyDecision(taxonomyEvidence, subcategoryCounts);
        writer.writeAmbiguousAndUncovered(ambiguousCases, uncoveredCases);
        writer.writeDiscoveredTerms(termCounts);
        System.out.println("Analysis complete.");
    }

    private void loadPaths(TweetPathIndex index, Map<Long, RootInfo> roots) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(pathsJsonl)) {
            String line;
            Pattern rootPattern = Pattern.compile("\"rootTweetId\":\"(\\d+)\"");
            // simple regex to find all quoted IDs in the line
            Pattern idPattern = Pattern.compile("\"(\\d+)\""); 
            
            while ((line = br.readLine()) != null) {
                Matcher rootMatcher = rootPattern.matcher(line);
                if (rootMatcher.find()) {
                    long rootId = Long.parseLong(rootMatcher.group(1));
                    roots.put(rootId, new RootInfo());
                    
                    // The line contains "paths":[["123","124"],["123","125"]]
                    // We can just parse the path arrays
                    // A simple way is to find the array starts and parse each
                    int pathsIdx = line.indexOf("\"paths\":[");
                    if (pathsIdx != -1) {
                        String pathsStr = line.substring(pathsIdx + 9, line.length() - 1);
                        // split by "],[" to process each path
                        String[] paths = pathsStr.split("\\],\\[");
                        for (String p : paths) {
                            Matcher m = idPattern.matcher(p);
                            int depth = 0;
                            while (m.find()) {
                                long id = Long.parseLong(m.group(1));
                                index.put(id, rootId, depth);
                                depth++;
                            }
                        }
                    }
                    
                    totalInteractions++;
                    if (limit > 0 && totalInteractions >= limit) {
                        break;
                    }
                }
            }
        }
    }

    private void extractAndAnalyzeTexts(TweetPathIndex index, Map<Long, RootInfo> roots) throws Exception {
        System.out.println("Streaming CSV to extract earliest customer messages...");
        try (CsvReader reader = CsvReader.read(csvPath)) {
            while (reader.hasNext()) {
                TweetRecord record = reader.next();
                if (record.getTweet_id() == null || record.getTweet_id().trim().isEmpty()) continue;
                
                long tweetId = -1;
                try {
                    tweetId = Long.parseLong(record.getTweet_id());
                } catch (NumberFormatException ignored) {}
                
                if (tweetId != -1 && index.contains(tweetId)) {
                    // It's in one of our paths
                    // We only want the *earliest customer message*
                    // Customers have inbound == True. (Or author_id != "AmazonHelp")
                    if (record.isInbound()) {
                        long rootId = index.getRootId(tweetId);
                        int depth = index.getDepth(tweetId);
                        
                        RootInfo rootInfo = roots.get(rootId);
                        if (rootInfo != null && depth < rootInfo.bestDepth) {
                            rootInfo.bestDepth = depth;
                            rootInfo.text = record.getText();
                            rootInfo.tweetId = tweetId;
                        }
                    }
                }
            }
        }
        
        System.out.println("Finished streaming CSV. Analyzing resolved messages...");
        
        for (RootInfo rootInfo : roots.values()) {
            if (rootInfo.text != null) {
                analyzeMessage(String.valueOf(rootInfo.tweetId), rootInfo.text);
                validMessages++;
            } else {
                missingIds++;
            }
        }
    }

    private void analyzeMessage(String tweetId, String rawText) {
        if (rawText == null) return;
        String text = rawText.toLowerCase();
        
        // Count terms
        String[] words = text.replaceAll("[^a-z ]", "").split("\\s+");
        for (String w : words) {
            if (w.length() > 2 && !STOP_WORDS.contains(w)) {
                termCounts.put(w, termCounts.getOrDefault(w, 0) + 1);
            }
        }
        
        List<IntentTaxonomy> categoryMatches = new ArrayList<>();
        List<String> subcategoryMatches = new ArrayList<>();
        
        for (IntentTaxonomy tax : IntentTaxonomy.values()) {
            boolean categoryMatched = false;
            for (String keyword : tax.getKeywords()) {
                if (text.contains(keyword)) {
                    categoryMatches.add(tax);
                    categoryMatched = true;
                    break;
                }
            }
            
            if (categoryMatched) {
                // Find matching subcategories within this category
                for (Map.Entry<String, List<String>> entry : tax.getSubcategoryKeywords().entrySet()) {
                    for (String subKeyword : entry.getValue()) {
                        if (text.contains(subKeyword)) {
                            subcategoryMatches.add(entry.getKey());
                            break;
                        }
                    }
                }
            }
        }
        
        String cleanRecord = tweetId + " : " + rawText.replace("\n", " ");

        if (categoryMatches.isEmpty()) {
            categoryCounts.put("UNCOVERED_CASES", categoryCounts.get("UNCOVERED_CASES") + 1);
            if (uncoveredCases.size() < 50) {
                uncoveredCases.add(cleanRecord);
            }
        } else if (categoryMatches.size() == 1) {
            IntentTaxonomy matchedTax = categoryMatches.get(0);
            String catName = matchedTax.name();
            categoryCounts.put(catName, categoryCounts.get(catName) + 1);
            
            Map<String, Object> catEvidence = taxonomyEvidence.get(catName);
            List<String> catExamples = (List<String>) catEvidence.get("examples");
            if (catExamples.size() < 10) catExamples.add(cleanRecord);

            if (subcategoryMatches.size() == 1) {
                String sub = subcategoryMatches.get(0);
                subcategoryCounts.put(sub, subcategoryCounts.get(sub) + 1);
                
                Map<String, Map<String, Object>> subEvidenceMap = (Map<String, Map<String, Object>>) catEvidence.get("subcategories");
                if (subEvidenceMap.containsKey(sub)) {
                    List<String> subExamples = (List<String>) subEvidenceMap.get(sub).get("examples");
                    if (subExamples.size() < 10) subExamples.add(cleanRecord);
                }
            } else if (subcategoryMatches.size() > 1) {
                // Ambiguous subcategories within the same category
                Map<String, Map<String, Object>> subEvidenceMap = (Map<String, Map<String, Object>>) catEvidence.get("subcategories");
                for (String sub : subcategoryMatches) {
                    if (subEvidenceMap.containsKey(sub)) {
                        List<String> confusing = (List<String>) subEvidenceMap.get(sub).get("confusing_intents");
                        String combo = String.join(" & ", subcategoryMatches);
                        if (!confusing.contains(combo)) confusing.add(combo);
                    }
                }
            }
        } else {
            categoryCounts.put("AMBIGUOUS_CASES", categoryCounts.get("AMBIGUOUS_CASES") + 1);
            if (ambiguousCases.size() < 50) {
                List<String> matchedNames = new ArrayList<>();
                for (IntentTaxonomy m : categoryMatches) matchedNames.add(m.name());
                ambiguousCases.add(cleanRecord + " [Matched: " + String.join(", ", matchedNames) + "]");
            }
            // Track confusing neighbors for categories
            for (IntentTaxonomy matchedTax : categoryMatches) {
                List<String> confusing = (List<String>) taxonomyEvidence.get(matchedTax.name()).get("confusing_intents");
                List<String> others = new ArrayList<>();
                for (IntentTaxonomy o : categoryMatches) if (o != matchedTax) others.add(o.name());
                String conflictStr = String.join(" & ", others);
                if (!confusing.contains(conflictStr)) confusing.add(conflictStr);
            }
        }
    }
}

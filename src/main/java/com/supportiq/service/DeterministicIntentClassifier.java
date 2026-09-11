package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashSet;

public class DeterministicIntentClassifier implements IntentClassifier {

    private static final Set<String> GENERIC_KEYWORDS = Set.of(
        "order", "prime", "delivery", "account", "payment", "pay", 
        "return", "product", "package", "hi", "hello", "hey", 
        "broken", "device", "amazon", "thanks", "help", "issue", 
        "question", "info", "details", "status", "cancel", "change"
    );

    @Override
    public Intent classify(CustomerMessage message) {
        if (message == null || message.getText() == null) {
            return null;
        }

        String text = message.getText(); // keep original case for regex, compile with CASE_INSENSITIVE

        List<IntentTaxonomy> strongCategoryMatches = new ArrayList<>();
        List<IntentTaxonomy> weakCategoryMatches = new ArrayList<>();
        
        // Maps Category -> List of Subcategories
        java.util.Map<IntentTaxonomy, List<String>> categoryToStrongSubcategories = new java.util.HashMap<>();
        java.util.Map<IntentTaxonomy, List<String>> categoryToWeakSubcategories = new java.util.HashMap<>();

        for (IntentTaxonomy tax : IntentTaxonomy.values()) {
            boolean hasStrongCategory = false;
            boolean hasWeakCategory = false;

            for (String keyword : tax.getKeywords()) {
                if (matchesKeyword(text, keyword)) {
                    if (isGeneric(keyword)) {
                        hasWeakCategory = true;
                    } else {
                        hasStrongCategory = true;
                    }
                }
            }

            if (hasStrongCategory) {
                strongCategoryMatches.add(tax);
            } else if (hasWeakCategory) {
                weakCategoryMatches.add(tax);
            }

            if (hasStrongCategory || hasWeakCategory) {
                List<String> strongSubs = new ArrayList<>();
                List<String> weakSubs = new ArrayList<>();
                
                for (Map.Entry<String, List<String>> entry : tax.getSubcategoryKeywords().entrySet()) {
                    for (String subKeyword : entry.getValue()) {
                        if (matchesKeyword(text, subKeyword)) {
                            if (isGeneric(subKeyword)) {
                                if (!weakSubs.contains(entry.getKey())) {
                                    weakSubs.add(entry.getKey());
                                }
                            } else {
                                if (!strongSubs.contains(entry.getKey())) {
                                    strongSubs.add(entry.getKey());
                                }
                            }
                        }
                    }
                }
                
                if (!strongSubs.isEmpty()) {
                    categoryToStrongSubcategories.put(tax, strongSubs);
                }
                if (!weakSubs.isEmpty()) {
                    categoryToWeakSubcategories.put(tax, weakSubs);
                }
            }
        }

        // 1. Conflict Handling: If multiple categories strongly matched
        if (strongCategoryMatches.size() > 1) {
            return new Intent(strongCategoryMatches.get(0), "AMBIGUOUS_CATEGORY", 0.0, true);
        }

        // 2. We need exactly one strong category
        if (strongCategoryMatches.size() == 1) {
            IntentTaxonomy matchedTax = strongCategoryMatches.get(0);
            List<String> strongSubs = categoryToStrongSubcategories.getOrDefault(matchedTax, new ArrayList<>());
            
            if (strongSubs.size() > 1) {
                return new Intent(matchedTax, "AMBIGUOUS_SUBCATEGORY", 0.0, true);
            } else if (strongSubs.size() == 1) {
                return new Intent(matchedTax, strongSubs.get(0), 1.0, false);
            } else {
                // If there are weak subcategories but no strong ones, it is ambiguous subcategory
                // Do NOT fall back to general just because a weak keyword matched.
                return new Intent(matchedTax, "AMBIGUOUS_SUBCATEGORY", 0.0, true);
            }
        }

        // 3. If there are only weak category matches
        if (!weakCategoryMatches.isEmpty()) {
            if (weakCategoryMatches.size() == 1) {
                return new Intent(weakCategoryMatches.get(0), "AMBIGUOUS_SUBCATEGORY", 0.0, true);
            } else {
                return new Intent(weakCategoryMatches.get(0), "AMBIGUOUS_CATEGORY", 0.0, true);
            }
        }

        return null;
    }

    private boolean isGeneric(String keyword) {
        return GENERIC_KEYWORDS.contains(keyword.toLowerCase());
    }

    private boolean matchesKeyword(String text, String keyword) {
        // Regex word boundary matching
        String regex = "\\b" + Pattern.quote(keyword) + "\\b";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).find();
    }
}

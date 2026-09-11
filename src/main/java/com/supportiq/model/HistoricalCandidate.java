package com.supportiq.model;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetRecord;
import java.util.List;

public class HistoricalCandidate {
    private final IntentTaxonomy category;
    private final String subcategory;
    private final List<TweetRecord> interactionPath;

    public HistoricalCandidate(IntentTaxonomy category, String subcategory, List<TweetRecord> interactionPath) {
        this.category = category;
        this.subcategory = subcategory;
        this.interactionPath = interactionPath;
    }

    public IntentTaxonomy getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public List<TweetRecord> getInteractionPath() {
        return interactionPath;
    }

    public String getRootTweetId() {
        if (interactionPath != null && !interactionPath.isEmpty()) {
            return interactionPath.get(0).getTweet_id();
        }
        return null;
    }
}

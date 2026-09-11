package com.supportiq.model;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetRecord;

import java.util.List;

public class HistoricalConversation {
    private final IntentTaxonomy category;
    private final String subcategory;
    private final String rootTweetId;
    private final List<List<TweetRecord>> paths;

    public HistoricalConversation(IntentTaxonomy category, String subcategory, String rootTweetId, List<List<TweetRecord>> paths) {
        this.category = category;
        this.subcategory = subcategory;
        this.rootTweetId = rootTweetId;
        this.paths = paths;
    }

    public IntentTaxonomy getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public String getRootTweetId() {
        return rootTweetId;
    }

    public List<List<TweetRecord>> getPaths() {
        return paths;
    }
}

package com.supportiq.model;

import java.util.List;

public class HistoricalCase {
    private String rootTweetId;
    private List<List<String>> paths;

    public HistoricalCase(String rootTweetId, List<List<String>> paths) {
        this.rootTweetId = rootTweetId;
        this.paths = paths;
    }

    public String getRootTweetId() {
        return rootTweetId;
    }

    public List<List<String>> getPaths() {
        return paths;
    }
}

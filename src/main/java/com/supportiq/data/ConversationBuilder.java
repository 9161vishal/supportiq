package com.supportiq.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversationBuilder {
    private final Map<String, List<String>> parentToChildren = new LinkedHashMap<>();

    public void addRecord(TweetRecord record) {
        String parent = record.getIn_response_to_tweet_id();
        if (parent != null && !parent.trim().isEmpty()) {
            parentToChildren.computeIfAbsent(parent, k -> new ArrayList<>()).add(record.getTweet_id());
        }
    }

    public List<List<String>> extractPaths(String rootId) {
        List<List<String>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        currentPath.add(rootId);
        dfs(rootId, currentPath, allPaths);
        return allPaths;
    }

    private void dfs(String currentId, List<String> currentPath, List<List<String>> allPaths) {
        List<String> children = parentToChildren.getOrDefault(currentId, new ArrayList<>());
        if (children.isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
            return;
        }
        for (String child : children) {
            currentPath.add(child);
            dfs(child, currentPath, allPaths);
            currentPath.remove(currentPath.size() - 1);
        }
    }
}

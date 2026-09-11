package com.supportiq.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConversationBuilder {
    
    private static class Node {
        String tweetId;
        String createdAt;
        Node(String tweetId, String createdAt) {
            this.tweetId = tweetId;
            this.createdAt = createdAt;
        }
    }
    
    private final Map<String, Map<String, Node>> parentToChildren = new HashMap<>();

    public void addRecord(String tweetId, String parentId, String createdAt) {
        if (parentId != null && !parentId.trim().isEmpty()) {
            parentToChildren
                .computeIfAbsent(parentId, k -> new HashMap<>())
                .putIfAbsent(tweetId, new Node(tweetId, createdAt));
        }
    }

    public List<List<String>> extractPaths(String rootId) {
        List<List<String>> allPaths = new ArrayList<>();
        List<String> currentPath = new ArrayList<>();
        currentPath.add(rootId);
        
        Set<String> visited = new HashSet<>();
        visited.add(rootId);
        
        dfs(rootId, currentPath, allPaths, visited);
        return allPaths;
    }

    private void dfs(String currentId, List<String> currentPath, List<List<String>> allPaths, Set<String> visited) {
        Map<String, Node> childrenMap = parentToChildren.get(currentId);
        if (childrenMap == null || childrenMap.isEmpty()) {
            allPaths.add(new ArrayList<>(currentPath));
            return;
        }

        // Sort children deterministically by created_at, then tweet_id
        List<Node> sortedChildren = childrenMap.values().stream().sorted((a, b) -> {
            String ca = a.createdAt != null ? a.createdAt : "";
            String cb = b.createdAt != null ? b.createdAt : "";
            int cmp = ca.compareTo(cb);
            if (cmp != 0) return cmp;
            return a.tweetId.compareTo(b.tweetId);
        }).collect(Collectors.toList());

        boolean advanced = false;
        for (Node child : sortedChildren) {
            String childId = child.tweetId;
            if (!visited.contains(childId)) {
                advanced = true;
                visited.add(childId);
                currentPath.add(childId);
                
                dfs(childId, currentPath, allPaths, visited);
                
                currentPath.remove(currentPath.size() - 1);
                visited.remove(childId);
            }
        }
        
        if (!advanced) {
            allPaths.add(new ArrayList<>(currentPath));
        }
    }
}

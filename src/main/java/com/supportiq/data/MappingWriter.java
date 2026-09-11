package com.supportiq.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import com.supportiq.model.intent.MainCategory;
import com.supportiq.model.intent.SubCategory;

public class MappingWriter {
    
    private final String baseDir;

    public MappingWriter(String baseDir) {
        this.baseDir = baseDir;
    }

    public void writeMapping(MainCategory mainCategory, SubCategory subCategory, String rootTweetId, List<List<String>> paths) throws IOException {
        if (rootTweetId == null || rootTweetId.trim().isEmpty()) {
            throw new IllegalArgumentException("rootTweetId must be present");
        }
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("paths must not be null or empty");
        }
        
        for (List<String> path : paths) {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("Path cannot be empty");
            }
            if (!rootTweetId.equals(path.get(0))) {
                throw new IllegalArgumentException("rootTweetId must match the first ID of each path");
            }
            for (String id : path) {
                if (id == null || id.trim().isEmpty()) {
                    throw new IllegalArgumentException("Paths must contain valid IDs");
                }
            }
        }

        Path dirPath = Paths.get(baseDir, mainCategory.name(), subCategory.name());
        Files.createDirectories(dirPath);
        
        Path filePath = dirPath.resolve("mapping.jsonl");
        
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"rootTweetId\":\"").append(rootTweetId).append("\",");
        jsonBuilder.append("\"paths\":[");
        for (int i = 0; i < paths.size(); i++) {
            jsonBuilder.append("[");
            List<String> path = paths.get(i);
            for (int j = 0; j < path.size(); j++) {
                jsonBuilder.append("\"").append(path.get(j)).append("\"");
                if (j < path.size() - 1) {
                    jsonBuilder.append(",");
                }
            }
            jsonBuilder.append("]");
            if (i < paths.size() - 1) {
                jsonBuilder.append(",");
            }
        }
        jsonBuilder.append("]}").append(System.lineSeparator());
        
        Files.writeString(filePath, jsonBuilder.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}

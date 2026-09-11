package com.supportiq.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class IntermediateMappingWriter {
    
    private final Path filePath;

    public IntermediateMappingWriter(String baseDir) throws IOException {
        Path dirPath = Paths.get(baseDir);
        Files.createDirectories(dirPath);
        this.filePath = dirPath.resolve("intermediate_paths.jsonl");
        // Clear previous runs
        Files.deleteIfExists(this.filePath);
        Files.createFile(this.filePath);
    }

    public void writePaths(String rootTweetId, List<List<String>> paths) throws IOException {
        if (rootTweetId == null || rootTweetId.trim().isEmpty()) {
            throw new IllegalArgumentException("rootTweetId must be present");
        }
        if (paths == null || paths.isEmpty()) {
            return; // Nothing to write
        }
        
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
        
        Files.writeString(filePath, jsonBuilder.toString(), StandardOpenOption.APPEND);
    }
}

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

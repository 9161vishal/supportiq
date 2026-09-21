package com.supportiq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.HistoricalCandidate;
import com.supportiq.model.Intent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class HistoricalRetriever {

    private final Path csvPath;
    private final String mappingBaseDir;
    private final int defaultMaxCandidates;
    private final ObjectMapper objectMapper;

    private CsvOffsetReader csvOffsetReader;
    private TweetOffsetIndex offsetIndex;

    public HistoricalRetriever(
            @Value("${supportiq.data.raw-csv:data/raw/twcs/twcs.csv}") String csvPathStr,
            @Value("${supportiq.data.mapping-dir:data/mapping/AmazonHelp}") String mappingBaseDir,
            @Value("${supportiq.retrieval.max-candidates:10}") int defaultMaxCandidates) {
        this.csvPath = Paths.get(csvPathStr);
        this.mappingBaseDir = mappingBaseDir;
        this.defaultMaxCandidates = defaultMaxCandidates;
        this.objectMapper = new ObjectMapper();
    }
    
    // For testing
    public void setCsvOffsetReaderAndIndex(CsvOffsetReader reader, TweetOffsetIndex index) {
        this.csvOffsetReader = reader;
        this.offsetIndex = index;
    }

    @PostConstruct
    public void init() throws IOException {
        System.out.println("Initializing HistoricalRetriever... Building offset index for " + csvPath);
        File csvFile = csvPath.toFile();
        if (csvFile.exists()) {
            this.csvOffsetReader = new CsvOffsetReader(csvPath);
            this.offsetIndex = this.csvOffsetReader.buildIndex();
            System.out.println("Offset index built. Indexed " + offsetIndex.size() + " tweets.");
        } else {
            System.out.println("WARNING: CSV file " + csvPath + " does not exist. Retrieval will not function.");
            this.offsetIndex = new TweetOffsetIndex(16);
        }
    }

    @PreDestroy
    public void destroy() throws IOException {
        if (this.csvOffsetReader != null) {
            this.csvOffsetReader.close();
        }
    }

    public List<HistoricalCandidate> retrieve(Intent intent) {
        return retrieve(intent, defaultMaxCandidates);
    }

    public List<HistoricalCandidate> retrieve(Intent intent, int limit) {
        if (intent == null || intent.getCategory() == null || intent.getSubCategory() == null) {
            return Collections.emptyList();
        }
        
        if (!intent.getCategory().isValidSubcategory(intent.getSubCategory())) {
            return Collections.emptyList();
        }
        
        File mappingFile = new File(mappingBaseDir + "/" + intent.getCategory().name() + "/" + intent.getSubCategory() + "/mapping.jsonl");
        if (!mappingFile.exists()) {
            return Collections.emptyList();
        }
        
        List<HistoricalCandidate> candidates = new ArrayList<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(mappingFile))) {
            String line;
            while ((line = br.readLine()) != null && candidates.size() < limit) {
                if (line.trim().isEmpty()) continue;
                
                JsonNode node;
                try {
                    node = objectMapper.readTree(line);
                } catch (Exception e) {
                    continue; // malformed JSON
                }
                
                JsonNode pathsArray = node.path("paths");
                if (pathsArray.isArray() && pathsArray.size() > 0) {
                    for (JsonNode pathArray : pathsArray) {
                        if (candidates.size() >= limit) break;
                        
                        List<TweetRecord> interactionPath = new ArrayList<>();
                        boolean pathValid = true;
                        
                        for (JsonNode idNode : pathArray) {
                            String tweetIdStr = idNode.asText();
                            long tweetId = -1;
                            try {
                                tweetId = Long.parseLong(tweetIdStr);
                            } catch (NumberFormatException e) {
                                pathValid = false;
                                break;
                            }
                            
                            long offset = offsetIndex.getOffset(tweetId);
                            if (offset == -1) {
                                pathValid = false; // missing ID in original CSV
                                break;
                            }
                            
                            TweetRecord record = csvOffsetReader.readRecordAt(offset);
                            interactionPath.add(record);
                        }
                        
                        if (pathValid && !interactionPath.isEmpty()) {
                            candidates.add(new HistoricalCandidate(intent.getCategory(), intent.getSubCategory(), interactionPath));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading mapping file: " + e.getMessage());
        }
        
        return candidates;
    }
}

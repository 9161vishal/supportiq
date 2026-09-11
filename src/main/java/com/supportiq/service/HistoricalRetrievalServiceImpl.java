package com.supportiq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.AmazonHelpFilter;
import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.HistoricalConversation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class HistoricalRetrievalServiceImpl implements HistoricalRetrievalService {

    private final CsvOffsetReader csvReader;
    private final TweetOffsetIndex offsetIndex;
    private final String mappingBaseDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public HistoricalRetrievalServiceImpl(String workingCsvPath, String mappingBaseDir) throws IOException {
        this.mappingBaseDir = mappingBaseDir;
        this.csvReader = new CsvOffsetReader(Paths.get(workingCsvPath));
        this.offsetIndex = this.csvReader.buildIndex();
    }

    @Override
    public List<HistoricalConversation> retrieve(IntentTaxonomy category, String subcategory, int limit) {
        if (category == null || subcategory == null || subcategory.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        // Validate subcategory exists in category
        boolean validSubcategory = false;
        for (String sub : category.getSubcategories()) {
            if (sub.equals(subcategory)) {
                validSubcategory = true;
                break;
            }
        }
        if (!validSubcategory) {
            return Collections.emptyList();
        }

        Path mappingFile = Paths.get(mappingBaseDir, category.name(), subcategory, "mapping.jsonl");
        if (!mappingFile.toFile().exists()) {
            return Collections.emptyList();
        }

        List<HistoricalConversation> results = new ArrayList<>();
        Set<String> seenRootIds = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();

        try (Stream<String> lines = Files.lines(mappingFile)) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext() && results.size() < limit) {
                String line = iterator.next();
                if (line.trim().isEmpty()) continue;

                JsonNode node = mapper.readTree(line);
                String rootId = node.get("rootTweetId").asText();

                if (!seenRootIds.add(rootId)) {
                    continue; // Skip duplicate conversation root
                }

                List<List<TweetRecord>> hydratedPaths = new ArrayList<>();
                boolean skipConversation = false;

                JsonNode pathsArray = node.get("paths");
                for (JsonNode pathNode : pathsArray) {
                    List<TweetRecord> currentPath = new ArrayList<>();
                    Set<String> pathTweetIds = new HashSet<>();
                    StringBuilder pathSignature = new StringBuilder();
                    boolean pathInvalid = false;

                    for (int i = 0; i < pathNode.size(); i++) {
                        String tweetId = pathNode.get(i).asText();
                        
                        // Check duplicate within path
                        if (!pathTweetIds.add(tweetId)) {
                            pathInvalid = true;
                            break;
                        }
                        pathSignature.append(tweetId).append("-");

                        long offset = offsetIndex.getOffset(Long.parseLong(tweetId));
                        if (offset == -1) {
                            pathInvalid = true; // Unresolved ID
                            break;
                        }

                        TweetRecord record = csvReader.readRecordAt(offset);
                        currentPath.add(record);

                        // Relationship validation
                        if (i > 0) {
                            String expectedParent = pathNode.get(i - 1).asText();
                            String actualParent = record.getIn_response_to_tweet_id();
                            if (actualParent == null || !actualParent.equals(expectedParent)) {
                                pathInvalid = true; // Mismatched edge
                                break;
                            }
                        }

                        // Role validation (AmazonHelp vs Customer vs Cross-Company)
                        if (!record.isInbound()) {
                            // Outbound from company: MUST be AmazonHelp
                            if (!AmazonHelpFilter.AMAZON_HELP_AUTHOR_ID.equalsIgnoreCase(record.getAuthor_id())) {
                                pathInvalid = true; // Cross-company contamination or invalid role
                                break;
                            }
                        } else {
                            // Inbound from customer: MUST NOT be AmazonHelp
                            if (AmazonHelpFilter.AMAZON_HELP_AUTHOR_ID.equalsIgnoreCase(record.getAuthor_id())) {
                                pathInvalid = true; // Invalid role
                                break;
                            }
                        }
                    }

                    if (pathInvalid || !seenPaths.add(pathSignature.toString())) {
                        skipConversation = true;
                        break;
                    }

                    hydratedPaths.add(currentPath);
                }

                if (!skipConversation && !hydratedPaths.isEmpty()) {
                    results.add(new HistoricalConversation(category, subcategory, rootId, hydratedPaths));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading mapping file: " + e.getMessage());
        }

        return results;
    }
}

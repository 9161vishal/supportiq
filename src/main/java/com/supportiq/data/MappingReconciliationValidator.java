package com.supportiq.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class MappingReconciliationValidator {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> globalRoots = new HashSet<>();
    private final Set<String> globalPaths = new HashSet<>();
    private final Set<String> allMappedIds = new HashSet<>();

    public void validate(String mappingDirStr, String workingCsvPathStr) throws Exception {
        System.out.println("Starting strict mapping reconciliation validation...");

        Path mappingDir = Paths.get(mappingDirStr);
        File auditFile = mappingDir.resolve("mapping_audit.json").toFile();
        if (!auditFile.exists()) {
            throw new IllegalStateException("mapping_audit.json not found");
        }

        JsonNode audit = mapper.readTree(auditFile);
        long totalCandidates = audit.get("total_candidates").asLong();
        long mapped = audit.get("confidently_mapped").asLong();
        long ambiguous = audit.get("ambiguous").asLong();
        long unmapped = audit.get("unmapped").asLong();
        long skipped = audit.get("missing_ids").asLong();
        
        long sum = mapped + ambiguous + unmapped + skipped;
        if (sum != totalCandidates) {
            throw new IllegalStateException("Global totals do not reconcile! Sum: " + sum + " vs Candidates: " + totalCandidates);
        }

        JsonNode taxonomyDist = audit.get("taxonomy_distribution");
        long sumFromDist = 0;
        Map<String, Long> categorySums = new HashMap<>();

        try (Stream<Path> paths = Files.walk(mappingDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().equals("mapping.jsonl"))
                 .forEach(p -> validateMappingFile(p, categorySums, taxonomyDist));
        }

        for (long count : categorySums.values()) {
            sumFromDist += count;
        }

        if (sumFromDist != mapped) {
            throw new IllegalStateException("Category sums (" + sumFromDist + ") do not equal total mapped (" + mapped + ")");
        }

        System.out.println("Validating all mapped IDs against working dataset...");
        Set<String> workingDatasetIds = new HashSet<>();
        try (CsvReader reader = CsvReader.read(Paths.get(workingCsvPathStr))) {
            while (reader.hasNext()) {
                TweetRecord r = reader.next();
                workingDatasetIds.add(r.getTweet_id());
            }
        }
        for (String id : allMappedIds) {
            if (!workingDatasetIds.contains(id)) {
                throw new IllegalStateException("Mapped ID not found in working dataset: " + id);
            }
        }

        System.out.println("Validating working dataset against original TWCS and caching relationships...");
        Set<String> twcsIds = new HashSet<>();
        Map<String, String> originalRelationships = new HashMap<>();
        try (CsvReader reader = CsvReader.read(Paths.get("data/raw/twcs.csv"))) {
            while (reader.hasNext()) {
                TweetRecord r = reader.next();
                twcsIds.add(r.getTweet_id());
                if (r.getIn_response_to_tweet_id() != null && !r.getIn_response_to_tweet_id().isEmpty()) {
                    originalRelationships.put(r.getTweet_id(), r.getIn_response_to_tweet_id());
                }
            }
        }
        for (String id : workingDatasetIds) {
            if (!twcsIds.contains(id)) {
                throw new IllegalStateException("Working dataset ID not found in TWCS: " + id);
            }
        }

        System.out.println("Validating parent-child relationships and structure of all mapped paths...");
        for (String pathStr : globalPaths) {
            String[] ids = pathStr.split("-");
            for (int i = 1; i < ids.length; i++) {
                String child = ids[i];
                String expectedParent = ids[i-1];
                String actualParent = originalRelationships.get(child);
                if (actualParent == null || !actualParent.equals(expectedParent)) {
                    throw new IllegalStateException("Parent-child relationship violation in path " + pathStr + ". Child " + child + " expected parent " + expectedParent + " but original TWCS says " + actualParent);
                }
            }
        }

        System.out.println("Validation SUCCESS. All mappings are unique, paths are valid, and counts reconcile exactly.");
        
        Map<String, Object> report = new HashMap<>();
        report.put("total_candidates", totalCandidates);
        report.put("total_mapped", mapped);
        report.put("total_ambiguous", ambiguous);
        report.put("total_unmapped", unmapped);
        report.put("total_skipped", skipped);
        report.put("category_sums_match_mapped", true);
        report.put("global_sums_match_candidates", true);
        report.put("duplicate_roots_found", 0);
        report.put("duplicate_paths_found", 0);
        report.put("all_mapped_ids_in_working", true);
        report.put("all_working_ids_in_twcs", true);

        File reportFile = Paths.get("data", "analysis", "mapping_reconciliation_report.json").toFile();
        reportFile.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, report);
    }

    private void validateMappingFile(Path p, Map<String, Long> categorySums, JsonNode taxonomyDist) {
        String subcategory = p.getParent().getFileName().toString();
        String category = p.getParent().getParent().getFileName().toString();
        
        long fileCount = 0;
        try (Stream<String> lines = Files.lines(p)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.trim().isEmpty()) continue;
                JsonNode node = mapper.readTree(line);
                String rootId = node.get("rootTweetId").asText();
                
                if (!globalRoots.add(rootId)) {
                    throw new IllegalStateException("Duplicate root ID found across files: " + rootId);
                }
                
                JsonNode pathsArray = node.get("paths");
                for (JsonNode pathNode : pathsArray) {
                    StringBuilder pathStr = new StringBuilder();
                    Set<String> idsInPath = new HashSet<>();
                    for (JsonNode idNode : pathNode) {
                        String id = idNode.asText();
                        if (!idsInPath.add(id)) {
                            throw new IllegalStateException("Duplicate tweet ID within a path: " + id + " in path " + pathNode.toString());
                        }
                        pathStr.append(id).append("-");
                        allMappedIds.add(id);
                    }
                    if (!globalPaths.add(pathStr.toString())) {
                        throw new IllegalStateException("Duplicate path structure found: " + pathStr);
                    }
                }
                fileCount++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading " + p, e);
        }

        String key = category + "->" + subcategory;
        long auditCount = taxonomyDist.has(key) ? taxonomyDist.get(key).asLong() : 0;
        if (fileCount != auditCount) {
            throw new IllegalStateException("File count for " + key + " (" + fileCount + ") does not match audit count (" + auditCount + ")");
        }

        categorySums.put(category, categorySums.getOrDefault(category, 0L) + fileCount);
    }
}

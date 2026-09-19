package com.supportiq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.data.AmazonHelpFilter;
import com.supportiq.data.CsvOffsetReader;
import com.supportiq.data.IntentTaxonomy;
import com.supportiq.data.TweetOffsetIndex;
import com.supportiq.data.TweetRecord;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.service.HistoricalRetrievalService;
import com.supportiq.service.HistoricalRetrievalServiceImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class HistoricalRetrievalValidator {

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("STARTING HISTORICAL RETRIEVAL VALIDATION");
        System.out.println("==================================================");

        String workingCsv = Paths.get("data/working/AmazonHelp/amazonhelp_relevant_tweets.csv").toAbsolutePath()
                .toString();
        String mappingBaseDir = Paths.get("data/mapping/AmazonHelp").toAbsolutePath().toString();

        HistoricalRetrievalService service = new HistoricalRetrievalServiceImpl(workingCsv, mappingBaseDir);
        ObjectMapper mapper = new ObjectMapper();

        CsvOffsetReader csvReader = new CsvOffsetReader(Paths.get(workingCsv));
        TweetOffsetIndex offsetIndex = csvReader.buildIndex();

        long totalBuckets = 0;
        long totalMappedCandidates = 0;
        long totalValidRetrievable = 0;
        long totalInvalidCandidates = 0;

        long unresolvedIds = 0;
        long duplicatePaths = 0;
        long relationshipFailures = 0;
        long crossCompanyContamination = 0;

        Map<String, Object> bucketReports = new TreeMap<>();
        Set<String> globalSeenPaths = new HashSet<>();

        long startTime = System.currentTimeMillis();

        for (IntentTaxonomy category : IntentTaxonomy.values()) {
            for (String subcategory : category.getSubcategories()) {
                Path mappingFile = Paths.get(mappingBaseDir, category.name(), subcategory, "mapping.jsonl");
                if (!mappingFile.toFile().exists())
                    continue;

                totalBuckets++;
                long bucketMapped = 0;
                long bucketValid = 0;

                // We measure performance of retrieval
                long startRetrieve = System.nanoTime();
                List<HistoricalConversation> retrieved = service.retrieve(category, subcategory, Integer.MAX_VALUE);
                long endRetrieve = System.nanoTime();

                bucketValid = retrieved.size();
                totalValidRetrievable += bucketValid;

                // Now audit the file manually for exact failure reasons
                try (Stream<String> lines = Files.lines(mappingFile)) {
                    for (String line : (Iterable<String>) lines::iterator) {
                        if (line.trim().isEmpty())
                            continue;
                        bucketMapped++;

                        JsonNode node = mapper.readTree(line);
                        JsonNode pathsArray = node.get("paths");

                        boolean convValid = true;
                        for (JsonNode pathNode : pathsArray) {
                            StringBuilder pathSig = new StringBuilder();
                            Set<String> localPathIds = new HashSet<>();

                            boolean pathOk = true;
                            for (int i = 0; i < pathNode.size(); i++) {
                                String id = pathNode.get(i).asText();
                                if (!localPathIds.add(id)) {
                                    duplicatePaths++;
                                    pathOk = false;
                                    break;
                                }
                                pathSig.append(id).append("-");

                                long offset = offsetIndex.getOffset(Long.parseLong(id));
                                if (offset == -1) {
                                    unresolvedIds++;
                                    pathOk = false;
                                    break;
                                }

                                TweetRecord record = csvReader.readRecordAt(offset);
                                if (i > 0) {
                                    String expectedParent = pathNode.get(i - 1).asText();
                                    if (record.getIn_response_to_tweet_id() == null
                                            || !record.getIn_response_to_tweet_id().equals(expectedParent)) {
                                        relationshipFailures++;
                                        pathOk = false;
                                        break;
                                    }
                                }

                                if (!record.isInbound()) {
                                    if (!AmazonHelpFilter.AMAZON_HELP_AUTHOR_ID
                                            .equalsIgnoreCase(record.getAuthor_id())) {
                                        crossCompanyContamination++;
                                        pathOk = false;
                                        break;
                                    }
                                } else {
                                    if (AmazonHelpFilter.AMAZON_HELP_AUTHOR_ID
                                            .equalsIgnoreCase(record.getAuthor_id())) {
                                        crossCompanyContamination++;
                                        pathOk = false;
                                        break;
                                    }
                                }
                            }

                            if (pathOk && !globalSeenPaths.add(pathSig.toString())) {
                                duplicatePaths++;
                                pathOk = false;
                            }

                            if (!pathOk) {
                                convValid = false;
                            }
                        }

                        if (!convValid) {
                            totalInvalidCandidates++;
                        }
                    }
                }

                totalMappedCandidates += bucketMapped;

                Map<String, Object> bReport = new HashMap<>();
                bReport.put("mapped", bucketMapped);
                bReport.put("retrieved", bucketValid);
                bReport.put("retrieval_ms", (endRetrieve - startRetrieve) / 1_000_000.0);
                if (bucketMapped != bucketValid) {
                    bReport.put("mismatch", bucketMapped - bucketValid);
                }

                bucketReports.put(category.name() + "->" + subcategory, bReport);
            }
        }

        csvReader.close();

        long endTime = System.currentTimeMillis();

        Map<String, Object> finalReport = new LinkedHashMap<>();
        finalReport.put("total_buckets", totalBuckets);
        finalReport.put("total_mapped_candidates", totalMappedCandidates);
        finalReport.put("total_valid_retrievable", totalValidRetrievable);
        finalReport.put("total_invalid_rejected", totalInvalidCandidates);

        Map<String, Object> failures = new LinkedHashMap<>();
        failures.put("unresolved_ids", unresolvedIds);
        failures.put("duplicate_paths", duplicatePaths);
        failures.put("relationship_failures", relationshipFailures);
        failures.put("cross_company_contamination", crossCompanyContamination);
        finalReport.put("failures", failures);

        finalReport.put("execution_time_ms", (endTime - startTime));
        finalReport.put("coverage_reconciled",
                (totalMappedCandidates == (totalValidRetrievable + totalInvalidCandidates)));
        finalReport.put("buckets", bucketReports);

        File reportFile = Paths.get("data", "analysis", "historical_retrieval_validation_report.json").toFile();
        reportFile.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, finalReport);

        System.out.println("Buckets Processed: " + totalBuckets);
        System.out.println("Mapped Candidates: " + totalMappedCandidates);
        System.out.println("Valid Retrievable: " + totalValidRetrievable);
        System.out.println("Invalid Rejected:  " + totalInvalidCandidates);
        System.out.println(
                "Coverage Reconciled: " + (totalMappedCandidates == (totalValidRetrievable + totalInvalidCandidates)));

        System.out.println("\n--- Failures ---");
        System.out.println("Unresolved IDs: " + unresolvedIds);
        System.out.println("Duplicate Paths/IDs: " + duplicatePaths);
        System.out.println("Relationship Failures: " + relationshipFailures);
        System.out.println("Cross-Company/Role Invalid: " + crossCompanyContamination);

        boolean success = (unresolvedIds == 0 && duplicatePaths == 0 && relationshipFailures == 0
                && crossCompanyContamination == 0
                && (totalMappedCandidates == totalValidRetrievable + totalInvalidCandidates));

        System.out.println("\n==================================================");
        if (success) {
            System.out.println("HISTORICAL RETRIEVAL MODULE LOCKED");
        } else {
            System.out.println("FAILED! Quality gates not met.");
        }
        System.out.println("==================================================");
    }
}

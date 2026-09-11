package com.supportiq.data;

import java.nio.file.Path;
import java.util.*;

public class AmazonHelpPipeline {

    private final Path csvPath;
    private final String outputDir;

    public AmazonHelpPipeline(Path csvPath, String outputDir) {
        this.csvPath = csvPath;
        this.outputDir = outputDir;
    }

    public void process() throws Exception {
        long startTime = System.currentTimeMillis();
        Runtime rt = Runtime.getRuntime();
        
        LongArray amazonHelpTweets = new LongArray(100_000);
        LongArray otherCompanyTweets = new LongArray(1_000_000);
        LongArray allIds = new LongArray(3_000_000);
        EdgeGraph graph = new EdgeGraph(3_000_000);

        AmazonHelpFilter filter = new AmazonHelpFilter();
        RelationshipValidator validator = new RelationshipValidator();

        System.out.println("PASS 1: Scanning and building primitive indexes...");
        long totalRows = 0;
        try (CsvReader reader = CsvReader.read(csvPath)) {
            while (reader.hasNext()) {
                TweetRecord record = reader.next();
                totalRows++;
                
                String tweetIdStr = record.getTweet_id();
                if (tweetIdStr == null || tweetIdStr.trim().isEmpty()) {
                    validator.incrementMalformed();
                    continue;
                }
                
                long tweetId;
                try {
                    tweetId = Long.parseLong(tweetIdStr);
                } catch (NumberFormatException e) {
                    validator.incrementMalformed();
                    continue;
                }
                
                allIds.add(tweetId);

                String parentIdStr = record.getIn_response_to_tweet_id();
                if (parentIdStr != null && !parentIdStr.trim().isEmpty()) {
                    try {
                        long parentId = Long.parseLong(parentIdStr);
                        graph.addEdge(tweetId, parentId);
                    } catch (NumberFormatException e) {
                        validator.incrementMalformed();
                    }
                }

                if (filter.isAmazonHelp(record)) {
                    amazonHelpTweets.add(tweetId);
                } else if (!record.isInbound()) {
                    otherCompanyTweets.add(tweetId);
                }
            }
        }

        System.out.println("Sorting indexes...");
        allIds.sort();
        otherCompanyTweets.sort();
        
        System.out.println("Computing valid AmazonHelp interactions...");
        LongHashSet validIds = computeValidInteractionIds(amazonHelpTweets, otherCompanyTweets, graph, validator);

        System.out.println("Discovering interaction roots...");
        LongHashSet interactionRoots = new LongHashSet(validIds.size());
        long[] validArray = validIds.toArray();
        graph.sortByChild();
        for (long id : validArray) {
            long parent = graph.getParent(id);
            if (parent == -1L || !validIds.contains(parent)) {
                interactionRoots.add(id);
            }
        }

        System.out.println("PASS 2: Reconstructing valid conversations...");
        ConversationBuilder builder = new ConversationBuilder();
        try (CsvReader reader = CsvReader.read(csvPath)) {
            while (reader.hasNext()) {
                TweetRecord record = reader.next();
                String tIdStr = record.getTweet_id();
                if (tIdStr != null && !tIdStr.trim().isEmpty()) {
                    try {
                        long tweetId = Long.parseLong(tIdStr);
                        if (validIds.contains(tweetId)) {
                            builder.addRecord(tIdStr, record.getIn_response_to_tweet_id(), record.getCreated_at());
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }
        }

        long heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

        System.out.println("Writing outputs...");
        IntermediateMappingWriter writer = new IntermediateMappingWriter(outputDir);
        long[] rootArray = interactionRoots.toArray();
        Arrays.sort(rootArray);
        long totalPaths = 0;
        long mappingLines = 0;
        for (long rootId : rootArray) {
            List<List<String>> paths = builder.extractPaths(String.valueOf(rootId));
            writer.writePaths(String.valueOf(rootId), paths);
            totalPaths += paths.size();
            mappingLines++;
        }
        
        long endTime = System.currentTimeMillis();
        
        System.out.println("==================================================");
        System.out.println("FINAL REPORT METRICS");
        System.out.println("==================================================");
        System.out.println("Total CSV rows processed: " + totalRows);
        System.out.println("Total AmazonHelp support tweets: " + amazonHelpTweets.size());
        System.out.println("Total other support-company tweets detected: " + otherCompanyTweets.size());
        
        // Count unique IDs
        long uniqueIds = 0;
        if (allIds.size() > 0) {
            uniqueIds = 1;
            for (int i = 1; i < allIds.size(); i++) {
                if (allIds.get(i) != allIds.get(i-1)) uniqueIds++;
            }
        }
        
        System.out.println("Total unique tweet IDs: " + uniqueIds);
        System.out.println("Total graph edges: " + graph.size());
        System.out.println("Total valid AmazonHelp interaction IDs: " + validIds.size());
        System.out.println("Total interaction roots: " + interactionRoots.size());
        System.out.println("Total generated paths: " + totalPaths);
        System.out.println("Total excluded IDs: " + (uniqueIds - validIds.size()));
        validator.report();
        System.out.println("Runtime: " + (endTime - startTime) + " ms");
        System.out.println("Observed JVM heap usage (approx): " + heapUsed + " MB");
        System.out.println("Number of mapping lines written: " + mappingLines);
        System.out.println("Pipeline completed successfully.");
    }

    private LongHashSet computeValidInteractionIds(
            LongArray amazonHelpTweets,
            LongArray otherCompanyTweets,
            EdgeGraph graph,
            RelationshipValidator validator) {

        LongHashSet validIds = new LongHashSet(amazonHelpTweets.size() * 10);

        // 1. Add all ancestors of AmazonHelp tweets
        graph.sortByChild();
        for (int i = 0; i < amazonHelpTweets.size(); i++) {
            long ahId = amazonHelpTweets.get(i);
            validIds.add(ahId);
            
            long curr = graph.getParent(ahId);
            while (curr != -1L) {
                if (!validIds.add(curr)) {
                    validator.incrementCycle();
                    break; // Cycle detected, stop tracing
                }
                curr = graph.getParent(curr);
            }
        }

        // 2. Add all valid descendants of AmazonHelp tweets
        graph.sortByParent(); // Important: Switch graph to allow O(log N) child lookup!
        
        LongArray q = new LongArray(1000);
        for (int i = 0; i < amazonHelpTweets.size(); i++) {
            q.add(amazonHelpTweets.get(i));
        }
        
        int head = 0;
        LongArray childrenBuf = new LongArray(10);
        while (head < q.size()) {
            long curr = q.get(head++);
            
            childrenBuf.clear();
            graph.getChildren(curr, childrenBuf);
            
            for (int j = 0; j < childrenBuf.size(); j++) {
                long child = childrenBuf.get(j);
                // Stop if we hit another company's tweet
                if (!otherCompanyTweets.contains(child)) {
                    if (validIds.add(child)) {
                        q.add(child);
                    }
                }
            }
        }

        return validIds;
    }
}

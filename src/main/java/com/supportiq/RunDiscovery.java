package com.supportiq;

import com.supportiq.data.IntentDiscoveryAnalyzer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RunDiscovery {
    public static void main(String[] args) throws Exception {
        Path csvPath = Paths.get("data/raw/twcs.csv").toAbsolutePath();
        Path pathsJsonl = Paths.get("data/mapping/AmazonHelp/intermediate_paths.jsonl").toAbsolutePath();
        String outDir = Paths.get("data/analysis/intent-discovery").toAbsolutePath().toString();
        
        // Pass -1 for no limit
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        
        System.out.println("Starting Phase 2B Step 2 Discovery");
        System.out.println("CSV Path: " + csvPath);
        System.out.println("Paths JSONL: " + pathsJsonl);
        System.out.println("Output Dir: " + outDir);
        System.out.println("Limit: " + (limit > 0 ? limit : "None"));
        
        long start = System.currentTimeMillis();
        IntentDiscoveryAnalyzer analyzer = new IntentDiscoveryAnalyzer(csvPath, pathsJsonl, outDir, limit);
        analyzer.run();
        long end = System.currentTimeMillis();
        
        System.gc();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        System.out.println("==================================================");
        System.out.println("PERFORMANCE METRICS");
        System.out.println("==================================================");
        System.out.println("Execution Time: " + (end - start) + " ms");
        System.out.println("Approximate JVM Heap Used: " + (usedMemory / (1024 * 1024)) + " MB");
    }
}

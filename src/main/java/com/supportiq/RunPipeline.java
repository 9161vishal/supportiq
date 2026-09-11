package com.supportiq;

import com.supportiq.data.AmazonHelpPipeline;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RunPipeline {
    public static void main(String[] args) throws Exception {
        Path csvPath = Paths.get("data/raw/twcs.csv").toAbsolutePath();
        String outputDir = Paths.get("data/mapping/AmazonHelp").toAbsolutePath().toString();
        
        System.out.println("Starting Phase 2B Step 1 Execution");
        System.out.println("CSV Path: " + csvPath);
        System.out.println("Output Dir: " + outputDir);
        
        AmazonHelpPipeline pipeline = new AmazonHelpPipeline(csvPath, outputDir);
        pipeline.process();
    }
}

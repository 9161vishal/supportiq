package com.supportiq.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class MappingQualitySampler {
    
    private final ObjectMapper mapper = new ObjectMapper();

    private static class Sample {
        String rootId;
        String pathStr;
        String text;
        String category;
        String subcategory;
    }

    public void generateSampleReport(String mappingDirStr, String twcsCsvPathStr) throws IOException {
        System.out.println("Generating Mapping Quality Sample Report...");
        Path mappingDir = Paths.get(mappingDirStr);
        
        // Initialize CsvReader
        CsvOffsetReader csvReader = new CsvOffsetReader(Paths.get(twcsCsvPathStr));
        TweetOffsetIndex offsetIndex = csvReader.buildIndex();
        
        List<Sample> allSamples = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(mappingDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().equals("mapping.jsonl"))
                 .forEach(p -> {
                     String subcategory = p.getParent().getFileName().toString();
                     String category = p.getParent().getParent().getFileName().toString();
                     
                     List<Sample> fileSamples = new ArrayList<>();
                     try (Stream<String> lines = Files.lines(p)) {
                         for (String line : (Iterable<String>) lines::iterator) {
                             if (line.trim().isEmpty()) continue;
                             JsonNode node = mapper.readTree(line);
                             Sample s = new Sample();
                             s.rootId = node.get("rootTweetId").asText();
                             s.category = category;
                             s.subcategory = subcategory;
                             s.pathStr = node.get("paths").toString();
                             fileSamples.add(s);
                         }
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                     
                     // Sort deterministically by rootId for consistent sampling
                     fileSamples.sort(Comparator.comparing(a -> Long.parseLong(a.rootId)));
                     
                     int sampleSize = fileSamples.size() <= 20 ? fileSamples.size() : 20;
                     // We select up to 20 deterministic examples. We will just pick the first 20.
                     // (or we could pick evenly spaced, but first 20 is deterministic).
                     for (int i = 0; i < sampleSize; i++) {
                         Sample s = fileSamples.get(i);
                         s.text = extractText(s.rootId, csvReader, offsetIndex);
                         allSamples.add(s);
                     }
                 });
        }
        
        File reportFile = Paths.get("data", "analysis", "mapping_quality_sample.txt").toFile();
        reportFile.getParentFile().mkdirs();
        
        try (PrintWriter writer = new PrintWriter(reportFile)) {
            writer.println("=========================================================");
            writer.println("MAPPING QUALITY SAMPLE REPORT");
            writer.println("=========================================================\n");
            
            String currentSubcat = "";
            for (Sample s : allSamples) {
                if (!s.subcategory.equals(currentSubcat)) {
                    writer.println("\n--- CATEGORY: " + s.category + " | SUBCATEGORY: " + s.subcategory + " ---");
                    currentSubcat = s.subcategory;
                }
                writer.println("Root ID: " + s.rootId);
                writer.println("Paths: " + s.pathStr);
                writer.println("Customer Text: " + s.text);
                writer.println("Verdict: [CORRECT / INCORRECT / UNCERTAIN]");
                writer.println();
            }
        }
        System.out.println("Mapping Quality Sample Report generated at " + reportFile.getAbsolutePath());
    }
    
    private String extractText(String rootId, CsvOffsetReader csvReader, TweetOffsetIndex index) {
        long id = Long.parseLong(rootId);
        long offset = index.getOffset(id);
        if (offset != -1) {
            try {
                TweetRecord rec = csvReader.readRecordAt(offset);
                if (rec != null) {
                    return rec.getText().replace("\n", " ").replace("\r", "");
                }
            } catch (Exception e) {
                return "ERROR_READING_TEXT";
            }
        }
        return "TEXT_NOT_FOUND";
    }

    public static void main(String[] args) throws IOException {
        new MappingQualitySampler().generateSampleReport("data/mapping/AmazonHelp", "data/raw/twcs.csv");
    }
}

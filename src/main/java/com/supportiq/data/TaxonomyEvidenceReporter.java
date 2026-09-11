package com.supportiq.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class TaxonomyEvidenceReporter {

    public void generateReport(String outputDir) throws IOException {
        System.out.println("Generating Taxonomy Evidence Report...");
        ObjectMapper mapper = new ObjectMapper();
        
        Path auditPath = Paths.get(outputDir, "mapping_audit.json");
        if (!auditPath.toFile().exists()) {
            throw new IOException("mapping_audit.json not found");
        }

        JsonNode audit = mapper.readTree(auditPath.toFile());
        JsonNode taxonomyDist = audit.get("taxonomy_distribution");
        
        Map<String, Object> evidenceReport = new TreeMap<>();
        
        // We iterate through all categories and subcategories defined in IntentTaxonomy
        for (IntentTaxonomy cat : IntentTaxonomy.values()) {
            for (String subcat : cat.getSubcategories()) {
                String key = cat.name() + "->" + subcat;
                long count = taxonomyDist.has(key) ? taxonomyDist.get(key).asLong() : 0;
                
                String status;
                if (count == 0) {
                    status = "UNMAPPED";
                } else if (subcat.equals("AMBIGUOUS_SUBCATEGORY") || cat.name().equals("GENERAL_INFORMATION_AND_NON_SUPPORT")) {
                    status = "REVIEW_REQUIRED";
                } else if (count < 10) {
                    status = "INSUFFICIENT_EVIDENCE";
                } else {
                    status = "SUPPORTED_BY_EVIDENCE";
                }
                
                Map<String, Object> details = new HashMap<>();
                details.put("count", count);
                details.put("status", status);
                
                if (status.equals("SUPPORTED_BY_EVIDENCE")) {
                    details.put("note", "Requires semantic review before final golden promotion.");
                }
                
                evidenceReport.put(key, details);
            }
        }
        
        File reportFile = Paths.get("data", "analysis", "taxonomy_evidence_report.json").toFile();
        reportFile.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile, evidenceReport);
        System.out.println("Taxonomy evidence report generated successfully.");
    }
}

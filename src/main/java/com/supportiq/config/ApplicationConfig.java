package com.supportiq.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.supportiq.service.RetrievalService;

@Configuration
public class ApplicationConfig {

    @Bean
    public com.supportiq.service.HistoricalRetrievalService historicalRetrievalService(
            @org.springframework.beans.factory.annotation.Value("${supportiq.csv.working:data/working/AmazonHelp/amazonhelp_relevant_tweets.csv}") String workingCsvPath,
            @org.springframework.beans.factory.annotation.Value("${supportiq.mapping.base-dir:data/mapping/AmazonHelp}") String mappingBaseDir)
            throws java.io.IOException {
        return new com.supportiq.service.HistoricalRetrievalServiceImpl(workingCsvPath, mappingBaseDir);
    }

    @Bean
    public RetrievalService retrievalService(
            com.supportiq.service.HistoricalRetrievalService historicalRetrievalService) {
        return new com.supportiq.service.RetrievalServiceImpl(historicalRetrievalService);
    }
}



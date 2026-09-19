package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.HistoricalConversation;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;

import java.util.List;

public class RetrievalServiceImpl implements RetrievalService {

    private final HistoricalRetrievalService historicalRetrievalService;

    public RetrievalServiceImpl(HistoricalRetrievalService historicalRetrievalService) {
        this.historicalRetrievalService = historicalRetrievalService;
    }

    @Override
    public RetrievedEvidence retrieve(CustomerMessage message, Intent intent) {
        if (intent == null || intent.getCategory() == null || intent.getSubCategory() == null) {
            return new RetrievedEvidence(List.of());
        }

        // We fetch candidates from the locked historical retrieval service.
        // It's a thin adapter. AI #2 will do the semantic relevance assessment.
        List<HistoricalConversation> candidates = historicalRetrievalService.retrieve(
                intent.getCategory(), 
                intent.getSubCategory(), 
                10 // Max candidates limit for the LLM context
        );

        return new RetrievedEvidence(candidates);
    }
}

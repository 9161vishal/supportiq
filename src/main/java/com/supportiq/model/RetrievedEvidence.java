package com.supportiq.model;

import java.util.List;

public class RetrievedEvidence {
    private List<HistoricalConversation> historicalCases;

    public RetrievedEvidence(List<HistoricalConversation> historicalCases) {
        this.historicalCases = historicalCases;
    }

    public List<HistoricalConversation> getHistoricalCases() {
        return historicalCases;
    }
}

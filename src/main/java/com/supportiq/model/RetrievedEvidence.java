package com.supportiq.model;

import java.util.List;

public class RetrievedEvidence {
    private List<HistoricalCase> historicalCases;

    public RetrievedEvidence(List<HistoricalCase> historicalCases) {
        this.historicalCases = historicalCases;
    }

    public List<HistoricalCase> getHistoricalCases() {
        return historicalCases;
    }
}

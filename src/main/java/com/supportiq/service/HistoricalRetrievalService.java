package com.supportiq.service;

import com.supportiq.data.IntentTaxonomy;
import com.supportiq.model.HistoricalConversation;

import java.util.List;

public interface HistoricalRetrievalService {
    /**
     * Retrieves historical conversation candidates for a given category and subcategory.
     * @param category The verified intent category
     * @param subcategory The verified subcategory
     * @param limit Maximum number of conversations to return
     * @return List of verified HistoricalConversation candidates
     */
    List<HistoricalConversation> retrieve(IntentTaxonomy category, String subcategory, int limit);
}

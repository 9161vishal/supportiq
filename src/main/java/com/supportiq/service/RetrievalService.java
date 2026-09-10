package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;
import com.supportiq.model.RetrievedEvidence;

public interface RetrievalService {
    RetrievedEvidence retrieve(CustomerMessage message, Intent intent);
}

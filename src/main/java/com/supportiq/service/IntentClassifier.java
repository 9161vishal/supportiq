package com.supportiq.service;

import com.supportiq.model.CustomerMessage;
import com.supportiq.model.Intent;

public interface IntentClassifier {
    Intent classify(CustomerMessage message);
}

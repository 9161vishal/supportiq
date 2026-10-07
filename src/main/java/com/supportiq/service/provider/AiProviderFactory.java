package com.supportiq.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;

public class AiProviderFactory {

    public static AiProvider create(String provider, String apiUrl, String apiKey, String model, HttpClient httpClient, long requestTimeoutSec, ObjectMapper objectMapper) {
        if ("groq".equalsIgnoreCase(provider)) {
            System.out.println("AI provider: groq");
            System.out.println("AI model: " + model);
            return new GroqAiProvider(apiKey, model, httpClient, requestTimeoutSec, objectMapper);
        } else if ("gemini".equalsIgnoreCase(provider) || provider == null || provider.isEmpty()) {
            return new GeminiAiProvider(apiUrl, apiKey, model, httpClient, requestTimeoutSec, objectMapper);
        } else {
            throw new IllegalStateException("Unsupported AI provider: " + provider);
        }
    }
}

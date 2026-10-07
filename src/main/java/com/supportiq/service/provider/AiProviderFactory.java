package com.supportiq.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;

public class AiProviderFactory {

    public static final String DEFAULT_GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    public static final String DEFAULT_GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static AiProvider create(String provider, String apiUrl, String apiKey, String model, HttpClient httpClient, long requestTimeoutSec, ObjectMapper objectMapper) {
        if ("groq".equalsIgnoreCase(provider)) {
            System.out.println("AI provider: groq");
            System.out.println("AI model: " + model);
            
            if (apiUrl == null || apiUrl.trim().isEmpty() || apiUrl.equals(DEFAULT_GEMINI_URL)) {
                apiUrl = DEFAULT_GROQ_URL;
            }
            
            return new GroqAiProvider(apiUrl, apiKey, model, httpClient, requestTimeoutSec, objectMapper);
        } else if ("gemini".equalsIgnoreCase(provider) || provider == null || provider.isEmpty()) {
            
            if (apiUrl == null || apiUrl.trim().isEmpty() || apiUrl.equals(DEFAULT_GROQ_URL)) {
                apiUrl = DEFAULT_GEMINI_URL;
            }
            
            return new GeminiAiProvider(apiUrl, apiKey, model, httpClient, requestTimeoutSec, objectMapper);
        } else {
            throw new IllegalStateException("Unsupported AI provider: " + provider);
        }
    }
}

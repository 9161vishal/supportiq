package com.supportiq.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class AiProviderFactoryTest {

    @Test
    void testCreateGeminiProvider() {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        AiProvider provider = AiProviderFactory.create("gemini", "url", "key", "model", client, 10, mapper);
        assertTrue(provider instanceof GeminiAiProvider);
    }

    @Test
    void testCreateGroqProvider() {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        AiProvider provider = AiProviderFactory.create("groq", "url", "key", "model", client, 10, mapper);
        assertTrue(provider instanceof GroqAiProvider);
    }

    @Test
    void testCreateDefaultProvider() {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        AiProvider provider = AiProviderFactory.create(null, "url", "key", "model", client, 10, mapper);
        assertTrue(provider instanceof GeminiAiProvider);
        
        provider = AiProviderFactory.create("", "url", "key", "model", client, 10, mapper);
        assertTrue(provider instanceof GeminiAiProvider);
    }

    @Test
    void testInvalidProviderThrowsException() {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            AiProviderFactory.create("invalid", "url", "key", "model", client, 10, mapper);
        });
        
        assertEquals("Unsupported AI provider: invalid", exception.getMessage());
    }
}

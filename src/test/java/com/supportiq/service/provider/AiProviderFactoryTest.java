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

    @Test
    void testGroqProviderDefaultUrlResolution() throws Exception {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        // Pass the Gemini default URL explicitly to simulate the regression scenario
        String geminiDefault = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
        AiProvider provider = AiProviderFactory.create("groq", geminiDefault, "key", "model", client, 10, mapper);
        
        assertTrue(provider instanceof GroqAiProvider);
        
        java.lang.reflect.Field urlField = GroqAiProvider.class.getDeclaredField("apiUrl");
        urlField.setAccessible(true);
        String actualUrl = (String) urlField.get(provider);
        
        assertEquals("https://api.groq.com/openai/v1/chat/completions", actualUrl);
    }

    @Test
    void testGeminiProviderDefaultUrlResolution() throws Exception {
        HttpClient client = mock(HttpClient.class);
        ObjectMapper mapper = new ObjectMapper();
        
        // Pass the Groq default URL explicitly to simulate the inverse scenario
        String groqDefault = "https://api.groq.com/openai/v1/chat/completions";
        AiProvider provider = AiProviderFactory.create("gemini", groqDefault, "key", "model", client, 10, mapper);
        
        assertTrue(provider instanceof GeminiAiProvider);
        
        java.lang.reflect.Field urlField = GeminiAiProvider.class.getDeclaredField("apiUrl");
        urlField.setAccessible(true);
        String actualUrl = (String) urlField.get(provider);
        
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent", actualUrl);
    }
}

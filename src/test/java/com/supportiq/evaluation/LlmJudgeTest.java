package com.supportiq.evaluation;

import com.supportiq.model.AgentOutcome;
import com.supportiq.service.provider.AiProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmJudgeTest {

    @Test
    void testJudgeResponseParsing_AIGenerated() {
        AiProvider mockProvider = prompt -> "{\n" +
                "  \"responseType\": \"AI_GENERATED\",\n" +
                "  \"semanticMatchScore\": 92,\n" +
                "  \"evidence\": \"Historical evidence summary\",\n" +
                "  \"reason\": \"Reasoning\"\n" +
                "}";

        LlmJudge judge = new LlmJudge(mockProvider);
        AgentOutcome outcome = new AgentOutcome(AgentOutcome.ResponseType.AI_GENERATED, "Hello", null, null);
        LlmJudge.JudgeResult res = judge.evaluate("Where is order?", "I will help", outcome);

        assertTrue(res.success);
        assertEquals("AI_GENERATED", res.responseType);
        assertEquals(92, res.semanticMatchScore);
        assertEquals("Historical evidence summary", res.evidence);
        assertEquals("Reasoning", res.reason);
    }

    @Test
    void testJudgeResponseParsing_HumanEscalation() {
        AiProvider mockProvider = prompt -> "{\n" +
                "  \"responseType\": \"HUMAN_ESCALATION\",\n" +
                "  \"semanticMatchScore\": null,\n" +
                "  \"evidence\": \"Escalation evidence\",\n" +
                "  \"reason\": \"Reasoning\"\n" +
                "}";

        LlmJudge judge = new LlmJudge(mockProvider);
        AgentOutcome outcome = new AgentOutcome(AgentOutcome.ResponseType.HUMAN_ESCALATION, "Escalating", null, "High Risk");
        LlmJudge.JudgeResult res = judge.evaluate("My account was hacked", "I will secure it", outcome);

        assertTrue(res.success);
        assertEquals("HUMAN_ESCALATION", res.responseType);
        assertNull(res.semanticMatchScore);
        assertEquals("Escalation evidence", res.evidence);
    }
    
    @Test
    void testJudgeResponseParsing_InvalidJson() {
        AiProvider mockProvider = prompt -> "Not a json object";

        LlmJudge judge = new LlmJudge(mockProvider);
        AgentOutcome outcome = new AgentOutcome(AgentOutcome.ResponseType.AI_GENERATED, "Hello", null, null);
        LlmJudge.JudgeResult res = judge.evaluate("Where is order?", "I will help", outcome);

        assertFalse(res.success);
        assertTrue(res.statusMessage.contains("Invalid JSON format"));
    }
}

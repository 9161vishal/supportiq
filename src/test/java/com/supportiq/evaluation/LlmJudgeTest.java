package com.supportiq.evaluation;

import com.supportiq.model.AgentOutcome;
import com.supportiq.service.provider.AiProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmJudgeTest {

    private LlmJudge createJudge(String jsonResponse) {
        AiProvider mockProvider = prompt -> jsonResponse;
        return new LlmJudge(mockProvider);
    }

    private AgentOutcome aiOutcome() {
        return new AgentOutcome(AgentOutcome.ResponseType.AI_GENERATED, "Hello", null, null);
    }

    private AgentOutcome humanOutcome() {
        return new AgentOutcome(AgentOutcome.ResponseType.HUMAN_ESCALATION, "Escalating", null, "High Risk");
    }

    private AgentOutcome invalidOutcome() {
        return new AgentOutcome(AgentOutcome.ResponseType.INVALID_OR_OUT_OF_CATEGORY, "Invalid", null, null);
    }

    @Test
    void test1_ValidAIGenerated() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        LlmJudge.JudgeResult res = judge.evaluate("Q", "A", aiOutcome());
        assertTrue(res.success);
        assertEquals(92, res.semanticMatchScore);
    }

    @Test
    void test2_ValidHumanEscalation() {
        LlmJudge judge = createJudge("{\"responseType\": \"HUMAN_ESCALATION\", \"semanticMatchScore\": null, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        LlmJudge.JudgeResult res = judge.evaluate("Q", "A", humanOutcome());
        assertTrue(res.success);
        assertNull(res.semanticMatchScore);
    }

    @Test
    void test3_ValidInvalidOrOutOfCategory() {
        LlmJudge judge = createJudge("{\"responseType\": \"INVALID_OR_OUT_OF_CATEGORY\", \"semanticMatchScore\": null, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        LlmJudge.JudgeResult res = judge.evaluate("Q", "A", invalidOutcome());
        assertTrue(res.success);
        assertNull(res.semanticMatchScore);
    }

    @Test
    void test4_InvalidJson() {
        LlmJudge judge = createJudge("Not JSON");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test5_MissingResponseType() {
        LlmJudge judge = createJudge("{\"semanticMatchScore\": 92, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test6_MissingSemanticMatchScore() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test7_MissingEvidence() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test8_MissingReason() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"evidence\": \"Ev\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test9_InvalidResponseType() {
        LlmJudge judge = createJudge("{\"responseType\": \"FOO\", \"semanticMatchScore\": 92, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test10_ScoreNegativeOne() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": -1, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test11_Score101() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 101, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test12_DecimalScore() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92.5, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test13_StringScore() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": \"92\", \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test14_ExtraField() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"evidence\": \"Ev\", \"reason\": \"Re\", \"extra\": 1}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test15_ResponseTypeMismatch() {
        LlmJudge judge = createJudge("{\"responseType\": \"HUMAN_ESCALATION\", \"semanticMatchScore\": null, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success); // aiOutcome returns AI_GENERATED
    }

    @Test
    void test16_NonNullScoreHumanEscalation() {
        LlmJudge judge = createJudge("{\"responseType\": \"HUMAN_ESCALATION\", \"semanticMatchScore\": 90, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", humanOutcome()).success);
    }

    @Test
    void test17_NonNullScoreInvalid() {
        LlmJudge judge = createJudge("{\"responseType\": \"INVALID_OR_OUT_OF_CATEGORY\", \"semanticMatchScore\": 0, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", invalidOutcome()).success);
    }

    @Test
    void test18_NullScoreAIGenerated() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": null, \"evidence\": \"Ev\", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test19_BlankEvidence() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"evidence\": \"   \", \"reason\": \"Re\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }

    @Test
    void test20_BlankReason() {
        LlmJudge judge = createJudge("{\"responseType\": \"AI_GENERATED\", \"semanticMatchScore\": 92, \"evidence\": \"Ev\", \"reason\": \"\"}");
        assertFalse(judge.evaluate("Q", "A", aiOutcome()).success);
    }
}

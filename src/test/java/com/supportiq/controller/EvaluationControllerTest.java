package com.supportiq.controller;

import com.supportiq.evaluation.LlmJudge;
import com.supportiq.model.AgentOutcome;
import com.supportiq.service.SupportAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvaluationControllerTest {

    private SupportAgentService supportAgentService;
    private LlmJudge llmJudge;
    private EvaluationController controller;

    @BeforeEach
    void setUp() {
        supportAgentService = mock(SupportAgentService.class);
        llmJudge = mock(LlmJudge.class);
        controller = new EvaluationController(supportAgentService, llmJudge);
    }

    private String getRealGoldenId() throws Exception {
        Path jsonlPath = Paths.get("data/evaluation/golden_dataset.jsonl");
        if (Files.exists(jsonlPath)) {
            for (String line : Files.readAllLines(jsonlPath)) {
                if (line.trim().isEmpty()) continue;
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(line);
                if (node.has("id")) {
                    return node.get("id").asText();
                }
            }
        }
        return "golden-001";
    }

    @Test
    void test1_QueryAndHumanAnswerWorks() throws Exception {
        AgentOutcome outcome = new AgentOutcome(AgentOutcome.ResponseType.AI_GENERATED, "Test", null, null);
        when(supportAgentService.handleMessageWithOutcome(any())).thenReturn(outcome);
        
        LlmJudge.JudgeResult jr = new LlmJudge.JudgeResult(true, "OK", "AI_GENERATED", 90, "Ev", "Re");
        when(llmJudge.evaluate(anyString(), anyString(), any())).thenReturn(jr);

        Map<String, Object> req = new HashMap<>();
        req.put("query", "Where is my order?");
        req.put("humanAnswer", "It is shipped.");

        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertEquals("AI_GENERATED", body.get("responseType"));
        assertEquals(90, body.get("semanticMatchScore"));
        
        verify(supportAgentService, times(1)).handleMessageWithOutcome(any());
        verify(llmJudge, times(1)).evaluate(anyString(), anyString(), any());
    }

    @Test
    void test2_GoldenCaseIdWorks() throws Exception {
        String validId = getRealGoldenId();
        AgentOutcome outcome = new AgentOutcome(AgentOutcome.ResponseType.AI_GENERATED, "Test", null, null);
        when(supportAgentService.handleMessageWithOutcome(any())).thenReturn(outcome);
        
        LlmJudge.JudgeResult jr = new LlmJudge.JudgeResult(true, "OK", "AI_GENERATED", 90, "Ev", "Re");
        when(llmJudge.evaluate(anyString(), anyString(), any())).thenReturn(jr);

        Map<String, Object> req = new HashMap<>();
        req.put("goldenCaseId", validId);

        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(supportAgentService, times(1)).handleMessageWithOutcome(any());
    }

    @Test
    void test3_NeitherSupplied() {
        Map<String, Object> req = new HashMap<>();
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void test4_QueryWithoutHumanAnswer() {
        Map<String, Object> req = new HashMap<>();
        req.put("query", "Where is my order?");
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void test5_HumanAnswerWithoutQuery() {
        Map<String, Object> req = new HashMap<>();
        req.put("humanAnswer", "It is shipped.");
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void test6_GoldenCaseIdAndQueryTogether() {
        Map<String, Object> req = new HashMap<>();
        req.put("goldenCaseId", "golden-001");
        req.put("query", "Where is my order?");
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void test7_BlankQuery() {
        Map<String, Object> req = new HashMap<>();
        req.put("query", "  ");
        req.put("humanAnswer", "It is shipped.");
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void test10_UnknownGoldenCaseId() {
        Map<String, Object> req = new HashMap<>();
        req.put("goldenCaseId", "unknown-001");
        ResponseEntity<?> resp = controller.judge(req);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}

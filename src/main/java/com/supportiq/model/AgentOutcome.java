package com.supportiq.model;

public class AgentOutcome {
    public enum ResponseType {
        AI_GENERATED,
        HUMAN_ESCALATION,
        INVALID_OR_OUT_OF_CATEGORY
    }

    private final ResponseType responseType;
    private final String text;
    private final RetrievedEvidence evidence;
    private final String escalationReason;

    public AgentOutcome(ResponseType responseType, String text, RetrievedEvidence evidence, String escalationReason) {
        this.responseType = responseType;
        this.text = text;
        this.evidence = evidence;
        this.escalationReason = escalationReason;
    }

    public ResponseType getResponseType() { return responseType; }
    public String getText() { return text; }
    public RetrievedEvidence getEvidence() { return evidence; }
    public String getEscalationReason() { return escalationReason; }
}

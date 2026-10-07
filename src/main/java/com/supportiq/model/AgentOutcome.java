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
    private final Intent intent;

    public AgentOutcome(ResponseType responseType, String text, RetrievedEvidence evidence, String escalationReason) {
        this(responseType, text, evidence, escalationReason, null);
    }
    
    public AgentOutcome(ResponseType responseType, String text, RetrievedEvidence evidence, String escalationReason, Intent intent) {
        this.responseType = responseType;
        this.text = text;
        this.evidence = evidence;
        this.escalationReason = escalationReason;
        this.intent = intent;
    }

    public ResponseType getResponseType() { return responseType; }
    public String getText() { return text; }
    public RetrievedEvidence getEvidence() { return evidence; }
    public String getEscalationReason() { return escalationReason; }
    public Intent getIntent() { return intent; }
}

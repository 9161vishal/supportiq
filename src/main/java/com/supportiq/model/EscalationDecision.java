package com.supportiq.model;

public class EscalationDecision {
    public enum Decision {
        AUTO_HANDLE,
        ESCALATE
    }

    private Decision decision;
    private String reason;

    public EscalationDecision(Decision decision, String reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }
}

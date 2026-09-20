package com.supportiq.model;

public class EscalationDecision {
    public enum Decision {
        AUTO_HANDLE,
        ESCALATE
    }

    private Decision decision;
    private EscalationReason reason;

    public EscalationDecision(Decision decision, EscalationReason reason) {
        this.decision = decision;
        this.reason = reason;
    }

    public Decision getDecision() {
        return decision;
    }

    public EscalationReason getReason() {
        return reason;
    }
}

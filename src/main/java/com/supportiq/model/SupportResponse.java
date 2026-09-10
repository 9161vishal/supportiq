package com.supportiq.model;

public class SupportResponse {
    private Intent intent;
    private EscalationDecision decision;
    private String reply;
    private RetrievedEvidence evidence;

    public SupportResponse(Intent intent, EscalationDecision decision, String reply, RetrievedEvidence evidence) {
        this.intent = intent;
        this.decision = decision;
        this.reply = reply;
        this.evidence = evidence;
    }

    public Intent getIntent() {
        return intent;
    }

    public EscalationDecision getDecision() {
        return decision;
    }

    public String getReply() {
        return reply;
    }

    public RetrievedEvidence getEvidence() {
        return evidence;
    }
}

package com.io.appioweb.adapters.persistence.aisupervisors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_supervisor_decision_logs")
public class JpaAiSupervisorDecisionLogEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "inbound_message_id")
    private UUID inboundMessageId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "target_agent_id", length = 120)
    private String targetAgentId;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "reason", length = 180)
    private String reason;

    @Column(name = "evaluation_key", nullable = false, length = 200)
    private String evaluationKey;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message_short", length = 220)
    private String errorMessageShort;

    @Column(name = "context_snippet", length = 400)
    private String contextSnippet;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(UUID supervisorId) {
        this.supervisorId = supervisorId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public UUID getInboundMessageId() {
        return inboundMessageId;
    }

    public void setInboundMessageId(UUID inboundMessageId) {
        this.inboundMessageId = inboundMessageId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetAgentId() {
        return targetAgentId;
    }

    public void setTargetAgentId(String targetAgentId) {
        this.targetAgentId = targetAgentId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvaluationKey() {
        return evaluationKey;
    }

    public void setEvaluationKey(String evaluationKey) {
        this.evaluationKey = evaluationKey;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessageShort() {
        return errorMessageShort;
    }

    public void setErrorMessageShort(String errorMessageShort) {
        this.errorMessageShort = errorMessageShort;
    }

    public String getContextSnippet() {
        return contextSnippet;
    }

    public void setContextSnippet(String contextSnippet) {
        this.contextSnippet = contextSnippet;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.io.appioweb.adapters.persistence.aiagents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_agent_kanban_move_attempts")
public class JpaAiAgentKanbanMoveAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "card_id", nullable = false, length = 120)
    private String cardId;

    @Column(name = "from_stage_id", length = 120)
    private String fromStageId;

    @Column(name = "to_stage_id", length = 120)
    private String toStageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private AiAgentKanbanMoveDecision decision;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "reason", length = 180)
    private String reason;

    @Column(name = "evaluation_key", nullable = false, length = 200)
    private String evaluationKey;

    @Column(name = "last_message_id")
    private UUID lastMessageId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message_short", length = 180)
    private String errorMessageShort;

    @Column(name = "llm_request_id", length = 120)
    private String llmRequestId;

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

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getFromStageId() {
        return fromStageId;
    }

    public void setFromStageId(String fromStageId) {
        this.fromStageId = fromStageId;
    }

    public String getToStageId() {
        return toStageId;
    }

    public void setToStageId(String toStageId) {
        this.toStageId = toStageId;
    }

    public AiAgentKanbanMoveDecision getDecision() {
        return decision;
    }

    public void setDecision(AiAgentKanbanMoveDecision decision) {
        this.decision = decision;
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

    public UUID getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(UUID lastMessageId) {
        this.lastMessageId = lastMessageId;
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

    public String getLlmRequestId() {
        return llmRequestId;
    }

    public void setLlmRequestId(String llmRequestId) {
        this.llmRequestId = llmRequestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

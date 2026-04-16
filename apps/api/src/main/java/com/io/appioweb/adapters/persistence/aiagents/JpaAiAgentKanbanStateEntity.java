package com.io.appioweb.adapters.persistence.aiagents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_agent_kanban_state")
@IdClass(JpaAiAgentKanbanStateId.class)
public class JpaAiAgentKanbanStateEntity {

    @Id
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Id
    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Id
    @Column(name = "card_id", nullable = false, length = 120)
    private String cardId;

    @Column(name = "last_evaluated_message_id")
    private UUID lastEvaluatedMessageId;

    @Column(name = "last_evaluated_message_at")
    private Instant lastEvaluatedMessageAt;

    @Column(name = "last_decision_at")
    private Instant lastDecisionAt;

    @Column(name = "last_moved_stage_id", length = 120)
    private String lastMovedStageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public UUID getLastEvaluatedMessageId() {
        return lastEvaluatedMessageId;
    }

    public void setLastEvaluatedMessageId(UUID lastEvaluatedMessageId) {
        this.lastEvaluatedMessageId = lastEvaluatedMessageId;
    }

    public Instant getLastEvaluatedMessageAt() {
        return lastEvaluatedMessageAt;
    }

    public void setLastEvaluatedMessageAt(Instant lastEvaluatedMessageAt) {
        this.lastEvaluatedMessageAt = lastEvaluatedMessageAt;
    }

    public Instant getLastDecisionAt() {
        return lastDecisionAt;
    }

    public void setLastDecisionAt(Instant lastDecisionAt) {
        this.lastDecisionAt = lastDecisionAt;
    }

    public String getLastMovedStageId() {
        return lastMovedStageId;
    }

    public void setLastMovedStageId(String lastMovedStageId) {
        this.lastMovedStageId = lastMovedStageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

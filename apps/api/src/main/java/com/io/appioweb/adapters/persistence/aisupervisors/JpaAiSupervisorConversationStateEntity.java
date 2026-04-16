package com.io.appioweb.adapters.persistence.aisupervisors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_supervisor_conversation_state")
public class JpaAiSupervisorConversationStateEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "supervisor_id", nullable = false)
    private UUID supervisorId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "card_id", length = 120)
    private String cardId;

    @Column(name = "assigned_agent_id", length = 120)
    private String assignedAgentId;

    @Column(name = "triage_asked", nullable = false)
    private boolean triageAsked;

    @Column(name = "last_supervisor_question_message_id")
    private UUID lastSupervisorQuestionMessageId;

    @Column(name = "last_evaluated_message_id")
    private UUID lastEvaluatedMessageId;

    @Column(name = "last_decision_at")
    private Instant lastDecisionAt;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(String assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public boolean isTriageAsked() {
        return triageAsked;
    }

    public void setTriageAsked(boolean triageAsked) {
        this.triageAsked = triageAsked;
    }

    public UUID getLastSupervisorQuestionMessageId() {
        return lastSupervisorQuestionMessageId;
    }

    public void setLastSupervisorQuestionMessageId(UUID lastSupervisorQuestionMessageId) {
        this.lastSupervisorQuestionMessageId = lastSupervisorQuestionMessageId;
    }

    public UUID getLastEvaluatedMessageId() {
        return lastEvaluatedMessageId;
    }

    public void setLastEvaluatedMessageId(UUID lastEvaluatedMessageId) {
        this.lastEvaluatedMessageId = lastEvaluatedMessageId;
    }

    public Instant getLastDecisionAt() {
        return lastDecisionAt;
    }

    public void setLastDecisionAt(Instant lastDecisionAt) {
        this.lastDecisionAt = lastDecisionAt;
    }

    public Instant getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(Instant cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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

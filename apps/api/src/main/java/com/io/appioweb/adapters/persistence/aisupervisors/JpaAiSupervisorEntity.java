package com.io.appioweb.adapters.persistence.aisupervisors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_supervisors")
public class JpaAiSupervisorEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 180)
    private String name;

    @Column(name = "communication_style", nullable = false, columnDefinition = "text")
    private String communicationStyle;

    @Column(name = "profile", nullable = false, columnDefinition = "text")
    private String profile;

    @Column(name = "objective", nullable = false, columnDefinition = "text")
    private String objective;

    @Column(name = "reasoning_model_version", nullable = false, length = 80)
    private String reasoningModelVersion;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "model", nullable = false, length = 120)
    private String model;

    @Column(name = "other_rules", nullable = false, columnDefinition = "text")
    private String otherRules;

    @Column(name = "human_handoff_enabled", nullable = false)
    private boolean humanHandoffEnabled;

    @Column(name = "notify_contact_on_agent_transfer", nullable = false)
    private boolean notifyContactOnAgentTransfer;

    @Column(name = "human_handoff_team", nullable = false, length = 120)
    private String humanHandoffTeam;

    @Column(name = "human_handoff_send_message", nullable = false)
    private boolean humanHandoffSendMessage;

    @Column(name = "human_handoff_message", nullable = false, columnDefinition = "text")
    private String humanHandoffMessage;

    @Column(name = "agent_issue_handoff_team", nullable = false, length = 120)
    private String agentIssueHandoffTeam;

    @Column(name = "agent_issue_send_message", nullable = false)
    private boolean agentIssueSendMessage;

    @Column(name = "human_user_choice_enabled", nullable = false)
    private boolean humanUserChoiceEnabled;

    @Column(name = "human_choice_options_json", nullable = false, columnDefinition = "text")
    private String humanChoiceOptionsJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommunicationStyle() {
        return communicationStyle;
    }

    public void setCommunicationStyle(String communicationStyle) {
        this.communicationStyle = communicationStyle;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getReasoningModelVersion() {
        return reasoningModelVersion;
    }

    public void setReasoningModelVersion(String reasoningModelVersion) {
        this.reasoningModelVersion = reasoningModelVersion;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getOtherRules() {
        return otherRules;
    }

    public void setOtherRules(String otherRules) {
        this.otherRules = otherRules;
    }

    public boolean isHumanHandoffEnabled() {
        return humanHandoffEnabled;
    }

    public void setHumanHandoffEnabled(boolean humanHandoffEnabled) {
        this.humanHandoffEnabled = humanHandoffEnabled;
    }

    public boolean isNotifyContactOnAgentTransfer() {
        return notifyContactOnAgentTransfer;
    }

    public void setNotifyContactOnAgentTransfer(boolean notifyContactOnAgentTransfer) {
        this.notifyContactOnAgentTransfer = notifyContactOnAgentTransfer;
    }

    public String getHumanHandoffTeam() {
        return humanHandoffTeam;
    }

    public void setHumanHandoffTeam(String humanHandoffTeam) {
        this.humanHandoffTeam = humanHandoffTeam;
    }

    public boolean isHumanHandoffSendMessage() {
        return humanHandoffSendMessage;
    }

    public void setHumanHandoffSendMessage(boolean humanHandoffSendMessage) {
        this.humanHandoffSendMessage = humanHandoffSendMessage;
    }

    public String getHumanHandoffMessage() {
        return humanHandoffMessage;
    }

    public void setHumanHandoffMessage(String humanHandoffMessage) {
        this.humanHandoffMessage = humanHandoffMessage;
    }

    public String getAgentIssueHandoffTeam() {
        return agentIssueHandoffTeam;
    }

    public void setAgentIssueHandoffTeam(String agentIssueHandoffTeam) {
        this.agentIssueHandoffTeam = agentIssueHandoffTeam;
    }

    public boolean isAgentIssueSendMessage() {
        return agentIssueSendMessage;
    }

    public void setAgentIssueSendMessage(boolean agentIssueSendMessage) {
        this.agentIssueSendMessage = agentIssueSendMessage;
    }

    public boolean isHumanUserChoiceEnabled() {
        return humanUserChoiceEnabled;
    }

    public void setHumanUserChoiceEnabled(boolean humanUserChoiceEnabled) {
        this.humanUserChoiceEnabled = humanUserChoiceEnabled;
    }

    public String getHumanChoiceOptionsJson() {
        return humanChoiceOptionsJson;
    }

    public void setHumanChoiceOptionsJson(String humanChoiceOptionsJson) {
        this.humanChoiceOptionsJson = humanChoiceOptionsJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

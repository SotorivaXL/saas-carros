package com.io.appioweb.adapters.persistence.aiagents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_agent_run_logs")
public class JpaAiAgentRunLogEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "agent_id", nullable = false, length = 120)
    private String agentId;

    @Column(name = "trace_id", nullable = false, length = 120)
    private String traceId;

    @Column(name = "customer_message", columnDefinition = "text")
    private String customerMessage;

    @Column(name = "final_text", columnDefinition = "text")
    private String finalText;

    @Column(name = "handoff", nullable = false)
    private boolean handoff;

    @Column(name = "actions_json", nullable = false, columnDefinition = "text")
    private String actionsJson;

    @Column(name = "tool_logs_json", nullable = false, columnDefinition = "text")
    private String toolLogsJson;

    @Column(name = "request_payload_json", nullable = false, columnDefinition = "text")
    private String requestPayloadJson;

    @Column(name = "response_payload_json", nullable = false, columnDefinition = "text")
    private String responsePayloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCompanyId() { return companyId; }
    public void setCompanyId(UUID companyId) { this.companyId = companyId; }

    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID conversationId) { this.conversationId = conversationId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getCustomerMessage() { return customerMessage; }
    public void setCustomerMessage(String customerMessage) { this.customerMessage = customerMessage; }

    public String getFinalText() { return finalText; }
    public void setFinalText(String finalText) { this.finalText = finalText; }

    public boolean isHandoff() { return handoff; }
    public void setHandoff(boolean handoff) { this.handoff = handoff; }

    public String getActionsJson() { return actionsJson; }
    public void setActionsJson(String actionsJson) { this.actionsJson = actionsJson; }

    public String getToolLogsJson() { return toolLogsJson; }
    public void setToolLogsJson(String toolLogsJson) { this.toolLogsJson = toolLogsJson; }

    public String getRequestPayloadJson() { return requestPayloadJson; }
    public void setRequestPayloadJson(String requestPayloadJson) { this.requestPayloadJson = requestPayloadJson; }

    public String getResponsePayloadJson() { return responsePayloadJson; }
    public void setResponsePayloadJson(String responsePayloadJson) { this.responsePayloadJson = responsePayloadJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

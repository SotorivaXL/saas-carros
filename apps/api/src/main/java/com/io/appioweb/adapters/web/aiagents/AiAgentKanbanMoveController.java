package com.io.appioweb.adapters.web.aiagents;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentStageRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentStageRuleEntity;
import com.io.appioweb.adapters.web.aiagents.kanban.KanbanMoveDecisionService;
import com.io.appioweb.adapters.web.aiagents.request.KanbanEvaluateHttpRequest;
import com.io.appioweb.adapters.web.aiagents.request.KanbanStageRulesHttpRequest;
import com.io.appioweb.adapters.web.aiagents.response.KanbanEvaluateHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.KanbanStageRuleHttpResponse;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class AiAgentKanbanMoveController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CurrentUserPort currentUser;
    private final AiAgentStageRuleRepositoryJpa stageRuleRepository;
    private final KanbanMoveDecisionService decisionService;

    public AiAgentKanbanMoveController(
            CurrentUserPort currentUser,
            AiAgentStageRuleRepositoryJpa stageRuleRepository,
            KanbanMoveDecisionService decisionService
    ) {
        this.currentUser = currentUser;
        this.stageRuleRepository = stageRuleRepository;
        this.decisionService = decisionService;
    }

    @GetMapping("/ai-agents/{agentId}/kanban/stage-rules")
    public ResponseEntity<List<KanbanStageRuleHttpResponse>> listStageRules(@PathVariable String agentId) {
        UUID companyId = currentUser.companyId();
        String normalizedAgentId = safeTrim(agentId);
        if (normalizedAgentId.isBlank()) {
            throw new BusinessException("KANBAN_AGENT_REQUIRED", "agentId obrigatorio");
        }
        List<JpaAiAgentStageRuleEntity> rows = stageRuleRepository
                .findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, normalizedAgentId);
        return ResponseEntity.ok(rows.stream().map(this::toResponse).toList());
    }

    @PutMapping("/ai-agents/{agentId}/kanban/stage-rules")
    public ResponseEntity<List<KanbanStageRuleHttpResponse>> saveStageRules(
            @PathVariable String agentId,
            @RequestBody(required = false) KanbanStageRulesHttpRequest request
    ) {
        UUID companyId = currentUser.companyId();
        String normalizedAgentId = safeTrim(agentId);
        if (normalizedAgentId.isBlank()) {
            throw new BusinessException("KANBAN_AGENT_REQUIRED", "agentId obrigatorio");
        }

        List<KanbanStageRulesHttpRequest.KanbanStageRuleItemHttpRequest> incoming =
                request == null || request.rules() == null ? List.of() : request.rules();

        Map<String, KanbanStageRulesHttpRequest.KanbanStageRuleItemHttpRequest> deduplicated = new LinkedHashMap<>();
        for (KanbanStageRulesHttpRequest.KanbanStageRuleItemHttpRequest item : incoming) {
            if (item == null) continue;
            String stageId = safeTrim(item.stageId());
            if (stageId.isBlank()) continue;
            deduplicated.put(stageId, item);
        }

        Instant now = Instant.now();
        stageRuleRepository.deleteAllByCompanyIdAndAgentId(companyId, normalizedAgentId);

        List<JpaAiAgentStageRuleEntity> rows = new ArrayList<>();
        for (Map.Entry<String, KanbanStageRulesHttpRequest.KanbanStageRuleItemHttpRequest> entry : deduplicated.entrySet()) {
            KanbanStageRulesHttpRequest.KanbanStageRuleItemHttpRequest item = entry.getValue();
            JpaAiAgentStageRuleEntity row = new JpaAiAgentStageRuleEntity();
            row.setId(UUID.randomUUID());
            row.setCompanyId(companyId);
            row.setAgentId(normalizedAgentId);
            row.setStageId(entry.getKey());
            row.setEnabled(item.enabled() == null || item.enabled().booleanValue());
            row.setPrompt(truncate(safeTrim(item.prompt()), 500));
            row.setPriority(item.priority());
            row.setOnlyForwardOverride(item.onlyForwardOverride());
            row.setAllowedFromStagesJson(toJsonArray(item.allowedFromStages()));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        stageRuleRepository.saveAllAndFlush(rows);

        List<KanbanStageRuleHttpResponse> response = rows.stream()
                .sorted(Comparator.comparing((JpaAiAgentStageRuleEntity item) -> item.getPriority() == null ? 0 : item.getPriority()).reversed())
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/internal/ai/kanban/evaluate")
    public ResponseEntity<KanbanEvaluateHttpResponse> evaluate(@RequestBody KanbanEvaluateHttpRequest request) {
        if (request == null || request.conversationId() == null) {
            throw new BusinessException("KANBAN_CONVERSATION_REQUIRED", "conversationId obrigatorio");
        }
        String agentId = safeTrim(request.agentId());
        if (agentId.isBlank()) {
            throw new BusinessException("KANBAN_AGENT_REQUIRED", "agentId obrigatorio");
        }
        String cardId = safeTrim(request.cardId());
        if (cardId.isBlank()) cardId = request.conversationId().toString();

        var result = decisionService.evaluateAndMaybeMoveCard(
                currentUser.companyId(),
                request.conversationId(),
                cardId,
                agentId,
                request.events() == null ? List.of() : request.events(),
                request.force() != null && request.force().booleanValue()
        );

        return ResponseEntity.ok(new KanbanEvaluateHttpResponse(
                result.decision(),
                result.moved(),
                result.targetStageId(),
                result.confidence(),
                result.reason(),
                result.errorCode(),
                result.evaluationKey(),
                result.llmRequestId()
        ));
    }

    private KanbanStageRuleHttpResponse toResponse(JpaAiAgentStageRuleEntity row) {
        return new KanbanStageRuleHttpResponse(
                safeTrim(row.getStageId()),
                row.isEnabled(),
                safeTrim(row.getPrompt()),
                row.getPriority(),
                row.getOnlyForwardOverride(),
                parseAllowedFromStages(row.getAllowedFromStagesJson()),
                row.getUpdatedAt()
        );
    }

    private List<String> parseAllowedFromStages(String rawJson) {
        List<String> values = new ArrayList<>();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(rawJson == null || rawJson.isBlank() ? "[]" : rawJson);
            if (!node.isArray()) return values;
            for (JsonNode item : node) {
                String value = safeTrim(item.asText(""));
                if (!value.isBlank()) values.add(value);
            }
            return values;
        } catch (Exception ex) {
            return values;
        }
    }

    private String toJsonArray(List<String> values) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        if (values != null) {
            for (String value : new LinkedHashSet<>(values)) {
                String normalized = safeTrim(value);
                if (normalized.isBlank()) continue;
                array.add(normalized);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(array);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int max) {
        String text = safeTrim(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max));
    }
}

package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class AiSupervisorDecisionParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ParsedDecision parseStrict(String raw, Set<String> candidateAgentIds, int maxClarifyingQuestionChars) {
        String payload = AiSupervisorSupport.safeTrim(raw);
        if (payload.isBlank()) {
            throw new BusinessException("SUPERVISOR_LLM_EMPTY_OUTPUT", "LLM do supervisor nao retornou JSON");
        }

        JsonNode root;
        try (JsonParser parser = OBJECT_MAPPER.createParser(payload)) {
            root = OBJECT_MAPPER.readTree(parser);
            if (root == null || !root.isObject()) {
                throw new BusinessException("SUPERVISOR_LLM_JSON_PARSE_ERROR", "Resposta do supervisor nao e um objeto JSON");
            }
            if (parser.nextToken() != null) {
                throw new BusinessException("SUPERVISOR_LLM_JSON_PARSE_ERROR", "Resposta do supervisor contem texto fora do JSON");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("SUPERVISOR_LLM_JSON_PARSE_ERROR", "Falha ao interpretar JSON do supervisor");
        }

        AiSupervisorAction action = parseAction(root.path("action"));
        String targetAgentId = parseNullableText(root.get("targetAgentId"));
        String messageToSend = parseNullableText(root.get("messageToSend"));
        String humanQueue = parseNullableText(root.get("humanQueue"));
        double confidence = parseConfidence(root.path("confidence"));
        String reason = parseReason(root.path("reason"));
        List<String> evidence = parseEvidence(root.get("evidence"));

        if (action == AiSupervisorAction.ASSIGN_AGENT) {
            if (AiSupervisorSupport.trimToNull(targetAgentId) == null) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "ASSIGN_AGENT requer targetAgentId");
            }
            if (!candidateAgentIds.contains(targetAgentId)) {
                throw new BusinessException("SUPERVISOR_LLM_TARGET_AGENT_INVALID", "targetAgentId nao pertence aos candidatos");
            }
            if (AiSupervisorSupport.trimToNull(messageToSend) != null) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "ASSIGN_AGENT nao deve retornar messageToSend");
            }
        }
        if (action == AiSupervisorAction.ASK_CLARIFYING) {
            if (AiSupervisorSupport.trimToNull(targetAgentId) != null) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "ASK_CLARIFYING nao aceita targetAgentId");
            }
            String normalizedMessage = AiSupervisorSupport.trimToNull(messageToSend);
            if (normalizedMessage == null) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "ASK_CLARIFYING requer messageToSend");
            }
            if (normalizedMessage.length() > maxClarifyingQuestionChars) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Pergunta de triagem excede o limite");
            }
        }
        if (action == AiSupervisorAction.HANDOFF_HUMAN && AiSupervisorSupport.trimToNull(targetAgentId) != null) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "HANDOFF_HUMAN nao aceita targetAgentId");
        }
        if (action == AiSupervisorAction.NO_ACTION) {
            if (AiSupervisorSupport.trimToNull(targetAgentId) != null
                    || AiSupervisorSupport.trimToNull(messageToSend) != null
                    || AiSupervisorSupport.trimToNull(humanQueue) != null) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "NO_ACTION nao aceita campos preenchidos");
            }
        }

        return new ParsedDecision(action, targetAgentId, messageToSend, humanQueue, confidence, reason, evidence);
    }

    private AiSupervisorAction parseAction(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Campo action obrigatorio");
        }
        try {
            return AiSupervisorAction.valueOf(AiSupervisorSupport.safeTrim(node.asText("")));
        } catch (Exception ex) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Action invalida");
        }
    }

    private String parseNullableText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Campo textual com tipo invalido");
        }
        return AiSupervisorSupport.trimToNull(node.asText(""));
    }

    private double parseConfidence(JsonNode node) {
        if (node == null || !node.isNumber()) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Campo confidence invalido");
        }
        double value = node.asDouble(-1.0d);
        if (value < 0.0d || value > 1.0d) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Confidence fora do intervalo");
        }
        return value;
    }

    private String parseReason(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Campo reason invalido");
        }
        String value = AiSupervisorSupport.safeTrim(node.asText(""));
        if (value.isBlank() || value.length() > 160) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Reason obrigatorio e curto");
        }
        return value;
    }

    private List<String> parseEvidence(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        if (!node.isArray()) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Campo evidence deve ser array");
        }
        List<String> evidence = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Evidence com item invalido");
            }
            String value = AiSupervisorSupport.safeTrim(item.asText(""));
            if (value.isBlank()) {
                throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Evidence nao pode conter item vazio");
            }
            evidence.add(value);
        }
        if (evidence.size() > 3) {
            throw new BusinessException("SUPERVISOR_LLM_SCHEMA_INVALID", "Evidence excede o maximo permitido");
        }
        return List.copyOf(evidence);
    }

    public record ParsedDecision(
            AiSupervisorAction action,
            String targetAgentId,
            String messageToSend,
            String humanQueue,
            double confidence,
            String reason,
            List<String> evidence
    ) {
    }
}

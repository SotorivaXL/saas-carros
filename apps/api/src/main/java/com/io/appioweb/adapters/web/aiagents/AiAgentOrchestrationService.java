
package com.io.appioweb.adapters.web.aiagents;

import com.io.appioweb.adapters.integrations.google.AiAgentCalendarOrchestrator;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentRunLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentCompanyStateEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentRunLogEntity;
import com.io.appioweb.adapters.persistence.crm.CrmCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.crm.JpaCrmCompanyStateEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentOrchestrateHttpRequest;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentToolHandoffHttpRequest;
import com.io.appioweb.adapters.web.aiagents.request.AiAgentToolKbSearchHttpRequest;
import com.io.appioweb.adapters.web.aiagents.response.AgentActionHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentOrchestrateHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentToolHandoffHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.AiAgentToolKbSearchHttpResponse;
import com.io.appioweb.adapters.web.aiagents.response.ToolExecutionLogHttpResponse;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import com.io.appioweb.realtime.RealtimeGateway;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAgentOrchestrationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Logger log = LoggerFactory.getLogger(AiAgentOrchestrationService.class);
    private static final int MAX_TOOL_LOOPS = 6;
    private static final int RATE_LIMIT_PER_MINUTE = 24;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{8,15}\\b");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^\\s)]+)\\)");
    private static final Pattern BRACKET_ONLY_CITATION_PATTERN = Pattern.compile("(?m)\\s*\\[[^\\]]{1,80}]\\s*");

    private final CurrentUserPort currentUser;
    private final UserRepositoryPort users;
    private final RealtimeGateway realtime;
    private final AiAgentCompanyStateRepositoryJpa aiAgentState;
    private final AtendimentoConversationRepositoryJpa conversations;
    private final AtendimentoMessageRepositoryJpa messages;
    private final AiAgentRunLogRepositoryJpa runLogs;
    private final CrmCompanyStateRepositoryJpa crmState;
    private final SensitiveDataCrypto crypto;
    private final AiAgentCalendarOrchestrator calendarOrchestrator;
    private final HttpRequestExecutor httpRequestExecutor;
    private final String openAiApiKey;
    private final String defaultOpenAiModel;

    private final Map<String, Deque<Instant>> requestBuckets = new ConcurrentHashMap<>();

    @Autowired
    public AiAgentOrchestrationService(
            CurrentUserPort currentUser,
            UserRepositoryPort users,
            RealtimeGateway realtime,
            AiAgentCompanyStateRepositoryJpa aiAgentState,
            AtendimentoConversationRepositoryJpa conversations,
            AtendimentoMessageRepositoryJpa messages,
            AiAgentRunLogRepositoryJpa runLogs,
            CrmCompanyStateRepositoryJpa crmState,
            SensitiveDataCrypto crypto,
            AiAgentCalendarOrchestrator calendarOrchestrator,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${OPENAI_DEFAULT_MODEL:gpt-5-mini}") String defaultOpenAiModel
    ) {
        this(
                currentUser,
                users,
                realtime,
                aiAgentState,
                conversations,
                messages,
                runLogs,
                crmState,
                crypto,
                calendarOrchestrator,
                openAiApiKey,
                defaultOpenAiModel,
                request -> HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        );
    }

    AiAgentOrchestrationService(
            CurrentUserPort currentUser,
            UserRepositoryPort users,
            RealtimeGateway realtime,
            AiAgentCompanyStateRepositoryJpa aiAgentState,
            AtendimentoConversationRepositoryJpa conversations,
            AtendimentoMessageRepositoryJpa messages,
            AiAgentRunLogRepositoryJpa runLogs,
            CrmCompanyStateRepositoryJpa crmState,
            SensitiveDataCrypto crypto,
            AiAgentCalendarOrchestrator calendarOrchestrator,
            String openAiApiKey,
            String defaultOpenAiModel,
            HttpRequestExecutor httpRequestExecutor
    ) {
        this.currentUser = currentUser;
        this.users = users;
        this.realtime = realtime;
        this.aiAgentState = aiAgentState;
        this.conversations = conversations;
        this.messages = messages;
        this.runLogs = runLogs;
        this.crmState = crmState;
        this.crypto = crypto;
        this.calendarOrchestrator = calendarOrchestrator;
        this.httpRequestExecutor = httpRequestExecutor;
        this.openAiApiKey = safeTrim(openAiApiKey);
        this.defaultOpenAiModel = safeTrim(defaultOpenAiModel);
    }

    public AiAgentOrchestrateHttpResponse orchestrate(AiAgentOrchestrateHttpRequest req) {
        return orchestrateForCompany(currentUser.companyId(), req);
    }

    public AiAgentOrchestrateHttpResponse orchestrateForCompany(UUID companyId, AiAgentOrchestrateHttpRequest req) {
        String traceId = UUID.randomUUID().toString();
        enforceRateLimit(companyId, req.conversationId());

        RuntimeContext runtime = loadRuntime(companyId, req.agentId());
        var conversation = conversations.findByIdAndCompanyId(req.conversationId(), companyId)
                .orElseThrow(() -> new BusinessException("ATENDIMENTO_CONVERSATION_NOT_FOUND", "Conversa nao encontrada"));

        AiAgentOrchestrateHttpResponse calendarResponse = maybeHandleCalendarFlow(runtime, req, traceId, conversation);
        if (calendarResponse != null) {
            return calendarResponse;
        }

        Set<String> allowedTools = runtime.allowedTools();
        ArrayNode actions = OBJECT_MAPPER.createArrayNode();
        ArrayNode toolLogs = OBJECT_MAPPER.createArrayNode();
        JsonNode openAiResponse = null;
        JsonNode openAiRequest = null;
        boolean handoff = false;

        try {
            openAiRequest = buildInitialOpenAiRequest(runtime, req, conversation.getPhone(), allowedTools);
            openAiResponse = callOpenAiResponses(runtime.openAiApiKey(), openAiRequest);

            int loops = 0;
            while (loops < MAX_TOOL_LOOPS) {
                List<FunctionCallRequest> calls = extractFunctionCalls(openAiResponse);
                if (calls.isEmpty()) break;

                ArrayNode functionOutputs = OBJECT_MAPPER.createArrayNode();
                for (FunctionCallRequest call : calls) {
                    ToolExecutionResult executed = runToolFromModel(runtime, req.conversationId(), call, traceId, allowedTools, req.customerMessage());
                    actions.add(executed.actionNode());
                    toolLogs.add(executed.logNode());
                    functionOutputs.add(executed.functionOutputNode(call.callId()));
                    if (executed.handoffTriggered()) handoff = true;
                }

                ObjectNode continuationRequest = OBJECT_MAPPER.createObjectNode();
                continuationRequest.put("model", runtime.model());
                continuationRequest.put("instructions", buildSystemPrompt(runtime));
                continuationRequest.put("temperature", resolveTemperature(runtime.agent()));
                continuationRequest.put("max_output_tokens", resolveMaxOutputTokens(runtime.agent()));
                continuationRequest.set("tools", buildOpenAiTools(runtime, allowedTools));
                continuationRequest.set("include", buildOpenAiInclude(runtime));
                continuationRequest.set("input", functionOutputs);
                String previousResponseId = safeTrim(openAiResponse.path("id").asText(""));
                if (!previousResponseId.isBlank()) continuationRequest.put("previous_response_id", previousResponseId);
                openAiRequest = continuationRequest;
                openAiResponse = callOpenAiResponses(runtime.openAiApiKey(), continuationRequest);
                loops++;
            }

            String finalText = extractAssistantText(openAiResponse);
            if (finalText.isBlank()) {
                finalText = "Nao consegui responder com seguranca agora. Pode reformular sua solicitacao?";
            }
            finalText = completeIfTruncated(runtime, openAiResponse, finalText);
            finalText = sanitizeAssistantOutput(finalText);
            finalText = enforceActiveChannelPolicy(finalText);
            finalText = enforceResponseStyle(finalText, runtime.agent(), req.customerMessage());
            tryAutoFillMissingCrmFields(runtime, req.conversationId(), traceId, actions, toolLogs);
            tryAutoFillCrmDescription(runtime, req.conversationId(), traceId, actions, toolLogs);

            persistRunLog(companyId, req, traceId, handoff, finalText, actions, toolLogs, openAiRequest, openAiResponse);
            return new AiAgentOrchestrateHttpResponse(finalText, toActionList(actions), toToolLogList(toolLogs), handoff, traceId);
        } catch (BusinessException ex) {
            persistRunLog(companyId, req, traceId, true, "", actions, toolLogs, openAiRequest, openAiResponse);
            throw ex;
        }
    }

    public AiAgentToolKbSearchHttpResponse executeKbSearch(AiAgentToolKbSearchHttpRequest req) {
        RuntimeContext runtime = loadRuntime(currentUser.companyId(), req.agentId());
        if (!runtime.allowedTools().contains("kb_search")) {
            throw new BusinessException("AI_TOOL_NOT_ALLOWED", "A skill do agente nao permite kb_search");
        }
        return kbSearchInternal(runtime, req.query(), req.topK(), req.filters());
    }

    public String findFirstActiveAgentId(UUID companyId) {
        AgentExecutionConfig config = findFirstActiveAgentConfig(companyId);
        return config == null ? null : config.agentId();
    }

    public AgentExecutionConfig findAgentExecutionConfig(UUID companyId, String agentId) {
        String normalizedAgentId = safeTrim(agentId);
        if (normalizedAgentId.isBlank()) return null;
        JpaAiAgentCompanyStateEntity state = aiAgentState.findById(companyId).orElse(null);
        if (state == null) return null;
        JsonNode agents = parseJson(state.getAgentsJson(), "[]");
        if (!agents.isArray()) return null;
        for (JsonNode agent : agents) {
            if (!normalizedAgentId.equals(safeTrim(agent.path("id").asText("")))) continue;
            if (!agent.path("isActive").asBoolean(true)) return null;
            int delayMessageSeconds = clampInt(agent.path("delayMessageSeconds").asInt(2), 0, 30, 2);
            int delayTypingSeconds = clampInt(agent.path("delayTypingSeconds").asInt(agent.path("typingSimulationSeconds").asInt(2)), 0, 30, 2);
            return new AgentExecutionConfig(normalizedAgentId, delayMessageSeconds, delayTypingSeconds);
        }
        return null;
    }

    public AgentExecutionConfig findFirstActiveAgentConfig(UUID companyId) {
        JpaAiAgentCompanyStateEntity state = aiAgentState.findById(companyId).orElse(null);
        if (state == null) return null;
        JsonNode agents = parseJson(state.getAgentsJson(), "[]");
        AgentExecutionConfig firstActive = null;
        for (JsonNode agent : agents) {
            if (!agent.path("isActive").asBoolean(true)) continue;
            String id = safeTrim(agent.path("id").asText(""));
            if (id.isBlank()) continue;
            int delayMessageSeconds = clampInt(agent.path("delayMessageSeconds").asInt(2), 0, 30, 2);
            int delayTypingSeconds = clampInt(agent.path("delayTypingSeconds").asInt(agent.path("typingSimulationSeconds").asInt(2)), 0, 30, 2);
            if (firstActive == null) firstActive = new AgentExecutionConfig(id, delayMessageSeconds, delayTypingSeconds);
            String providerId = safeTrim(agent.path("providerId").asText(""));
            if (!providerId.isBlank()) return new AgentExecutionConfig(id, delayMessageSeconds, delayTypingSeconds);
        }
        return firstActive;
    }

    public AiAgentToolHandoffHttpResponse executeHandoff(AiAgentToolHandoffHttpRequest req) {
        RuntimeContext runtime = loadRuntime(currentUser.companyId(), req.agentId());
        if (!runtime.allowedTools().contains("handoff_to_human")) {
            throw new BusinessException("AI_TOOL_NOT_ALLOWED", "A skill do agente nao permite handoff_to_human");
        }
        return executeHandoffInternal(runtime, req.conversationId(), req.reason(), UUID.randomUUID().toString());
    }

    private AiAgentToolKbSearchHttpResponse kbSearchInternal(RuntimeContext runtime, String query, Integer topK, JsonNode filters) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            throw new BusinessException("KB_QUERY_REQUIRED", "Informe uma consulta para buscar na base de conhecimento");
        }

        int limit = clampInt(topK == null ? 4 : topK, 1, 10, 4);
        List<String> queryTokens = tokenize(normalizedQuery);
        List<KbCandidate> candidates = new ArrayList<>();

        String selectedKnowledgeBaseId = safeTrim(runtime.agent().path("knowledgeBaseId").asText(""));
        JsonNode knowledgeBase = runtime.knowledgeBase();
        if (knowledgeBase.isArray()) {
            for (JsonNode base : knowledgeBase) {
                String baseId = safeTrim(base.path("id").asText(""));
                if (!selectedKnowledgeBaseId.isBlank() && !selectedKnowledgeBaseId.equals(baseId)) continue;

                String baseName = safeTrim(base.path("name").asText("Base de conhecimento"));
                JsonNode files = base.path("files");
                if (!files.isArray()) continue;

                for (JsonNode file : files) {
                    String title = safeTrim(file.path("title").asText(""));
                    String description = safeTrim(file.path("description").asText(""));
                    String content = safeTrim(file.path("content").asText(""));
                    String source = safeTrim(file.path("sourceName").asText(""));
                    if (source.isBlank()) source = title.isBlank() ? baseName : title;

                    String bestText = !content.isBlank() ? content : (!description.isBlank() ? description : title);
                    if (bestText.isBlank()) continue;

                    String searchable = normalize(title + " " + description + " " + content + " " + source + " " + baseName);
                    double candidateScore = round(score(searchable, normalizedQuery, queryTokens));
                    if (candidateScore <= 0.0d) continue;

                    candidates.add(new KbCandidate(source, candidateScore, bestText));
                }
            }
        }

        candidates.sort((left, right) -> Double.compare(right.score(), left.score()));

        List<AiAgentToolKbSearchHttpResponse.KbChunkHttpResponse> chunks = new ArrayList<>();
        for (KbCandidate candidate : candidates) {
            if (chunks.size() >= limit) break;
            boolean duplicated = chunks.stream()
                    .anyMatch(existing -> existing.source().equals(candidate.source()) && existing.text().equals(candidate.text()));
            if (duplicated) continue;
            chunks.add(new AiAgentToolKbSearchHttpResponse.KbChunkHttpResponse(
                    candidate.source(),
                    candidate.score(),
                    candidate.text()
            ));
        }

        boolean lowConfidence = chunks.isEmpty() || chunks.get(0).score() < 0.35d;
        return new AiAgentToolKbSearchHttpResponse(chunks, lowConfidence);
    }

    private RuntimeContext loadRuntime(UUID companyId, String agentId) {
        JpaAiAgentCompanyStateEntity state = aiAgentState.findById(companyId)
                .orElseThrow(() -> new BusinessException("AI_AGENT_STATE_NOT_FOUND", "Estado de agentes IA nao encontrado"));

        JsonNode providers = parseJson(crypto.decrypt(state.getProvidersJson()), "[]");
        JsonNode agents = parseJson(state.getAgentsJson(), "[]");
        JsonNode knowledgeBase = parseJson(state.getKnowledgeBaseJson(), "[]");

        JsonNode agent = null;
        for (JsonNode item : agents) {
            if (safeTrim(item.path("id").asText("")).equals(safeTrim(agentId))) {
                agent = item;
                break;
            }
        }
        if (agent == null || agent.isMissingNode()) {
            throw new BusinessException("AI_AGENT_NOT_FOUND", "Agente nao encontrado");
        }

        JsonNode provider = null;
        String providerId = safeTrim(agent.path("providerId").asText(""));
        for (JsonNode item : providers) {
            if (safeTrim(item.path("id").asText("")).equals(providerId)) {
                provider = item;
                break;
            }
        }

        String model = chooseModel(agent, provider);
        String apiKey = resolveOpenAiApiKey(provider);
        Set<String> allowedTools = resolveAllowedTools(agent.path("skills"));
        if (hasKnowledgeContentAvailable(agent, knowledgeBase)) {
            allowedTools.add("kb_search");
        }
        List<String> vectorStoreIds = resolveVectorStoreIds(agent, knowledgeBase);
        JsonNode crmStateNode = loadCrmState(companyId);
        return new RuntimeContext(companyId, agent, provider, knowledgeBase, crmStateNode, apiKey, model, allowedTools, vectorStoreIds);
    }

    private ObjectNode buildInitialOpenAiRequest(RuntimeContext runtime, AiAgentOrchestrateHttpRequest req, String phone, Set<String> allowedTools) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", runtime.model());
        payload.put("instructions", buildSystemPrompt(runtime));
        payload.put("temperature", resolveTemperature(runtime.agent()));
        payload.put("max_output_tokens", resolveMaxOutputTokens(runtime.agent()));

        ArrayNode input = OBJECT_MAPPER.createArrayNode();

        var history = messages.findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(req.conversationId(), runtime.companyId());
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            var item = history.get(i);
            boolean fromAssistant = item.isFromMe();
            ObjectNode msg = OBJECT_MAPPER.createObjectNode();
            msg.put("role", fromAssistant ? "assistant" : "user");
            ArrayNode content = OBJECT_MAPPER.createArrayNode();
            ObjectNode text = OBJECT_MAPPER.createObjectNode();
            text.put("type", fromAssistant ? "output_text" : "input_text");
            text.put("text", safeTrim(item.getMessageText()));
            content.add(text);
            msg.set("content", content);
            input.add(msg);
        }

        String channel = safeTrim(req.channel());
        if (channel.isBlank()) channel = "integracao";
        input.add(textInputNode("developer", "INSTRUCAO CRITICA: Responda de forma curta e direta. Evite textos longos. Priorize 1 a 3 frases por resposta, salvo quando o cliente pedir detalhes."));
        input.add(textInputNode("user", "Canal: " + channel + "\nContato: " + maskPii(phone) + "\nMensagem do cliente: " + safeTrim(req.customerMessage())));

        payload.set("input", input);
        payload.set("tools", buildOpenAiTools(runtime, allowedTools));
        payload.set("include", buildOpenAiInclude(runtime));
        return payload;
    }

    private ToolExecutionResult runToolFromModel(
            RuntimeContext runtime,
            UUID conversationId,
            FunctionCallRequest call,
            String traceId,
            Set<String> allowedTools,
            String customerMessage
    ) {
        Instant startedAt = Instant.now();
        String toolName = safeTrim(call.name());

        if (!allowedTools.contains(toolName)) {
            ObjectNode blocked = OBJECT_MAPPER.createObjectNode();
            blocked.put("ok", false);
            blocked.put("error", "AI_TOOL_NOT_ALLOWED");
            blocked.put("message", "Tool nao permitida para este agente");
            return new ToolExecutionResult(blocked, actionNode(toolName, "blocked", "Tool bloqueada pelo allowlist"), logNode(toolName, "blocked", Duration.between(startedAt, Instant.now()).toMillis(), 0, "AI_TOOL_NOT_ALLOWED"), false);
        }

        try {
            ObjectNode output = switch (toolName) {
                case "kb_search" -> {
                    JsonNode args = parseJson(call.argumentsJson(), "{}");
                    String query = requiredText(args, "query", "KB_QUERY_REQUIRED");
                    Integer topK = asInt(args.path("top_k"), 4);
                    var response = kbSearchInternal(runtime, query, topK, args.path("filters"));
                    ObjectNode node = OBJECT_MAPPER.createObjectNode();
                    node.put("ok", true);
                    node.put("low_confidence", response.lowConfidence());
                    node.set("chunks", OBJECT_MAPPER.valueToTree(response.chunks()));
                    yield node;
                }
                case "handoff_to_human" -> {
                    JsonNode args = parseJson(call.argumentsJson(), "{}");
                    String reason = requiredText(args, "reason", "HANDOFF_REASON_REQUIRED");
                    if (!isCustomerRequestingHuman(customerMessage, reason)) {
                        throw new BusinessException("HANDOFF_NOT_REQUESTED", "Handoff permitido apenas quando o contato solicitar atendimento humano");
                    }
                    var response = executeHandoffInternal(runtime, conversationId, reason, traceId);
                    ObjectNode node = OBJECT_MAPPER.createObjectNode();
                    node.put("ok", true);
                    node.put("transferred", response.transferred());
                    node.put("target_user_id", String.valueOf(response.targetUserId()));
                    node.put("protocol", response.protocol());
                    yield node;
                }
                case "kanban_move_card" -> {
                    JsonNode args = parseJson(call.argumentsJson(), "{}");
                    String targetStageId = requiredText(args, "target_stage_id", "CRM_STAGE_ID_REQUIRED");
                    String reason = safeTrim(args.path("reason").asText(""));
                    ObjectNode node = moveLeadToStage(runtime, conversationId, targetStageId, reason);
                    yield node;
                }
                case "crm_update_contact_data" -> {
                    JsonNode args = parseJson(call.argumentsJson(), "{}");
                    String fieldId = safeTrim(args.path("field_id").asText(""));
                    String fieldLabel = safeTrim(args.path("field_label").asText(""));
                    String value = requiredText(args, "value", "CRM_FIELD_VALUE_REQUIRED");
                    log.info(
                            "CRM tool call received companyId={} conversationId={} traceId={} fieldId={} fieldLabel={} value={}",
                            runtime.companyId(),
                            conversationId,
                            traceId,
                            shortenForLog(maskPii(fieldId), 80),
                            shortenForLog(maskPii(fieldLabel), 120),
                            shortenForLog(maskPii(value), 180)
                    );
                    ObjectNode node = updateLeadCrmField(runtime, conversationId, fieldId, fieldLabel, value);
                    yield node;
                }
                default -> throw new BusinessException("AI_TOOL_UNKNOWN", "Tool nao suportada: " + toolName);
            };

            boolean handoffTriggered = "handoff_to_human".equals(toolName);
            return new ToolExecutionResult(output, actionNode(toolName, "ok", summarizeAction(toolName, output)), logNode(toolName, "ok", Duration.between(startedAt, Instant.now()).toMillis(), 0, ""), handoffTriggered);
        } catch (BusinessException ex) {
            String code = safeTrim(ex.code());
            ObjectNode failed = OBJECT_MAPPER.createObjectNode();
            failed.put("ok", false);
            failed.put("error", code);
            String message = safeTrim(ex.getMessage());
            failed.put("message", message);
            return new ToolExecutionResult(failed, actionNode(toolName, "failed", "Falha: " + code), logNode(toolName, "failed", Duration.between(startedAt, Instant.now()).toMillis(), 0, code), false);
        }
    }

    private AiAgentToolHandoffHttpResponse executeHandoffInternal(RuntimeContext runtime, UUID conversationId, String reason, String traceId) {
        var conversation = conversations.findByIdAndCompanyId(conversationId, runtime.companyId())
                .orElseThrow(() -> new BusinessException("ATENDIMENTO_CONVERSATION_NOT_FOUND", "Conversa nao encontrada"));

        String transferUserId = safeTrim(runtime.agent().path("transferUserId").asText(""));
        UUID targetUserId = null;
        if (!transferUserId.isBlank()) {
            try {
                targetUserId = UUID.fromString(transferUserId);
            } catch (IllegalArgumentException ignored) {
                targetUserId = null;
            }
        }

        if (targetUserId == null) {
            targetUserId = users.findAllByCompanyId(runtime.companyId()).stream()
                    .filter(user -> user.isActive())
                    .map(user -> user.id())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("ATENDIMENTO_TARGET_NOT_FOUND", "Nenhum usuario ativo para transferir"));
        }

        var target = users.findByIdAndCompanyId(targetUserId, runtime.companyId())
                .orElseThrow(() -> new BusinessException("ATENDIMENTO_TARGET_NOT_FOUND", "Usuario de transferencia nao encontrado"));

        Instant now = Instant.now();
        conversation.setStatus("IN_PROGRESS");
        conversation.setAssignedUserId(target.id());
        conversation.setAssignedUserName(target.fullName());
        if (conversation.getStartedAt() == null) conversation.setStartedAt(now);
        conversation.setUpdatedAt(now);
        conversations.saveAndFlush(conversation);
        realtime.conversationChanged(runtime.companyId(), conversationId);

        String protocol = "handoff:" + traceId + ":" + now.getEpochSecond();
        return new AiAgentToolHandoffHttpResponse(true, target.id(), protocol + ":" + safeTrim(reason));
    }

    private ObjectNode moveLeadToStage(RuntimeContext runtime, UUID conversationId, String targetStageId, String reason) {
        JsonNode crmStages = runtime.crmState().path("stages");
        if (!crmStages.isArray() || crmStages.isEmpty()) {
            throw new BusinessException("CRM_STAGES_NOT_CONFIGURED", "Nao existem etapas configuradas no Kanban");
        }

        String stageId = safeTrim(targetStageId);
        JsonNode stageNode = null;
        for (JsonNode stage : crmStages) {
            if (stageId.equals(safeTrim(stage.path("id").asText("")))) {
                stageNode = stage;
                break;
            }
        }
        if (stageNode == null) {
            throw new BusinessException("CRM_STAGE_NOT_FOUND", "Etapa de Kanban nao encontrada");
        }

        JsonNode stagePrompts = runtime.agent().path("capabilityConfigs").path("kanbanMoveCardStagePrompts");
        String stagePrompt = safeTrim(stagePrompts.path(stageId).asText(""));
        if (stagePrompt.isBlank()) {
            throw new BusinessException("CRM_STAGE_PROMPT_NOT_CONFIGURED", "A etapa alvo nao possui prompt configurado para movimentacao");
        }

        var entity = crmState.findById(runtime.companyId()).orElseGet(() -> defaultCrmEntity(runtime.companyId()));
        ObjectNode leadStageMap = asObjectNode(parseJson(entity.getLeadStageMapJson(), "{}"));
        leadStageMap.put(conversationId.toString(), stageId);
        entity.setLeadStageMapJson(toJson(leadStageMap, "{}"));
        entity.setUpdatedAt(Instant.now());
        crmState.saveAndFlush(entity);
        realtime.crmStateChanged(runtime.companyId());

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("ok", true);
        node.put("conversation_id", conversationId.toString());
        node.put("target_stage_id", stageId);
        node.put("target_stage_title", safeTrim(stageNode.path("title").asText("")));
        node.put("reason", safeTrim(reason));
        return node;
    }

    private ObjectNode updateLeadCrmField(RuntimeContext runtime, UUID conversationId, String fieldIdRaw, String fieldLabelRaw, String valueRaw) {
        String fieldId = safeTrim(fieldIdRaw);
        String fieldLabel = safeTrim(fieldLabelRaw);
        String value = safeTrim(valueRaw);
        log.debug(
                "CRM update requested companyId={} conversationId={} fieldId={} fieldLabel={} value={}",
                runtime.companyId(),
                conversationId,
                shortenForLog(maskPii(fieldId), 80),
                shortenForLog(maskPii(fieldLabel), 120),
                shortenForLog(maskPii(value), 180)
        );
        if (fieldId.isBlank() && fieldLabel.isBlank()) {
            throw new BusinessException("CRM_FIELD_IDENTIFIER_REQUIRED", "Informe field_id ou field_label");
        }
        if (value.isBlank()) throw new BusinessException("CRM_FIELD_VALUE_REQUIRED", "Campo value obrigatorio");

        Set<String> allowedFieldIds = new HashSet<>(asStringList(runtime.agent().path("capabilityConfigs").path("crmFieldIdsToFill")));
        JsonNode customFields = runtime.crmState().path("customFields");
        List<JsonNode> allowedFields = new ArrayList<>();
        if (customFields.isArray()) {
            for (JsonNode customField : customFields) {
                String candidateId = safeTrim(customField.path("id").asText(""));
                if (candidateId.isBlank()) continue;
                if (!allowedFieldIds.contains(candidateId)) continue;
                allowedFields.add(customField);
            }
        }

        JsonNode fieldNode = null;
        if (!fieldId.isBlank()) {
            for (JsonNode candidate : allowedFields) {
                if (fieldId.equals(safeTrim(candidate.path("id").asText("")))) {
                    fieldNode = candidate;
                    break;
                }
            }
        }
        if (fieldNode == null && !fieldLabel.isBlank()) {
            String normalizedLabel = normalize(fieldLabel);
            for (JsonNode candidate : allowedFields) {
                String candidateLabel = normalize(candidate.path("label").asText(""));
                if (candidateLabel.equals(normalizedLabel)) {
                    fieldNode = candidate;
                    break;
                }
            }
            if (fieldNode == null) {
                for (JsonNode candidate : allowedFields) {
                    String candidateLabel = normalize(candidate.path("label").asText(""));
                    if (candidateLabel.contains(normalizedLabel) || normalizedLabel.contains(candidateLabel)) {
                        fieldNode = candidate;
                        break;
                    }
                }
            }
        }

        if (fieldNode == null && !fieldId.isBlank()) {
            String normalizedFieldId = normalize(fieldId);
            for (JsonNode candidate : allowedFields) {
                String candidateLabel = normalize(candidate.path("label").asText(""));
                if (candidateLabel.equals(normalizedFieldId) || candidateLabel.contains(normalizedFieldId)) {
                    fieldNode = candidate;
                    break;
                }
            }
        }

        if (fieldNode == null && allowedFieldIds.isEmpty()) {
            log.warn("CRM update blocked: no configured fields companyId={} conversationId={} agentId={}",
                    runtime.companyId(),
                    conversationId,
                    safeTrim(runtime.agent().path("id").asText("")));
            throw new BusinessException("CRM_FIELDS_NOT_CONFIGURED", "Nenhum campo foi configurado para preenchimento automatico neste agente");
        }
        if (fieldNode == null && allowedFields.size() == 1) {
            fieldNode = allowedFields.get(0);
        }
        if (fieldNode == null) {
            List<String> allowedFieldDebug = new ArrayList<>();
            for (JsonNode customField : allowedFields) {
                String candidateId = safeTrim(customField.path("id").asText(""));
                String candidateLabel = safeTrim(customField.path("label").asText(""));
                if (candidateId.isBlank() && candidateLabel.isBlank()) continue;
                allowedFieldDebug.add((candidateLabel.isBlank() ? "sem_label" : candidateLabel) + " (" + (candidateId.isBlank() ? "sem_id" : candidateId) + ")");
            }
            log.warn(
                    "CRM update blocked: field not allowed companyId={} conversationId={} requestedFieldId={} requestedFieldLabel={} allowedFieldIds={} allowedFields={}",
                    runtime.companyId(),
                    conversationId,
                    shortenForLog(maskPii(fieldId), 80),
                    shortenForLog(maskPii(fieldLabel), 120),
                    allowedFieldIds,
                    allowedFieldDebug
            );
            throw new BusinessException("CRM_FIELD_NOT_ALLOWED", "Campo nao permitido para preenchimento por este agente");
        }

        fieldId = safeTrim(fieldNode.path("id").asText(fieldId));

        var entity = crmState.findById(runtime.companyId()).orElseGet(() -> defaultCrmEntity(runtime.companyId()));
        ObjectNode leadFieldValues = asObjectNode(parseJson(entity.getLeadFieldValuesJson(), "{}"));
        ObjectNode leadValues = leadFieldValues.has(conversationId.toString()) && leadFieldValues.get(conversationId.toString()).isObject()
                ? (ObjectNode) leadFieldValues.get(conversationId.toString())
                : OBJECT_MAPPER.createObjectNode();
        leadValues.put(fieldId, value);
        leadFieldValues.set(conversationId.toString(), leadValues);
        entity.setLeadFieldValuesJson(toJson(leadFieldValues, "{}"));
        entity.setUpdatedAt(Instant.now());
        crmState.saveAndFlush(entity);
        realtime.crmStateChanged(runtime.companyId());
        log.info(
                "CRM update persisted companyId={} conversationId={} fieldId={} fieldLabel={} value={}",
                runtime.companyId(),
                conversationId,
                fieldId,
                safeTrim(fieldNode.path("label").asText(fieldId)),
                shortenForLog(maskPii(value), 180)
        );

        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("ok", true);
        node.put("conversation_id", conversationId.toString());
        node.put("field_id", fieldId);
        node.put("field_label", safeTrim(fieldNode.path("label").asText(fieldId)));
        node.put("value", value);
        return node;
    }

    private void tryAutoFillCrmDescription(
            RuntimeContext runtime,
            UUID conversationId,
            String traceId,
            ArrayNode actions,
            ArrayNode toolLogs
    ) {
        if (!runtime.allowedTools().contains("crm_update_contact_data")) return;

        Set<String> allowedFieldIds = new HashSet<>(asStringList(runtime.agent().path("capabilityConfigs").path("crmFieldIdsToFill")));
        if (allowedFieldIds.isEmpty()) return;

        JsonNode customFields = runtime.crmState().path("customFields");
        if (!customFields.isArray()) return;

        JsonNode descriptionField = null;
        for (JsonNode customField : customFields) {
            String candidateId = safeTrim(customField.path("id").asText(""));
            if (candidateId.isBlank() || !allowedFieldIds.contains(candidateId)) continue;
            String label = customField.path("label").asText("");
            if (isDescriptionLikeFieldLabel(label)) {
                descriptionField = customField;
                break;
            }
        }
        if (descriptionField == null) return;

        String targetFieldId = safeTrim(descriptionField.path("id").asText(""));
        if (targetFieldId.isBlank()) return;

        String summary = buildCrmDescriptionSummary(runtime, conversationId);
        if (summary.isBlank()) return;

        var entity = crmState.findById(runtime.companyId()).orElse(null);
        if (entity != null) {
            ObjectNode leadFieldValues = asObjectNode(parseJson(entity.getLeadFieldValuesJson(), "{}"));
            JsonNode existingLeadValuesNode = leadFieldValues.get(conversationId.toString());
            String existing = existingLeadValuesNode != null && existingLeadValuesNode.isObject()
                    ? safeTrim(existingLeadValuesNode.path(targetFieldId).asText(""))
                    : "";
            if (!existing.isBlank() && existing.equals(summary)) return;
        }

        Instant startedAt = Instant.now();
        try {
            updateLeadCrmField(runtime, conversationId, targetFieldId, "", summary);
            actions.add(actionNode("crm_update_contact_data", "ok", "Resumo do atendimento salvo no campo de descricao"));
            toolLogs.add(logNode("crm_update_contact_data", "ok", Duration.between(startedAt, Instant.now()).toMillis(), 0, ""));
            log.info(
                    "CRM description auto-summary persisted companyId={} conversationId={} traceId={} fieldId={}",
                    runtime.companyId(),
                    conversationId,
                    traceId,
                    targetFieldId
            );
        } catch (BusinessException ex) {
            String code = safeTrim(ex.code());
            actions.add(actionNode("crm_update_contact_data", "failed", "Falha no resumo automatico: " + code));
            toolLogs.add(logNode("crm_update_contact_data", "failed", Duration.between(startedAt, Instant.now()).toMillis(), 0, code));
            log.warn(
                    "CRM description auto-summary failed companyId={} conversationId={} traceId={} errorCode={} message={}",
                    runtime.companyId(),
                    conversationId,
                    traceId,
                    code,
                    safeTrim(ex.getMessage())
            );
        }
    }

    private void tryAutoFillMissingCrmFields(
            RuntimeContext runtime,
            UUID conversationId,
            String traceId,
            ArrayNode actions,
            ArrayNode toolLogs
    ) {
        if (!runtime.allowedTools().contains("crm_update_contact_data")) return;

        Set<String> allowedFieldIds = new HashSet<>(asStringList(runtime.agent().path("capabilityConfigs").path("crmFieldIdsToFill")));
        if (allowedFieldIds.isEmpty()) return;

        JsonNode customFieldsNode = runtime.crmState().path("customFields");
        if (!customFieldsNode.isArray()) return;

        ObjectNode existingValues = OBJECT_MAPPER.createObjectNode();
        var entity = crmState.findById(runtime.companyId()).orElse(null);
        if (entity != null) {
            ObjectNode leadFieldValues = asObjectNode(parseJson(entity.getLeadFieldValuesJson(), "{}"));
            JsonNode current = leadFieldValues.get(conversationId.toString());
            if (current != null && current.isObject()) existingValues = (ObjectNode) current;
        }

        ArrayNode missingFields = OBJECT_MAPPER.createArrayNode();
        Set<String> missingFieldIds = new HashSet<>();
        for (JsonNode field : customFieldsNode) {
            String fieldId = safeTrim(field.path("id").asText(""));
            if (fieldId.isBlank() || !allowedFieldIds.contains(fieldId)) continue;
            String label = safeTrim(field.path("label").asText(""));
            if (isDescriptionLikeFieldLabel(label)) continue;
            String currentValue = safeTrim(existingValues.path(fieldId).asText(""));
            if (!currentValue.isBlank()) continue;
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("field_id", fieldId);
            item.put("field_label", label);
            missingFields.add(item);
            missingFieldIds.add(fieldId);
        }
        if (missingFields.isEmpty()) return;

        String transcript = buildConversationTranscript(runtime, conversationId, 40);
        if (transcript.isBlank()) return;

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", runtime.model());
        payload.put("temperature", 0.2d);
        payload.put("max_output_tokens", 800);
        payload.put("instructions", "Extraia dados de CRM do dialogo. Seja sensivel a inferencias contextuais claras, sem inventar.");
        ArrayNode input = OBJECT_MAPPER.createArrayNode();
        input.add(textInputNode("user",
                "Campos faltantes (JSON): " + missingFields + "\n" +
                        "Conversa:\n" + transcript + "\n\n" +
                        "Retorne APENAS JSON valido neste formato: " +
                        "{\"fields\":[{\"field_id\":\"...\",\"value\":\"...\",\"confidence\":0.0,\"reason\":\"...\"}]}. " +
                        "confidence de 0 a 1. Se nao houver evidencias, retorne fields vazio."
        ));
        payload.set("input", input);

        try {
            JsonNode response = callOpenAiResponses(runtime.openAiApiKey(), payload);
            String jsonText = safeTrim(extractAssistantText(response));
            JsonNode root = parseJson(jsonText, "{}");
            JsonNode fields = root.path("fields");
            if (!fields.isArray()) return;

            for (JsonNode candidate : fields) {
                String fieldId = safeTrim(candidate.path("field_id").asText(""));
                String value = safeTrim(candidate.path("value").asText(""));
                double confidence = candidate.path("confidence").asDouble(0.0d);
                if (fieldId.isBlank() || value.isBlank()) continue;
                if (!missingFieldIds.contains(fieldId)) continue;
                if (confidence < 0.35d) continue;

                Instant startedAt = Instant.now();
                try {
                    updateLeadCrmField(runtime, conversationId, fieldId, "", value);
                    actions.add(actionNode("crm_update_contact_data", "ok", "Campo preenchido por inferencia contextual"));
                    toolLogs.add(logNode("crm_update_contact_data", "ok", Duration.between(startedAt, Instant.now()).toMillis(), 0, ""));
                    log.info(
                            "CRM field auto-inferred companyId={} conversationId={} traceId={} fieldId={} confidence={}",
                            runtime.companyId(),
                            conversationId,
                            traceId,
                            fieldId,
                            confidence
                    );
                } catch (BusinessException ex) {
                    String code = safeTrim(ex.code());
                    actions.add(actionNode("crm_update_contact_data", "failed", "Falha em inferencia contextual: " + code));
                    toolLogs.add(logNode("crm_update_contact_data", "failed", Duration.between(startedAt, Instant.now()).toMillis(), 0, code));
                    log.warn(
                            "CRM field auto-inferred failed companyId={} conversationId={} traceId={} fieldId={} errorCode={}",
                            runtime.companyId(),
                            conversationId,
                            traceId,
                            fieldId,
                            code
                    );
                }
            }
        } catch (BusinessException ex) {
            log.warn(
                    "CRM missing-fields extraction skipped companyId={} conversationId={} traceId={} errorCode={}",
                    runtime.companyId(),
                    conversationId,
                    traceId,
                    safeTrim(ex.code())
            );
        }
    }

    private String buildCrmDescriptionSummary(RuntimeContext runtime, UUID conversationId) {
        String transcript = buildConversationTranscript(runtime, conversationId, 40);
        if (transcript.isBlank()) return "";

        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("model", runtime.model());
        payload.put("temperature", 0.3d);
        payload.put("max_output_tokens", 260);
        payload.put("instructions", "Gere um resumo executivo curto e claro do atendimento.");
        ArrayNode input = OBJECT_MAPPER.createArrayNode();
        input.add(textInputNode("user",
                "Resuma a conversa abaixo para CRM em portugues do Brasil, em 2 a 4 frases, sem bullets e sem repetir mensagens literalmente. " +
                        "Inclua contexto do lead, necessidade principal, objeccoes/sinais relevantes e proximo passo.\n\nConversa:\n" + transcript
        ));
        payload.set("input", input);

        try {
            JsonNode response = callOpenAiResponses(runtime.openAiApiKey(), payload);
            String summary = safeTrim(extractAssistantText(response));
            summary = summary.replaceAll("\\s+", " ").trim();
            if (summary.length() > 900) summary = summary.substring(0, 900) + "...";
            if (!summary.isBlank()) return summary;
        } catch (BusinessException ex) {
            log.warn(
                    "CRM description summary by model failed companyId={} conversationId={} errorCode={}",
                    runtime.companyId(),
                    conversationId,
                    safeTrim(ex.code())
            );
        }

        return buildFallbackDescriptionSummary(runtime, conversationId);
    }

    private String buildFallbackDescriptionSummary(RuntimeContext runtime, UUID conversationId) {
        var history = messages.findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(conversationId, runtime.companyId());
        if (history.isEmpty()) return "";

        int start = Math.max(0, history.size() - 20);
        List<String> leadSnippets = new ArrayList<>();
        List<String> agentSnippets = new ArrayList<>();

        for (int i = start; i < history.size(); i++) {
            var message = history.get(i);
            String text = safeTrim(message.getMessageText());
            if (text.isBlank()) {
                String type = normalize(message.getMessageType());
                text = switch (type) {
                    case "audio" -> "[audio]";
                    case "image" -> "[imagem]";
                    case "video" -> "[video]";
                    case "document" -> "[documento]";
                    case "location" -> "[localizacao]";
                    default -> "";
                };
            }
            if (text.isBlank()) continue;
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > 180) text = text.substring(0, 180) + "...";
            if (message.isFromMe()) {
                agentSnippets.add(text);
            } else {
                leadSnippets.add(text);
            }
        }

        if (leadSnippets.isEmpty() && agentSnippets.isEmpty()) return "";

        String leadPart = joinLimited(leadSnippets, 4, " | ");
        String agentPart = joinLimited(agentSnippets, 2, " | ");
        StringBuilder summary = new StringBuilder("Resumo do atendimento: ");
        if (!leadPart.isBlank()) {
            summary.append("Lead informou ").append(leadPart);
        }
        if (!agentPart.isBlank()) {
            if (!leadPart.isBlank()) summary.append(". ");
            summary.append("Agente orientou ").append(agentPart);
        }
        String value = summary.toString().trim();
        if (value.length() > 900) value = value.substring(0, 900) + "...";
        return value;
    }

    private String buildConversationTranscript(RuntimeContext runtime, UUID conversationId, int maxMessages) {
        var history = messages.findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(conversationId, runtime.companyId());
        if (history.isEmpty()) return "";
        int start = Math.max(0, history.size() - Math.max(1, maxMessages));
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < history.size(); i++) {
            var item = history.get(i);
            String text = safeTrim(item.getMessageText());
            if (text.isBlank()) continue;
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > 280) text = text.substring(0, 280) + "...";
            sb.append(item.isFromMe() ? "Agente: " : "Lead: ").append(text).append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isDescriptionLikeFieldLabel(String labelRaw) {
        String label = normalize(labelRaw);
        return label.contains("descricao")
                || label.contains("resumo")
                || label.contains("observacao")
                || label.contains("observacoes")
                || label.contains("anotacao")
                || label.contains("anotacoes");
    }

    private String joinLimited(List<String> values, int maxItems, String separator) {
        if (values == null || values.isEmpty() || maxItems <= 0) return "";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String item : values) {
            String text = safeTrim(item);
            if (text.isBlank()) continue;
            if (count >= maxItems) break;
            if (!sb.isEmpty()) sb.append(separator);
            sb.append(text);
            count++;
        }
        return sb.toString();
    }

    private JsonNode loadCrmState(UUID companyId) {
        var entity = crmState.findById(companyId).orElseGet(() -> defaultCrmEntity(companyId));
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.set("stages", parseJson(entity.getStagesJson(), "[]"));
        root.set("leadStageMap", parseJson(entity.getLeadStageMapJson(), "{}"));
        root.set("customFields", parseJson(entity.getCustomFieldsJson(), "[]"));
        root.set("leadFieldValues", parseJson(entity.getLeadFieldValuesJson(), "{}"));
        root.set("leadFieldOrder", parseJson(entity.getLeadFieldsOrderJson(), "[]"));
        return root;
    }

    private JpaCrmCompanyStateEntity defaultCrmEntity(UUID companyId) {
        Instant now = Instant.now();
        JpaCrmCompanyStateEntity entity = new JpaCrmCompanyStateEntity();
        entity.setCompanyId(companyId);
        entity.setStagesJson("[]");
        entity.setLeadStageMapJson("{}");
        entity.setCustomFieldsJson("[]");
        entity.setLeadFieldValuesJson("{}");
        entity.setLeadFieldsOrderJson("[]");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private HttpResponse<String> sendHttp(HttpRequest request) throws Exception {
        return httpRequestExecutor.send(request);
    }

    private long extractRetryDelayMillis(String body) {
        return 2000L;
    }

    private JsonNode callOpenAiResponses(String apiKey, JsonNode payload) {
    if (apiKey.isBlank()) {
        throw new BusinessException("OPENAI_API_KEY_MISSING", "Configure OPENAI_API_KEY ou apiKey no provedor do agente");
    }

    int maxAttempts = 3;
    long fallbackDelayMs = 2000L;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(35))
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_REQUEST_BUILD_ERROR", "Nao foi possivel montar requisicao para OpenAI");
        }

        HttpResponse<String> response;
        try {
            response = sendHttp(request);
        } catch (Exception ex) {
            throw new BusinessException("OPENAI_CONNECTION_ERROR", "Falha de conexao com OpenAI");
        }

        if (response.statusCode() >= 200 && response.statusCode() <= 299) {
            return parseJson(response.body(), "{}");
        }

        if (response.statusCode() == 429 && attempt < maxAttempts) {
            long delayMs = extractRetryDelayMillis(response.body());
            log.warn("OpenAI 429 rate limit. attempt={} delayMs={}", attempt, delayMs);
            try {
                Thread.sleep(Math.max(delayMs, fallbackDelayMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("OPENAI_RETRY_INTERRUPTED", "Retry interrompido");
            }
            continue;
        }

        String detail = extractOpenAiErrorDetail(response.body());
        throw new BusinessException("OPENAI_UPSTREAM_ERROR", "OpenAI retornou status " + response.statusCode() + ": " + detail);
    }

    throw new BusinessException("OPENAI_UPSTREAM_ERROR", "Falha ao chamar OpenAI apos retries");
}

    private double resolveTemperature(JsonNode agent) {
        double value = agent.path("temperature").asDouble(1.0d);
        if (Double.isNaN(value) || Double.isInfinite(value)) return 1.0d;
        if (value < 0.0d) return 0.0d;
        if (value > 2.0d) return 2.0d;
        return value;
    }

    private int resolveMaxOutputTokens(JsonNode agent) {
        int value = agent.path("maxTokensPerMessage").asInt(400);
        return clampInt(value, 80, 4000, 400);
    }

    private ArrayNode buildOpenAiTools(RuntimeContext runtime, Set<String> allowedTools) {
        ArrayNode tools = OBJECT_MAPPER.createArrayNode();
        if (!runtime.vectorStoreIds().isEmpty()) {
            ObjectNode fileSearch = OBJECT_MAPPER.createObjectNode();
            fileSearch.put("type", "file_search");
            ArrayNode vectorStoreIdsNode = OBJECT_MAPPER.createArrayNode();
            for (String vectorStoreId : runtime.vectorStoreIds()) vectorStoreIdsNode.add(vectorStoreId);
            fileSearch.set("vector_store_ids", vectorStoreIdsNode);
            fileSearch.put("max_num_results", 6);
            tools.add(fileSearch);
        }
        if (allowedTools.contains("kb_search")) {
            tools.add(functionTool("kb_search", "Busca trechos da base de conhecimento para fundamentar a resposta.",
                    parametersSchema(Map.of("query", stringSchema("Consulta do cliente"), "top_k", integerSchema("Quantidade de trechos (1-10)"), "filters", objectSchema("Filtros opcionais por tags")), List.of("query"))));
        }
        if (allowedTools.contains("handoff_to_human")) {
            tools.add(functionTool("handoff_to_human", "Transfere o atendimento para um humano quando necessario.", parametersSchema(Map.of("reason", stringSchema("Motivo do handoff")), List.of("reason"))));
        }
        if (allowedTools.contains("kanban_move_card")) {
            tools.add(functionTool("kanban_move_card", "Move o card/lead para uma etapa do Kanban da empresa.",
                    parametersSchema(Map.of(
                            "target_stage_id", stringSchema("ID da etapa de destino no Kanban"),
                            "reason", stringSchema("Resumo curto do motivo da movimentacao")
                    ), List.of("target_stage_id", "reason"))));
        }
        if (allowedTools.contains("crm_update_contact_data")) {
            tools.add(functionTool("crm_update_contact_data", "Atualiza um campo do card do lead no CRM.",
                    parametersSchema(Map.of(
                            "field_id", stringSchema("ID do campo customizado do CRM"),
                            "field_label", stringSchema("Nome do campo no CRM (use quando nao souber o ID)"),
                            "value", stringSchema("Valor confirmado pelo lead para gravar no campo")
                    ), List.of("value"))));
        }
        return tools;
    }

    private ArrayNode buildOpenAiInclude(RuntimeContext runtime) {
        ArrayNode include = OBJECT_MAPPER.createArrayNode();
        if (!runtime.vectorStoreIds().isEmpty()) include.add("file_search_call.results");
        return include;
    }

    private ObjectNode functionTool(String name, String description, JsonNode parameters) {
        ObjectNode tool = OBJECT_MAPPER.createObjectNode();
        tool.put("type", "function");
        tool.put("name", name);
        tool.put("description", description);
        tool.set("parameters", parameters);
        return tool;
    }

    private ObjectNode parametersSchema(Map<String, JsonNode> properties, List<String> required) {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = OBJECT_MAPPER.createObjectNode();
        for (var entry : properties.entrySet()) props.set(entry.getKey(), entry.getValue());
        schema.set("properties", props);
        ArrayNode requiredArray = OBJECT_MAPPER.createArrayNode();
        for (String key : required) requiredArray.add(key);
        schema.set("required", requiredArray);
        return schema;
    }

    private ObjectNode stringSchema(String description) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "string");
        node.put("description", description);
        return node;
    }

    private ObjectNode integerSchema(String description) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "integer");
        node.put("description", description);
        node.put("minimum", 1);
        node.put("maximum", 10);
        return node;
    }

    private ObjectNode durationMinutesSchema(String description) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "integer");
        node.put("description", description);
        node.put("minimum", 15);
        node.put("maximum", 240);
        return node;
    }

    private ObjectNode objectSchema(String description) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "object");
        node.put("description", description);
        node.put("additionalProperties", true);
        return node;
    }

    private ObjectNode arrayOfStringsSchema(String description) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "array");
        node.put("description", description);
        ObjectNode items = OBJECT_MAPPER.createObjectNode();
        items.put("type", "string");
        node.set("items", items);
        return node;
    }

    private ObjectNode textInputNode(String role, String text) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("role", role);
        ArrayNode content = OBJECT_MAPPER.createArrayNode();
        ObjectNode contentItem = OBJECT_MAPPER.createObjectNode();
        contentItem.put("type", "input_text");
        contentItem.put("text", text);
        content.add(contentItem);
        node.set("content", content);
        return node;
    }

    private List<FunctionCallRequest> extractFunctionCalls(JsonNode response) {
        List<FunctionCallRequest> calls = new ArrayList<>();
        JsonNode output = response.path("output");
        if (!output.isArray()) return calls;
        for (JsonNode item : output) {
            if (!"function_call".equals(safeTrim(item.path("type").asText("")))) continue;
            String callId = safeTrim(item.path("call_id").asText(""));
            if (callId.isBlank()) callId = safeTrim(item.path("id").asText(""));
            String name = safeTrim(item.path("name").asText(""));
            String args = safeTrim(item.path("arguments").asText("{}"));
            if (!name.isBlank()) calls.add(new FunctionCallRequest(callId, name, args));
        }
        return calls;
    }

    private String extractAssistantText(JsonNode response) {
        String direct = safeTrim(response.path("output_text").asText(""));
        if (!direct.isBlank()) return direct;
        JsonNode output = response.path("output");
        if (!output.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode item : output) {
            if (!"message".equals(safeTrim(item.path("type").asText("")))) continue;
            JsonNode content = item.path("content");
            if (!content.isArray()) continue;
            for (JsonNode piece : content) {
                String text = safeTrim(piece.path("text").asText(""));
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
            }
        }
        return sb.toString().trim();
    }

    private void persistRunLog(UUID companyId, AiAgentOrchestrateHttpRequest req, String traceId, boolean handoff, String finalText, JsonNode actions, JsonNode toolLogs, JsonNode openAiRequest, JsonNode openAiResponse) {
        JpaAiAgentRunLogEntity row = new JpaAiAgentRunLogEntity();
        row.setId(UUID.randomUUID());
        row.setCompanyId(companyId);
        row.setConversationId(req.conversationId());
        row.setAgentId(safeTrim(req.agentId()));
        row.setTraceId(traceId);
        row.setCustomerMessage(maskPii(safeTrim(req.customerMessage())));
        row.setFinalText(maskPii(safeTrim(finalText)));
        row.setHandoff(handoff);
        row.setActionsJson(toJson(actions, "[]"));
        row.setToolLogsJson(toJson(toolLogs, "[]"));
        row.setRequestPayloadJson(maskPii(toJson(openAiRequest, "{}")));
        row.setResponsePayloadJson(maskPii(toJson(openAiResponse, "{}")));
        row.setCreatedAt(Instant.now());
        runLogs.save(row);
    }

    private AiAgentOrchestrateHttpResponse maybeHandleCalendarFlow(
            RuntimeContext runtime,
            AiAgentOrchestrateHttpRequest req,
            String traceId,
            com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoConversationEntity conversation
    ) {
        AiAgentCalendarOrchestrator.CalendarFlowResponse flow = calendarOrchestrator.maybeHandleMessage(
                runtime.companyId(),
                req.conversationId(),
                req.customerMessage(),
                conversation.getDisplayName(),
                runtime.allowedTools().contains("schedule_google_meeting")
        );
        if (flow == null) return null;
        ArrayNode actions = OBJECT_MAPPER.createArrayNode();
        ArrayNode toolLogs = OBJECT_MAPPER.createArrayNode();
        persistRunLog(runtime.companyId(), req, traceId, false, flow.finalText(), actions, toolLogs, flow.requestMeta(), flow.responseMeta());
        return new AiAgentOrchestrateHttpResponse(flow.finalText(), List.of(), List.of(), false, traceId);
    }

    private String buildSystemPrompt(RuntimeContext runtime) {
        JsonNode agent = runtime.agent();
        JsonNode capabilityConfigs = agent.path("capabilityConfigs");
        StringBuilder sb = new StringBuilder();
        ZoneId nowZone = ZoneId.of("America/Sao_Paulo");
        LocalDateTime now = LocalDateTime.now(nowZone);
        sb.append("Voce e um agente de atendimento ao cliente.\n");
        sb.append("Nome: ").append(safeTrim(agent.path("name").asText("Agente IA"))).append("\n");
        sb.append("Perfil: ").append(safeTrim(agent.path("profile").asText("Atendimento"))).append("\n");
        sb.append("Objetivo: ").append(safeTrim(agent.path("objective").asText("Resolver demandas dos clientes"))).append("\n");
        sb.append("Estilo de comunicacao: ").append(safeTrim(agent.path("communicationStyle").asText("Objetivo e cordial"))).append("\n\n");
        sb.append("Contexto de canal fixo: voce ja esta conversando com o cliente no canal atual.\n");
        sb.append("- Nao diga que vai enviar algo depois por outro canal, pois a conversa atual ja esta ativa.\n");
        sb.append("- Nao prometa lembretes futuros, envio posterior de mensagem ou link fora da conversa atual.\n\n");
        sb.append("- Nunca invente horarios, disponibilidade ou links de reuniao. Quando o tema for agendamento, isso e decidido pelo backend.\n\n");
        sb.append("Data/hora atual de referencia (").append(nowZone.getId()).append("): ").append(now).append("\n");
        JsonNode stageBehaviors = agent.path("stageBehaviors");
        if (stageBehaviors.isArray()) {
            sb.append("Roteiro por etapas (ordem obrigatoria):\n");
            for (JsonNode stage : stageBehaviors) {
                sb.append("- ").append(safeTrim(stage.path("stage").asText("Etapa"))).append(": ").append(safeTrim(stage.path("instruction").asText(""))).append("\n");
            }
            sb.append("\nRegra rigida de condução por etapas:");
            sb.append("\n- Siga sempre o roteiro por etapas em ordem.");
            sb.append("\n- Se o cliente fizer pergunta fora da etapa atual, responda objetivamente e retorne imediatamente ao roteiro.");
            sb.append("\n- Após responder desvios, faça a proxima pergunta da etapa/roteiro para retomar o fluxo.");
            sb.append("\n- Nao abandone o roteiro de qualificacao e agendamento, exceto em casos de handoff, encerramento explicito do cliente ou restricao.");
            sb.append("\n- Coletas de dados para CRM/Kanban sao auxiliares: apos coletar um dado, retome imediatamente a etapa atual.");
            sb.append("\n- Nunca substitua o roteiro principal por um questionario de cadastro.");
        }
        JsonNode rules = agent.path("rules");
        if (rules.isArray() && !rules.isEmpty()) {
            sb.append("\nRegras obrigatorias (prioridade alta):");
            int index = 1;
            for (JsonNode rule : rules) {
                String text = safeTrim(rule.asText(""));
                if (text.isBlank()) continue;
                sb.append("\n").append(index++).append(". ").append(text);
            }
            sb.append("\n- Nunca descumpra as regras obrigatorias acima.");
        }
        JsonNode restrictions = agent.path("restrictions");
        if (restrictions.isArray() && !restrictions.isEmpty()) {
            sb.append("\n\nRestricoes (proibicoes explicitas):");
            int index = 1;
            for (JsonNode restriction : restrictions) {
                String text = safeTrim(restriction.asText(""));
                if (text.isBlank()) continue;
                sb.append("\n").append(index++).append(". ").append(text);
            }
            sb.append("\n- Se a solicitacao do cliente conflitar com restricoes, recuse com educacao e ofereca alternativa permitida.");
        }
        if (runtime.allowedTools().contains("kanban_move_card")) {
            sb.append("\n\nRegras de movimentacao de cards no Kanban:");
            sb.append("\n- Use a tool kanban_move_card quando houver evidencia clara para avancar a etapa do lead.");
            sb.append("\n- Sempre envie target_stage_id valido e um reason curto com a justificativa.");
            JsonNode stagePrompts = capabilityConfigs.path("kanbanMoveCardStagePrompts");
            JsonNode crmStages = runtime.crmState().path("stages");
            if (crmStages.isArray() && !crmStages.isEmpty()) {
                sb.append("\n- Criterios configurados por etapa:");
                for (JsonNode stage : crmStages) {
                    String stageId = safeTrim(stage.path("id").asText(""));
                    String stageTitle = safeTrim(stage.path("title").asText("Etapa"));
                    String prompt = safeTrim(stagePrompts.path(stageId).asText(""));
                    if (stageId.isBlank() || prompt.isBlank()) continue;
                    sb.append("\n  - ").append(stageTitle).append(" (").append(stageId).append("): ").append(prompt);
                }
            }
        }
        if (runtime.allowedTools().contains("crm_update_contact_data")) {
            sb.append("\n\nRegras de atualizacao de dados do CRM:");
            sb.append("\n- Extraia somente dados confirmados pelo lead.");
            sb.append("\n- Use a tool crm_update_contact_data para preencher campos configurados durante a conversa.");
            sb.append("\n- Na tool crm_update_contact_data, pode enviar field_id ou field_label (nome do campo) junto com value.");
            sb.append("\n- Sempre que identificar na fala do lead um valor de algum campo permitido, chame a tool antes de responder.");
            sb.append("\n- Em toda nova mensagem do lead, verifique primeiro se ha dado de CRM para registrar; se houver, registre imediatamente.");
            sb.append("\n- Se a mensagem trouxer mais de um dado (ex.: nicho e faturamento), chame a tool uma vez para cada campo.");
            sb.append("\n- Seja sensivel a sinais indiretos: mapeie equivalencias semanticas (ex.: ramo, nicho, segmento, area de atuacao).");
            sb.append("\n- Quando houver evidencia contextual forte e sem contradicao, registre o dado mesmo que nao esteja literal.");
            sb.append("\n- Se a confianca estiver baixa, faca uma pergunta curta de confirmacao e, apos confirmar, salve imediatamente.");
            sb.append("\n- Normalize valores numericos quando possivel (ex.: R$ 20 mil -> 20000) preservando o sentido informado.");
            sb.append("\n- Coletar dados do CRM nao muda a ordem das etapas: salve o dado e volte para o fluxo principal.");
            sb.append("\n- Evite sequencias longas de perguntas de cadastro; mantenha o foco no objetivo da etapa.");
            JsonNode allowedFieldIds = capabilityConfigs.path("crmFieldIdsToFill");
            JsonNode customFields = runtime.crmState().path("customFields");
            if (allowedFieldIds.isArray() && customFields.isArray()) {
                sb.append("\n- Campos permitidos para preenchimento:");
                for (JsonNode fieldIdNode : allowedFieldIds) {
                    String fieldId = safeTrim(fieldIdNode.asText(""));
                    if (fieldId.isBlank()) continue;
                    String label = fieldId;
                    for (JsonNode customField : customFields) {
                        if (fieldId.equals(safeTrim(customField.path("id").asText("")))) {
                            label = safeTrim(customField.path("label").asText(fieldId));
                            break;
                        }
                    }
                    sb.append("\n  - ").append(label).append(" (").append(fieldId).append(")");
                }
            }
        }
        sb.append("\n\nRegras de uso da base de conhecimento:");
        sb.append("\n- Para duvidas factuais sobre servicos, produtos, politicas, processos e documentos, consulte file_search e/ou kb_search antes de responder.");
        sb.append("\n- Nao invente informacoes quando nao houver evidencias na base.");
        sb.append("\n- Quando usar file_search ou kb_search, priorize respostas fundamentadas nos trechos retornados.");
        sb.append("\n\nRegras de formato da resposta:");
        sb.append("\n- Nao use links no formato markdown com colchetes, como [texto](url).");
        sb.append("\n- Quando precisar compartilhar link, escreva em texto simples: texto - https://...");
        sb.append("\n- Evite marcadores de citacao em colchetes (ex.: [1], [fonte], [@perfil]).");
        sb.append("\n- Prefira respostas curtas e diretas (1 a 3 frases), evitando blocos longos, salvo quando o cliente pedir detalhes.");
        sb.append("\n\nUse as tools quando necessario. Se faltar confianca, prefira transferir para humano.");
        return sb.toString();
    }

    private String sanitizeAssistantOutput(String text) {
        String value = safeTrim(text);
        if (value.isBlank()) return value;
        Matcher markdownLinkMatcher = MARKDOWN_LINK_PATTERN.matcher(value);
        value = markdownLinkMatcher.replaceAll("$1 - $2");
        value = BRACKET_ONLY_CITATION_PATTERN.matcher(value).replaceAll(" ");
        value = value
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n[ \\t]+", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return value;
    }

    private String enforceActiveChannelPolicy(String text) {
        String value = safeTrim(text);
        if (value.isBlank()) return value;

        String[] sentences = value.split("(?<=[.!?])\\s+");
        List<String> kept = new ArrayList<>();
        for (String sentence : sentences) {
            String current = safeTrim(sentence);
            if (current.isBlank()) continue;
            String normalized = normalize(current);
            boolean mentionsExternalChannel = normalized.contains("whatsapp")
                    || normalized.contains("telefone")
                    || normalized.contains("ligacao");
            boolean promisesLater =
                    normalized.contains("te chamar")
                            || normalized.contains("entrar em contato")
                            || normalized.contains("entraremos em contato")
                            || normalized.contains("vou registrar")
                            || normalized.contains("registrado para o time")
                            || normalized.contains("time te chamar")
                            || normalized.contains("vamos enviar")
                            || normalized.contains("vou enviar")
                            || normalized.contains("enviaremos")
                            || normalized.contains("lembrete")
                            || normalized.contains("depois")
                            || normalized.contains("posterior");
            if (mentionsExternalChannel && promisesLater) continue;
            if (normalized.contains("lembrete")) continue;
            kept.add(current);
        }

        String cleaned = safeTrim(String.join(" ", kept).replaceAll("\\s{2,}", " "));
        if (cleaned.isBlank()) {
            return "Perfeito, seguimos por aqui.";
        }
        return cleaned;
    }

    private String completeIfTruncated(RuntimeContext runtime, JsonNode response, String partialText) {
        String current = safeTrim(partialText);
        if (current.isBlank()) return current;
        if (!wasTruncatedByTokenLimit(response, current)) return current;

        JsonNode latestResponse = response;
        for (int attempt = 0; attempt < 2; attempt++) {
            String previousResponseId = safeTrim(latestResponse.path("id").asText(""));
            if (previousResponseId.isBlank()) break;
            ObjectNode payload = OBJECT_MAPPER.createObjectNode();
            payload.put("model", runtime.model());
            payload.put("instructions", buildSystemPrompt(runtime));
            payload.put("temperature", Math.min(resolveTemperature(runtime.agent()), 0.8d));
            payload.put("max_output_tokens", Math.max(120, resolveMaxOutputTokens(runtime.agent())));
            payload.put("previous_response_id", previousResponseId);
            ArrayNode input = OBJECT_MAPPER.createArrayNode();
            input.add(textInputNode("user", "Continue exatamente de onde parou e finalize a resposta com frase completa, sem repetir o que ja foi dito."));
            payload.set("input", input);

            try {
                latestResponse = callOpenAiResponses(runtime.openAiApiKey(), payload);
            } catch (Exception ex) {
                break;
            }
            String continuation = safeTrim(extractAssistantText(latestResponse));
            if (continuation.isBlank()) break;
            current = stitchContinuation(current, continuation);
            if (endsWithTerminalPunctuation(current)) break;
            if (!wasTruncatedByTokenLimit(latestResponse, continuation)) break;
        }
        return closeIncompleteSentence(current);
    }

    private boolean wasTruncatedByTokenLimit(JsonNode response, String text) {
        String status = normalize(response.path("status").asText(""));
        String reason = normalize(response.path("incomplete_details").path("reason").asText(""));
        if ("incomplete".equals(status) && reason.contains("max_output_tokens")) return true;
        return !endsWithTerminalPunctuation(text) && "incomplete".equals(status);
    }

    private boolean endsWithTerminalPunctuation(String text) {
        String value = safeTrim(text);
        if (value.isBlank()) return false;
        char end = value.charAt(value.length() - 1);
        return end == '.' || end == '!' || end == '?';
    }

    private String stitchContinuation(String base, String continuation) {
        String left = safeTrim(base);
        String right = safeTrim(continuation);
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        char leftLast = left.charAt(left.length() - 1);
        char rightFirst = right.charAt(0);
        boolean joinWithoutSpace = Character.isLetterOrDigit(leftLast) && Character.isLetterOrDigit(rightFirst);
        return joinWithoutSpace ? left + right : (left + " " + right).replaceAll("\\s{2,}", " ").trim();
    }

    private String closeIncompleteSentence(String text) {
        String value = safeTrim(text);
        if (value.isBlank()) return value;
        if (endsWithTerminalPunctuation(value)) return value;
        return value + ".";
    }

    private String enforceResponseStyle(String text, JsonNode agent, String customerMessage) {
        String value = safeTrim(text);
        if (value.isBlank()) return value;
        if (!preferShortResponses(agent)) return value;
        if (customerAskedForDetailedAnswer(customerMessage)) return value;
        return value;
    }

    private boolean preferShortResponses(JsonNode agent) {
        String merged = normalize(
                safeTrim(agent.path("profile").asText("")) + " " +
                safeTrim(agent.path("communicationStyle").asText("")) + " " +
                safeTrim(agent.path("objective").asText(""))
        );
        if (merged.contains("resposta curta")
                || merged.contains("respostas curtas")
                || merged.contains("resposta breve")
                || merged.contains("respostas breves")
                || merged.contains("sem texto longo")
                || merged.contains("sem textos longos")
                || merged.contains("objetivo e curto")
                || merged.contains("curto e objetivo")) {
            return true;
        }

        JsonNode rules = agent.path("rules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                String ruleText = normalize(rule.asText(""));
                if (ruleText.contains("resposta curta")
                        || ruleText.contains("respostas curtas")
                        || ruleText.contains("resposta breve")
                        || ruleText.contains("sem texto longo")
                        || ruleText.contains("sem textos longos")) {
                    return true;
                }
            }
        }

        JsonNode restrictions = agent.path("restrictions");
        if (restrictions.isArray()) {
            for (JsonNode restriction : restrictions) {
                String restrictionText = normalize(restriction.asText(""));
                if (restrictionText.contains("texto longo")
                        || restrictionText.contains("textos longos")
                        || restrictionText.contains("resposta longa")
                        || restrictionText.contains("respostas longas")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean customerAskedForDetailedAnswer(String customerMessage) {
        String text = normalize(customerMessage);
        if (text.isBlank()) return false;
        return text.contains("detalh")
                || text.contains("explica melhor")
                || text.contains("passo a passo")
                || text.contains("mais detalhes")
                || text.contains("completo")
                || text.contains("aprofund");
    }

    private String chooseModel(JsonNode agent, JsonNode provider) {
        String reasoning = safeTrim(agent.path("reasoningModel").asText(""));
        if (!reasoning.isBlank()) return reasoning;
        if (provider != null && !provider.isMissingNode()) {
            String providerReasoning = safeTrim(provider.path("reasoningModel").asText(""));
            if (!providerReasoning.isBlank()) return providerReasoning;
        }
        String modelVersion = safeTrim(agent.path("modelVersion").asText(""));
        if (!modelVersion.isBlank() && modelVersion.toLowerCase(Locale.ROOT).startsWith("gpt-")) return modelVersion;
        return defaultOpenAiModel.isBlank() ? "gpt-5-mini" : defaultOpenAiModel;
    }

    private String resolveOpenAiApiKey(JsonNode provider) {
        if (!openAiApiKey.isBlank()) return openAiApiKey;
        if (provider != null && !provider.isMissingNode()) {
            String key = safeTrim(provider.path("apiKey").asText(""));
            if (!key.isBlank()) return key;
        }
        return "";
    }

    private Set<String> resolveAllowedTools(JsonNode skillsNode) {
        Set<String> allowed = new HashSet<>();
        List<String> skills = asStringList(skillsNode);
        for (String raw : skills) {
            String skill = normalize(raw);
            if ("schedule_google_meeting".equals(skill)) allowed.add("schedule_google_meeting");
            if ("kanban_move_card".equals(skill)) allowed.add("kanban_move_card");
            if ("crm_update_contact_data".equals(skill)) allowed.add("crm_update_contact_data");
            if ("handoff_to_human".equals(skill)) allowed.add("handoff_to_human");
            if (skill.contains("agendar reuniao") || skill.contains("agendamento") || skill.contains("google meet") || skill.contains("calendar")) {
                allowed.add("schedule_google_meeting");
            }
            if (skill.contains("kb_search") || skill.contains("base de conhecimento") || skill.contains("knowledge") || skill.contains("rag")) allowed.add("kb_search");
            if (skill.contains("kanban") || skill.contains("mover card") || skill.contains("pipeline")) allowed.add("kanban_move_card");
            if (skill.contains("crm") || skill.contains("dados do contato") || skill.contains("atualizar dados")) allowed.add("crm_update_contact_data");
            if (skill.contains("handoff") || skill.contains("transfer") || skill.contains("humano") || skill.contains("encaminh")) allowed.add("handoff_to_human");
        }
        return allowed;
    }

    private boolean hasKnowledgeContentAvailable(JsonNode agent, JsonNode knowledgeBase) {
        String selectedKnowledgeBaseId = safeTrim(agent.path("knowledgeBaseId").asText(""));
        boolean hasAnyBase = false;
        for (JsonNode base : knowledgeBase) {
            String baseId = safeTrim(base.path("id").asText(""));
            if (!selectedKnowledgeBaseId.isBlank() && !selectedKnowledgeBaseId.equals(baseId)) continue;
            if (hasVectorStoreConfigured(base)) return true;
            JsonNode files = base.path("files");
            if (!files.isArray()) continue;
            for (JsonNode file : files) {
                hasAnyBase = true;
                String content = safeTrim(file.path("content").asText(""));
                String description = safeTrim(file.path("description").asText(""));
                String title = safeTrim(file.path("title").asText(""));
                if (!content.isBlank() || !description.isBlank() || !title.isBlank()) return true;
            }
        }
        return hasAnyBase;
    }

    private boolean hasVectorStoreConfigured(JsonNode base) {
        if (base == null || base.isMissingNode()) return false;
        if (!safeTrim(base.path("vectorStoreId").asText("")).isBlank()) return true;
        JsonNode vectorStoreIds = base.path("vectorStoreIds");
        if (!vectorStoreIds.isArray()) return false;
        for (JsonNode value : vectorStoreIds) {
            if (!safeTrim(value.asText("")).isBlank()) return true;
        }
        return false;
    }

    private List<String> resolveVectorStoreIds(JsonNode agent, JsonNode knowledgeBase) {
        String selectedKnowledgeBaseId = safeTrim(agent.path("knowledgeBaseId").asText(""));
        List<String> resolved = new ArrayList<>();
        for (JsonNode base : knowledgeBase) {
            String baseId = safeTrim(base.path("id").asText(""));
            if (!selectedKnowledgeBaseId.isBlank() && !selectedKnowledgeBaseId.equals(baseId)) continue;
            addVectorStoreIdsFromBase(resolved, base);
        }
        if (!selectedKnowledgeBaseId.isBlank() && resolved.isEmpty()) {
            for (JsonNode base : knowledgeBase) addVectorStoreIdsFromBase(resolved, base);
        }
        return resolved;
    }

    private void addVectorStoreIdsFromBase(List<String> values, JsonNode base) {
        addUnique(values, safeTrim(base.path("vectorStoreId").asText("")));
        JsonNode vectorStoreIds = base.path("vectorStoreIds");
        if (!vectorStoreIds.isArray()) return;
        for (JsonNode value : vectorStoreIds) {
            addUnique(values, safeTrim(value.asText("")));
        }
    }

    private void addUnique(List<String> values, String value) {
        if (value.isBlank() || values.contains(value)) return;
        values.add(value);
    }

    private void enforceRateLimit(UUID companyId, UUID conversationId) {
        String key = companyId + ":" + conversationId;
        Deque<Instant> bucket = requestBuckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            Instant threshold = Instant.now().minusSeconds(60);
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(threshold)) bucket.pollFirst();
            if (bucket.size() >= RATE_LIMIT_PER_MINUTE) throw new BusinessException("AI_RATE_LIMIT", "Limite de requisicoes por conversa excedido");
            bucket.addLast(Instant.now());
        }
    }

    private boolean shouldFallbackToHandoff(String code) {
        String normalized = normalize(code);
        return normalized.contains("openai") || normalized.contains("kb") || normalized.contains("connection") || normalized.contains("upstream") || normalized.contains("circuit");
    }

    private String summarizeAction(String toolName, ObjectNode output) {
        if ("kb_search".equals(toolName)) return "Consulta de conhecimento executada com " + (output.path("chunks").isArray() ? output.path("chunks").size() : 0) + " resultados";
        if ("handoff_to_human".equals(toolName)) return "Atendimento transferido para humano";
        if ("kanban_move_card".equals(toolName)) return "Card movido para etapa do Kanban";
        if ("crm_update_contact_data".equals(toolName)) return "Campo do CRM atualizado com dado do lead";
        return "Tool executada";
    }

    private ObjectNode actionNode(String toolName, String status, String summary) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("toolName", safeTrim(toolName));
        node.put("status", safeTrim(status));
        node.put("summary", safeTrim(summary));
        return node;
    }

    private ObjectNode logNode(String toolName, String status, long latencyMs, int retries, String errorCode) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("toolName", safeTrim(toolName));
        node.put("status", safeTrim(status));
        node.put("latencyMs", Math.max(0, latencyMs));
        node.put("retries", Math.max(0, retries));
        node.put("errorCode", safeTrim(errorCode));
        return node;
    }

    private <T> T withRetries(String toolName, int maxRetries, ThrowingSupplier<T> action) {
        int attempt = 0;
        while (true) {
            try { return action.get(); }
            catch (BusinessException ex) { if (attempt >= maxRetries || !isRetriable(ex)) throw ex; }
            catch (Exception ex) { if (attempt >= maxRetries) throw new BusinessException("AI_TOOL_RETRY_FAILED", "Falha ao executar " + toolName + " apos retries"); }
            attempt++;
            try { Thread.sleep(200L * (1L << (attempt - 1))); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("AI_TOOL_RETRY_INTERRUPTED", "Retry interrompido"); }
        }
    }

    private boolean isRetriable(BusinessException ex) {
        String code = normalize(ex.code());
        if (code.contains("invalid") || code.contains("required") || code.contains("not_allowed") || code.contains("conflict") || code.contains("rejected")) return false;
        return code.contains("connection") || code.contains("upstream") || code.contains("circuit") || code.contains("auth") || code.contains("openai");
    }

    private String requiredText(JsonNode node, String fieldName, String errorCode) {
        String value = safeTrim(node.path(fieldName).asText(""));
        if (value.isBlank()) throw new BusinessException(errorCode, "Campo obrigatorio ausente: " + fieldName);
        return value;
    }

    private List<String> asStringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> data = new ArrayList<>();
        for (JsonNode item : node) {
            String value = safeTrim(item.asText(""));
            if (!value.isBlank()) data.add(value);
        }
        return data;
    }

    private List<AgentActionHttpResponse> toActionList(JsonNode actions) {
        if (!actions.isArray()) return List.of();
        List<AgentActionHttpResponse> rows = new ArrayList<>();
        for (JsonNode item : actions) {
            rows.add(new AgentActionHttpResponse(safeTrim(item.path("toolName").asText("")), safeTrim(item.path("status").asText("")), safeTrim(item.path("summary").asText(""))));
        }
        return rows;
    }

    private List<ToolExecutionLogHttpResponse> toToolLogList(JsonNode logs) {
        if (!logs.isArray()) return List.of();
        List<ToolExecutionLogHttpResponse> rows = new ArrayList<>();
        for (JsonNode item : logs) {
            rows.add(new ToolExecutionLogHttpResponse(safeTrim(item.path("toolName").asText("")), safeTrim(item.path("status").asText("")), item.path("latencyMs").asLong(0), item.path("retries").asInt(0), safeTrim(item.path("errorCode").asText(""))));
        }
        return rows;
    }

    private int asInt(JsonNode node, int fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        int value = node.asInt(fallback);
        return value <= 0 ? fallback : value;
    }

    private int clampInt(int value, int min, int max, int fallback) {
        if (value < min || value > max) return fallback;
        return value;
    }

    private JsonNode parseJson(String raw, String fallbackJson) {
        try { return OBJECT_MAPPER.readTree(raw == null || raw.isBlank() ? fallbackJson : raw); }
        catch (Exception ignored) {
            try { return OBJECT_MAPPER.readTree(fallbackJson); } catch (Exception impossible) { return OBJECT_MAPPER.createObjectNode(); }
        }
    }

    private String toJson(JsonNode value, String fallback) {
        try { return value == null || value.isNull() ? fallback : OBJECT_MAPPER.writeValueAsString(value); }
        catch (Exception ignored) { return fallback; }
    }

    private ObjectNode asObjectNode(JsonNode node) {
        if (node != null && node.isObject()) return (ObjectNode) node;
        return OBJECT_MAPPER.createObjectNode();
    }

    private String safeTrim(String value) { return value == null ? "" : value.trim(); }
    private String normalize(String value) {
        String trimmed = safeTrim(value).toLowerCase(Locale.ROOT);
        String noDiacritics = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return safeTrim(noDiacritics);
    }

    private List<String> tokenize(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return List.of();
        String[] pieces = NON_WORD.split(normalized);
        List<String> tokens = new ArrayList<>();
        for (String piece : pieces) {
            String token = safeTrim(piece);
            if (token.length() >= 2) tokens.add(token);
        }
        return tokens;
    }

    private double score(String searchableText, String query, List<String> queryTokens) {
        if (searchableText.isBlank()) return 0.0d;
        double match = searchableText.contains(query) ? 0.5d : 0.0d;
        int tokenHits = 0;
        for (String token : queryTokens) if (searchableText.contains(token)) tokenHits++;
        if (!queryTokens.isEmpty()) match += ((double) tokenHits / (double) queryTokens.size()) * 0.5d;
        return match;
    }

    private double round(double value) { return Math.round(value * 1000d) / 1000d; }

    private String maskPii(String value) {
        String masked = value == null ? "" : value;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("***@***");
        Matcher phone = PHONE_PATTERN.matcher(masked);
        StringBuffer sb = new StringBuffer();
        while (phone.find()) {
            String match = phone.group();
            String replacement = match.length() <= 4 ? "****" : match.substring(0, 2) + "****" + match.substring(match.length() - 2);
            phone.appendReplacement(sb, replacement);
        }
        phone.appendTail(sb);
        return sb.toString();
    }

    private boolean isCustomerRequestingHuman(String customerMessage, String reason) {
        String merged = normalize(safeTrim(customerMessage) + " " + safeTrim(reason));
        if (merged.isBlank()) return false;
        return merged.contains("atendente")
                || merged.contains("humano")
                || merged.contains("pessoa")
                || merged.contains("falar com")
                || merged.contains("suporte humano")
                || merged.contains("transferir")
                || merged.contains("quero atendimento");
    }

    private String extractOpenAiErrorDetail(String body) {
        JsonNode root = parseJson(body, "{}");
        String message = safeTrim(root.path("error").path("message").asText(""));
        if (!message.isBlank()) return message;
        String code = safeTrim(root.path("error").path("code").asText(""));
        if (!code.isBlank()) return code;
        String type = safeTrim(root.path("error").path("type").asText(""));
        if (!type.isBlank()) return type;
        String raw = safeTrim(body);
        if (raw.isBlank()) return "sem detalhes";
        return raw.length() > 300 ? raw.substring(0, 300) : raw;
    }

    private String shortenForLog(String value, int max) {
        String normalized = safeTrim(value);
        if (normalized.length() <= max) return normalized;
        return normalized.substring(0, Math.max(0, max)) + "...";
    }

    private record RuntimeContext(UUID companyId, JsonNode agent, JsonNode provider, JsonNode knowledgeBase, JsonNode crmState, String openAiApiKey, String model, Set<String> allowedTools, List<String> vectorStoreIds) {}
    public record AgentExecutionConfig(String agentId, int delayMessageSeconds, int delayTypingSeconds) {}
    private record FunctionCallRequest(String callId, String name, String argumentsJson) {}
    private record ToolExecutionResult(ObjectNode output, ObjectNode actionNode, ObjectNode logNode, boolean handoffTriggered) {
        ObjectNode functionOutputNode(String callId) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            result.put("type", "function_call_output");
            result.put("call_id", callId);
            result.put("output", output.toString());
            return result;
        }
    }
    private record KbCandidate(String source, double score, String text) {}
    @FunctionalInterface interface HttpRequestExecutor { HttpResponse<String> send(HttpRequest request) throws Exception; }
    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}

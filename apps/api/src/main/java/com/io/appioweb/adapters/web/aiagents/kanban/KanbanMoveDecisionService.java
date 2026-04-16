package com.io.appioweb.adapters.web.aiagents.kanban;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanMoveAttemptRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanMoveDecision;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentRunLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentStageRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentCompanyStateEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentKanbanMoveAttemptEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentKanbanStateEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentRunLogEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentStageRuleEntity;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoMessageEntity;
import com.io.appioweb.adapters.persistence.crm.CrmCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.crm.JpaCrmCompanyStateEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.shared.errors.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

@Service
public class KanbanMoveDecisionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(KanbanMoveDecisionService.class);
    private static final String SYSTEM_PROMPT =
            "Voc\u00ea \u00e9 um classificador determin\u00edstico. Decide se devemos mover o card do CRM para outra etapa. " +
                    "Responda APENAS com JSON v\u00e1lido e sem texto extra.";
    private static final Set<String> SIGNAL_TOKENS = Set.of(
            "agend", "reuniao", "reuni\u00e3o", "proposta", "contrato", "pag", "pagar", "pagamento",
            "confirm", "aceit", "fech", "quero", "interess", "cancel", "encerrar", "nao tenho interesse"
    );
    private static final Set<String> SPECIAL_STAGE_TOKENS = Set.of(
            "perdido", "lost", "encerrado", "encerrada", "final", "won", "ganho", "ganha", "fechado", "fechada"
    );

    private final CrmCompanyStateRepositoryJpa crmStateRepository;
    private final AtendimentoMessageRepositoryJpa messageRepository;
    private final AiAgentStageRuleRepositoryJpa stageRuleRepository;
    private final AiAgentKanbanMoveAttemptRepositoryJpa attemptRepository;
    private final AiAgentKanbanStateRepositoryJpa stateRepository;
    private final AiAgentRunLogRepositoryJpa runLogRepository;
    private final AiAgentCompanyStateRepositoryJpa aiAgentStateRepository;
    private final SensitiveDataCrypto crypto;
    private final AiKanbanMoveLlmClient llmClient;
    private final KanbanMoveFeatureProperties featureProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final String defaultOpenAiModel;
    private final String openAiApiKey;

    private final LongAdder llmCallsCount = new LongAdder();
    private final LongAdder llmTokensEstimated = new LongAdder();
    private final LongAdder movesCount = new LongAdder();
    private final LongAdder noopCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    public KanbanMoveDecisionService(
            CrmCompanyStateRepositoryJpa crmStateRepository,
            AtendimentoMessageRepositoryJpa messageRepository,
            AiAgentStageRuleRepositoryJpa stageRuleRepository,
            AiAgentKanbanMoveAttemptRepositoryJpa attemptRepository,
            AiAgentKanbanStateRepositoryJpa stateRepository,
            AiAgentRunLogRepositoryJpa runLogRepository,
            AiAgentCompanyStateRepositoryJpa aiAgentStateRepository,
            SensitiveDataCrypto crypto,
            AiKanbanMoveLlmClient llmClient,
            KanbanMoveFeatureProperties featureProperties,
            PlatformTransactionManager txManager,
            ApplicationEventPublisher eventPublisher,
            @Value("${OPENAI_DEFAULT_MODEL:gpt-5-mini}") String defaultOpenAiModel,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey
    ) {
        this.crmStateRepository = crmStateRepository;
        this.messageRepository = messageRepository;
        this.stageRuleRepository = stageRuleRepository;
        this.attemptRepository = attemptRepository;
        this.stateRepository = stateRepository;
        this.runLogRepository = runLogRepository;
        this.aiAgentStateRepository = aiAgentStateRepository;
        this.crypto = crypto;
        this.llmClient = llmClient;
        this.featureProperties = featureProperties;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.eventPublisher = eventPublisher;
        this.defaultOpenAiModel = safeTrim(defaultOpenAiModel);
        this.openAiApiKey = safeTrim(openAiApiKey);
    }

    public KanbanMoveEvaluationResult evaluateAndMaybeMoveCard(
            UUID companyId,
            UUID conversationId,
            String cardId,
            String agentId
    ) {
        return evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId, List.of(), false);
    }

    public KanbanMoveEvaluationResult evaluateAndMaybeMoveCard(
            UUID companyId,
            UUID conversationId,
            String cardId,
            String agentId,
            List<String> explicitEvents,
            boolean forceEvaluation
    ) {
        LoadedContext context;
        try {
            context = loadContext(companyId, conversationId, cardId, agentId, explicitEvents);
        } catch (Exception ex) {
            String errorCode = ex instanceof BusinessException be ? safeTrim(be.code()) : "KANBAN_CONTEXT_LOAD_ERROR";
            String reason = shorten(safeTrim(ex.getMessage()), 180);
            errorCount.increment();
            log.error("kanban_move error correlationId={} companyId={} cardId={} code={} reason={}",
                    conversationId, companyId, safeTrim(cardId), errorCode, reason, ex);
            return new KanbanMoveEvaluationResult("ERROR", false, null, null, reason, errorCode, "", null);
        }

        GateDecision gate = applyGating(context, forceEvaluation);
        if (gate.skip()) {
            noopCount.increment();
            persistState(context, null);
            persistAttempt(context, AiAgentKanbanMoveDecision.NO_MOVE, null, gate.reason(), null, "", "", null);
            log.info("kanban_move noop correlationId={} companyId={} cardId={} reason={} evaluationKey={}",
                    context.conversationId(), context.companyId(), context.cardId(), gate.reason(), context.evaluationKey());
            return new KanbanMoveEvaluationResult(
                    "NO_MOVE",
                    false,
                    null,
                    null,
                    gate.reason(),
                    "",
                    context.evaluationKey(),
                    null
            );
        }

        List<CandidateStage> candidates = buildCandidates(context);
        if (candidates.isEmpty()) {
            noopCount.increment();
            persistState(context, null);
            persistAttempt(context, AiAgentKanbanMoveDecision.NO_MOVE, null, "no_candidate_stage", null, "", "", null);
            return new KanbanMoveEvaluationResult("NO_MOVE", false, null, null, "no_candidate_stage", "", context.evaluationKey(), null);
        }

        LlmDecision llmDecision;
        String llmRequestId = "";
        try {
            llmDecision = callLlmAndParseJsonStrict(context, candidates);
            llmRequestId = llmDecision.llmRequestId();
            llmCallsCount.increment();
            llmTokensEstimated.add(Math.max(0, llmDecision.estimatedTokens()));
        } catch (Exception ex) {
            String errorCode = ex instanceof BusinessException be ? safeTrim(be.code()) : "KANBAN_LLM_ERROR";
            String reason = shorten(safeTrim(ex.getMessage()), 180);
            errorCount.increment();
            persistState(context, null);
            persistAttempt(context, AiAgentKanbanMoveDecision.ERROR, null, "llm_error", null, errorCode, reason, null);
            return new KanbanMoveEvaluationResult("ERROR", false, null, null, "llm_error", errorCode, context.evaluationKey(), null);
        }

        if (!llmDecision.move()) {
            noopCount.increment();
            persistState(context, null);
            persistAttempt(context, AiAgentKanbanMoveDecision.NO_MOVE, null, llmDecision.reason(), llmDecision.confidence(), "", "", llmRequestId);
            return new KanbanMoveEvaluationResult(
                    "NO_MOVE",
                    false,
                    null,
                    llmDecision.confidence(),
                    llmDecision.reason(),
                    "",
                    context.evaluationKey(),
                    llmRequestId
            );
        }

        ValidationResult validation = validateDecision(context, candidates, llmDecision);
        if (!validation.valid()) {
            noopCount.increment();
            persistState(context, null);
            persistAttempt(
                    context,
                    AiAgentKanbanMoveDecision.NO_MOVE,
                    validation.targetStageId(),
                    validation.reason(),
                    llmDecision.confidence(),
                    validation.errorCode(),
                    "",
                    llmRequestId
            );
            return new KanbanMoveEvaluationResult(
                    "NO_MOVE",
                    false,
                    validation.targetStageId(),
                    llmDecision.confidence(),
                    validation.reason(),
                    validation.errorCode(),
                    context.evaluationKey(),
                    llmRequestId
            );
        }

        ApplyMoveResult apply = applyMoveTransactionally(context, validation.targetStageId());
        if (!apply.moved()) {
            noopCount.increment();
            persistState(context, null);
            persistAttempt(
                    context,
                    AiAgentKanbanMoveDecision.NO_MOVE,
                    validation.targetStageId(),
                    apply.reason(),
                    llmDecision.confidence(),
                    apply.errorCode(),
                    "",
                    llmRequestId
            );
            return new KanbanMoveEvaluationResult(
                    "NO_MOVE",
                    false,
                    validation.targetStageId(),
                    llmDecision.confidence(),
                    apply.reason(),
                    apply.errorCode(),
                    context.evaluationKey(),
                    llmRequestId
            );
        }

        movesCount.increment();
        persistState(context, validation.targetStageId());
        persistAttempt(
                context,
                AiAgentKanbanMoveDecision.MOVE,
                validation.targetStageId(),
                llmDecision.reason(),
                llmDecision.confidence(),
                "",
                "",
                llmRequestId
        );
        log.debug("kanban_move metrics llm_calls_count={} llm_tokens_estimated={} moves_count={} noop_count={} error_count={}",
                llmCallsCount.sum(), llmTokensEstimated.sum(), movesCount.sum(), noopCount.sum(), errorCount.sum());
        return new KanbanMoveEvaluationResult(
                "MOVE",
                true,
                validation.targetStageId(),
                llmDecision.confidence(),
                llmDecision.reason(),
                "",
                context.evaluationKey(),
                llmRequestId
        );
    }

    private LoadedContext loadContext(
            UUID companyId,
            UUID conversationId,
            String cardIdRaw,
            String agentIdRaw,
            List<String> explicitEvents
    ) {
        String agentId = safeTrim(agentIdRaw);
        String cardId = safeTrim(cardIdRaw).isBlank() ? conversationId.toString() : safeTrim(cardIdRaw);
        if (agentId.isBlank()) throw new BusinessException("KANBAN_AGENT_REQUIRED", "agentId obrigatorio para avaliar mudanca de etapa");

        JpaCrmCompanyStateEntity crmState = crmStateRepository.findById(companyId).orElseGet(() -> defaultCrmEntity(companyId));
        List<StageRef> stages = parseStages(crmState.getStagesJson());
        Map<String, String> leadStageMap = parseLeadStageMap(crmState.getLeadStageMapJson());
        StageRef currentStage = resolveCurrentStage(stages, leadStageMap.get(cardId));

        JpaAiAgentKanbanStateEntity state = stateRepository
                .findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId)
                .orElse(null);

        List<JpaAtendimentoMessageEntity> latestMessagesDesc = messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(
                conversationId,
                companyId,
                PageRequest.of(0, featureProperties.maxMessages())
        );
        List<CompactMessage> compactMessages = toCompactMessages(latestMessagesDesc);
        CompactMessage latestMessage = compactMessages.isEmpty() ? null : compactMessages.get(compactMessages.size() - 1);

        AgentRuntime runtime = loadAgentRuntime(companyId, agentId);
        List<StageRuleRef> rules = loadStageRules(companyId, agentId, runtime, stages);
        String stageSetVersion = buildStageSetVersion(stages, rules);
        String evaluationKey = buildEvaluationKey(conversationId, cardId, latestMessage == null ? null : latestMessage.id(), stageSetVersion);

        List<String> events = new ArrayList<>();
        if (explicitEvents != null) {
            for (String event : explicitEvents) {
                String normalized = safeTrim(event);
                if (!normalized.isBlank() && !events.contains(normalized)) events.add(normalized);
            }
        }
        for (String inferred : inferStructuredEvents(companyId, conversationId)) {
            if (!events.contains(inferred)) events.add(inferred);
        }

        return new LoadedContext(
                companyId,
                conversationId,
                cardId,
                agentId,
                stages,
                currentStage,
                rules,
                compactMessages,
                latestMessage,
                events,
                evaluationKey,
                stageSetVersion,
                runtime,
                state
        );
    }

    private GateDecision applyGating(LoadedContext context, boolean forceEvaluation) {
        if (!featureProperties.isEnabledFor(context.companyId())) {
            return GateDecision.skip("feature_disabled");
        }
        if (context.stages().isEmpty()) {
            return GateDecision.skip("stages_not_configured");
        }
        if (context.latestMessage() == null) {
            return GateDecision.skip("no_new_messages");
        }
        long enabledRules = context.rules().stream().filter(StageRuleRef::enabled).count();
        if (enabledRules == 0) {
            return GateDecision.skip("no_enabled_stage_rules");
        }
        if (featureProperties.blockWhenFinalStage() && context.currentStage() != null && context.currentStage().isFinalStage()) {
            return GateDecision.skip("current_stage_closed");
        }

        JpaAiAgentKanbanStateEntity state = context.state();
        if (!forceEvaluation && state != null) {
            if (Objects.equals(state.getLastEvaluatedMessageId(), context.latestMessage().id())) {
                return GateDecision.skip("no_new_messages");
            }
            if (state.getLastEvaluatedMessageAt() != null
                    && !context.latestMessage().ts().isAfter(state.getLastEvaluatedMessageAt())) {
                return GateDecision.skip("no_new_messages");
            }
            if (context.events().isEmpty()
                    && state.getLastDecisionAt() != null
                    && Duration.between(state.getLastDecisionAt(), Instant.now()).toSeconds() < featureProperties.cooldownSeconds()) {
                return GateDecision.skip("cooldown_active");
            }
        }

        if (!forceEvaluation && context.events().isEmpty() && context.latestMessage().fromMe()) {
            return GateDecision.skip("latest_message_from_agent");
        }
        if (!forceEvaluation && context.events().isEmpty() && isLowSignal(context.latestMessage().text())) {
            return GateDecision.skip("low_signal_message");
        }
        return GateDecision.continueEvaluation();
    }

    private List<CandidateStage> buildCandidates(LoadedContext context) {
        if (context.currentStage() == null) return List.of();

        Set<String> baseCandidateIds = new LinkedHashSet<>();
        baseCandidateIds.add(context.currentStage().id());
        List<StageRef> sorted = context.stages().stream().sorted(Comparator.comparingInt(StageRef::order)).toList();

        int currentIndex = -1;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).id().equals(context.currentStage().id())) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex >= 0) {
            for (int i = currentIndex + 1; i < sorted.size() && i <= currentIndex + 2; i++) {
                baseCandidateIds.add(sorted.get(i).id());
            }
        }
        for (StageRef stage : sorted) {
            if (stage.isFinalStage() || isSpecialStageTitle(stage.name())) {
                baseCandidateIds.add(stage.id());
            }
        }

        Map<String, StageRef> stageById = new HashMap<>();
        for (StageRef stage : sorted) stageById.put(stage.id(), stage);

        List<CandidateStage> candidates = new ArrayList<>();
        for (StageRuleRef rule : context.rules()) {
            if (!rule.enabled()) continue;
            if (safeTrim(rule.prompt()).isBlank()) continue;
            if (!baseCandidateIds.contains(rule.stageId())) continue;
            StageRef stage = stageById.get(rule.stageId());
            if (stage == null) continue;
            boolean onlyForward = rule.onlyForward();
            if (onlyForward && stage.order() < context.currentStage().order()) continue;
            if (!rule.allowedFromStages().isEmpty() && !rule.allowedFromStages().contains(context.currentStage().id())) continue;
            candidates.add(new CandidateStage(stage.id(), stage.name(), stage.order(), rule.prompt(), rule.priority(), onlyForward, rule.allowedFromStages()));
        }
        candidates.sort((left, right) -> {
            int byPriority = Integer.compare(right.priority(), left.priority());
            if (byPriority != 0) return byPriority;
            return Integer.compare(left.order(), right.order());
        });
        return candidates;
    }

    private LlmDecision callLlmAndParseJsonStrict(LoadedContext context, List<CandidateStage> candidates) {
        ObjectNode userPayload = OBJECT_MAPPER.createObjectNode();
        ObjectNode currentStage = OBJECT_MAPPER.createObjectNode();
        currentStage.put("id", context.currentStage().id());
        currentStage.put("name", context.currentStage().name());
        userPayload.set("currentStage", currentStage);

        ArrayNode candidateArray = OBJECT_MAPPER.createArrayNode();
        for (CandidateStage candidate : candidates) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("id", candidate.id());
            node.put("name", candidate.name());
            node.put("rulePrompt", safeTrim(candidate.prompt()));
            candidateArray.add(node);
        }
        userPayload.set("candidates", candidateArray);

        ArrayNode messageArray = OBJECT_MAPPER.createArrayNode();
        for (CompactMessage message : context.messages()) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("role", message.fromMe() ? "agent" : "user");
            node.put("text", safeTrim(message.text()));
            node.put("ts", message.ts() == null ? "" : message.ts().toString());
            messageArray.add(node);
        }
        userPayload.set("lastMessages", messageArray);

        if (!context.events().isEmpty()) {
            ArrayNode events = OBJECT_MAPPER.createArrayNode();
            for (String event : context.events()) events.add(event);
            userPayload.set("events", events);
        }

        ObjectNode constraints = OBJECT_MAPPER.createObjectNode();
        boolean onlyForward = candidates.stream().allMatch(CandidateStage::onlyForward);
        constraints.put("onlyForward", onlyForward);
        userPayload.set("constraints", constraints);

        String userPrompt;
        try {
            userPrompt = OBJECT_MAPPER.writeValueAsString(userPayload);
        } catch (Exception ex) {
            throw new BusinessException("KANBAN_LLM_USER_PROMPT_ERROR", "Falha ao montar prompt compacto do classificador Kanban");
        }

        AiKanbanMoveLlmClient.LlmResponse response = llmClient.classify(
                new AiKanbanMoveLlmClient.LlmRequest(
                        context.runtime().apiKey(),
                        context.runtime().model(),
                        SYSTEM_PROMPT,
                        userPrompt,
                        featureProperties.maxOutputTokens()
                )
        );
        DecisionPayload payload = parseStrictDecision(response.outputText());
        int estimatedTokens = response.inputTokens() + response.outputTokens();
        if (estimatedTokens <= 0) {
            estimatedTokens = Math.max(1, (SYSTEM_PROMPT.length() + userPrompt.length() + response.outputText().length()) / 4);
        }
        return new LlmDecision(
                payload.move(),
                payload.targetStageId(),
                payload.confidence(),
                payload.reason(),
                response.requestId(),
                estimatedTokens
        );
    }

    private DecisionPayload parseStrictDecision(String rawJson) {
        String raw = safeTrim(rawJson);
        if (raw.isBlank()) {
            throw new BusinessException("KANBAN_LLM_EMPTY_JSON", "Classificador Kanban retornou JSON vazio");
        }

        JsonNode root;
        try {
            var parser = OBJECT_MAPPER.createParser(raw);
            root = OBJECT_MAPPER.readTree(parser);
            if (root == null || !root.isObject()) {
                throw new BusinessException("KANBAN_LLM_JSON_INVALID", "Resposta do classificador Kanban precisa ser um objeto JSON");
            }
            if (parser.nextToken() != null) {
                throw new BusinessException("KANBAN_LLM_JSON_TRAILING_TEXT", "Resposta do classificador Kanban contem texto fora do JSON");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("KANBAN_LLM_JSON_PARSE_ERROR", "Nao foi possivel interpretar JSON do classificador Kanban");
        }

        JsonNode moveNode = root.get("move");
        if (moveNode == null || !moveNode.isBoolean()) {
            throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo move deve ser boolean");
        }
        boolean move = moveNode.asBoolean(false);

        JsonNode targetNode = root.get("targetStageId");
        String targetStageId;
        if (move) {
            if (targetNode == null || targetNode.isNull() || !targetNode.isTextual()) {
                throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo targetStageId deve ser string quando move=true");
            }
            targetStageId = safeTrim(targetNode.asText(""));
            if (targetStageId.isBlank()) {
                throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo targetStageId vazio quando move=true");
            }
        } else {
            if (targetNode != null && !targetNode.isNull()) {
                throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo targetStageId deve ser null quando move=false");
            }
            targetStageId = null;
        }

        JsonNode confidenceNode = root.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo confidence deve ser numerico");
        }
        double confidence = confidenceNode.asDouble();
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo confidence fora do range 0..1");
        }

        JsonNode reasonNode = root.get("reason");
        if (reasonNode == null || !reasonNode.isTextual()) {
            throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo reason deve ser string");
        }
        String reason = shorten(safeTrim(reasonNode.asText("")), 140);
        if (reason.isBlank()) reason = move ? "move_without_reason" : "no_move";

        JsonNode evidenceNode = root.get("evidence");
        if (evidenceNode != null && !evidenceNode.isNull()) {
            if (!evidenceNode.isArray()) {
                throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo evidence deve ser array quando informado");
            }
            if (evidenceNode.size() > 3) {
                throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Campo evidence aceita no maximo 3 itens");
            }
            for (JsonNode item : evidenceNode) {
                if (!item.isTextual()) {
                    throw new BusinessException("KANBAN_LLM_JSON_SCHEMA_ERROR", "Itens de evidence devem ser string");
                }
            }
        }
        return new DecisionPayload(move, targetStageId, confidence, reason);
    }

    private ValidationResult validateDecision(LoadedContext context, List<CandidateStage> candidates, LlmDecision decision) {
        String targetStageId = safeTrim(decision.targetStageId());
        if (targetStageId.isBlank()) {
            return ValidationResult.invalid("target_stage_missing", "KANBAN_TARGET_MISSING", null);
        }
        if (context.currentStage() != null && targetStageId.equals(context.currentStage().id())) {
            return ValidationResult.invalid("target_equals_current", "KANBAN_TARGET_EQUALS_CURRENT", targetStageId);
        }
        Map<String, CandidateStage> candidateById = new HashMap<>();
        for (CandidateStage candidate : candidates) candidateById.put(candidate.id(), candidate);
        CandidateStage candidate = candidateById.get(targetStageId);
        if (candidate == null) {
            return ValidationResult.invalid("target_not_allowed", "KANBAN_TARGET_NOT_ALLOWED", targetStageId);
        }

        StageRef targetStage = null;
        for (StageRef stage : context.stages()) {
            if (stage.id().equals(targetStageId)) {
                targetStage = stage;
                break;
            }
        }
        if (targetStage == null) {
            return ValidationResult.invalid("target_not_found_pipeline", "KANBAN_TARGET_NOT_FOUND", targetStageId);
        }
        if (featureProperties.blockWhenFinalStage() && context.currentStage() != null && context.currentStage().isFinalStage()) {
            return ValidationResult.invalid("current_stage_closed", "KANBAN_CURRENT_STAGE_CLOSED", targetStageId);
        }
        if (candidate.onlyForward() && context.currentStage() != null && targetStage.order() < context.currentStage().order()) {
            return ValidationResult.invalid("only_forward_violation", "KANBAN_ONLY_FORWARD_VIOLATION", targetStageId);
        }
        if (!candidate.allowedFromStages().isEmpty()
                && context.currentStage() != null
                && !candidate.allowedFromStages().contains(context.currentStage().id())) {
            return ValidationResult.invalid("allowed_from_violation", "KANBAN_ALLOWED_FROM_VIOLATION", targetStageId);
        }
        return ValidationResult.valid(targetStageId);
    }

    private ApplyMoveResult applyMoveTransactionally(LoadedContext context, String targetStageId) {
        return transactionTemplate.execute(status -> {
            if (attemptRepository.existsByCompanyIdAndAgentIdAndConversationIdAndCardIdAndEvaluationKeyAndDecision(
                    context.companyId(),
                    context.agentId(),
                    context.conversationId(),
                    context.cardId(),
                    context.evaluationKey(),
                    AiAgentKanbanMoveDecision.MOVE
            )) {
                return ApplyMoveResult.fail("idempotent_same_evaluation", "KANBAN_IDEMPOTENT_MOVE");
            }

            JpaCrmCompanyStateEntity crmState = crmStateRepository.findByCompanyIdForUpdate(context.companyId()).orElseGet(() -> {
                JpaCrmCompanyStateEntity created = defaultCrmEntity(context.companyId());
                return crmStateRepository.saveAndFlush(created);
            });

            Map<String, String> leadStageMap = parseLeadStageMap(crmState.getLeadStageMapJson());
            StageRef liveCurrent = resolveCurrentStage(context.stages(), leadStageMap.get(context.cardId()));
            if (liveCurrent == null) {
                return ApplyMoveResult.fail("current_stage_not_found", "KANBAN_CURRENT_STAGE_NOT_FOUND");
            }
            if (!Objects.equals(liveCurrent.id(), context.currentStage() == null ? null : context.currentStage().id())) {
                return ApplyMoveResult.fail("current_stage_changed", "KANBAN_CONCURRENT_STAGE_CHANGED");
            }
            if (targetStageId.equals(liveCurrent.id())) {
                return ApplyMoveResult.fail("target_equals_current", "KANBAN_TARGET_EQUALS_CURRENT");
            }

            leadStageMap.put(context.cardId(), targetStageId);
            crmState.setLeadStageMapJson(toJson(leadStageMap, "{}"));
            crmState.setUpdatedAt(Instant.now());
            crmStateRepository.saveAndFlush(crmState);

            eventPublisher.publishEvent(new CardStageMovedEvent(
                    context.companyId(),
                    context.agentId(),
                    context.conversationId(),
                    context.cardId(),
                    liveCurrent.id(),
                    targetStageId,
                    context.evaluationKey(),
                    Instant.now()
            ));
            return ApplyMoveResult.success();
        });
    }

    private void persistAttempt(
            LoadedContext context,
            AiAgentKanbanMoveDecision decision,
            String toStageId,
            String reason,
            Double confidence,
            String errorCode,
            String errorMessageShort,
            String llmRequestId
    ) {
        JpaAiAgentKanbanMoveAttemptEntity row = new JpaAiAgentKanbanMoveAttemptEntity();
        row.setId(UUID.randomUUID());
        row.setCompanyId(context.companyId());
        row.setAgentId(context.agentId());
        row.setConversationId(context.conversationId());
        row.setCardId(context.cardId());
        row.setFromStageId(context.currentStage() == null ? null : context.currentStage().id());
        row.setToStageId(trimToNull(toStageId));
        row.setDecision(decision);
        row.setConfidence(confidence == null ? null : BigDecimal.valueOf(clampConfidence(confidence)));
        row.setReason(trimToNull(shorten(reason, 180)));
        row.setEvaluationKey(shorten(safeTrim(context.evaluationKey()), 200));
        row.setLastMessageId(context.latestMessage() == null ? null : context.latestMessage().id());
        row.setErrorCode(trimToNull(shorten(errorCode, 80)));
        row.setErrorMessageShort(trimToNull(shorten(errorMessageShort, 180)));
        row.setLlmRequestId(trimToNull(shorten(llmRequestId, 120)));
        row.setCreatedAt(Instant.now());
        try {
            attemptRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException duplicated) {
            // idempotency safeguard for repeated evaluationKey
        }
    }

    private void persistState(LoadedContext context, String movedStageId) {
        Instant now = Instant.now();
        JpaAiAgentKanbanStateEntity state = context.state() == null ? new JpaAiAgentKanbanStateEntity() : context.state();
        if (context.state() == null) {
            state.setCompanyId(context.companyId());
            state.setAgentId(context.agentId());
            state.setConversationId(context.conversationId());
            state.setCardId(context.cardId());
            state.setCreatedAt(now);
        }
        if (context.latestMessage() != null) {
            state.setLastEvaluatedMessageId(context.latestMessage().id());
            state.setLastEvaluatedMessageAt(context.latestMessage().ts());
        }
        if (trimToNull(movedStageId) != null) {
            state.setLastMovedStageId(movedStageId.trim());
        }
        state.setLastDecisionAt(now);
        state.setUpdatedAt(now);
        stateRepository.saveAndFlush(state);
    }

    private AgentRuntime loadAgentRuntime(UUID companyId, String agentId) {
        JpaAiAgentCompanyStateEntity state = aiAgentStateRepository.findById(companyId).orElse(null);
        if (state == null) {
            return new AgentRuntime(openAiApiKey, fallbackModel(), null);
        }
        JsonNode providers = parseJson(crypto.decrypt(state.getProvidersJson()), "[]");
        JsonNode agents = parseJson(state.getAgentsJson(), "[]");
        JsonNode agent = null;
        if (agents.isArray()) {
            for (JsonNode item : agents) {
                if (safeTrim(item.path("id").asText("")).equals(safeTrim(agentId))) {
                    agent = item;
                    break;
                }
            }
        }

        JsonNode provider = null;
        String providerId = agent == null ? "" : safeTrim(agent.path("providerId").asText(""));
        if (providers.isArray() && !providerId.isBlank()) {
            for (JsonNode item : providers) {
                if (providerId.equals(safeTrim(item.path("id").asText("")))) {
                    provider = item;
                    break;
                }
            }
        }

        String model = fallbackModel();
        String agentReasoningModel = agent == null ? "" : safeTrim(agent.path("reasoningModel").asText(""));
        if (!agentReasoningModel.isBlank()) {
            model = agentReasoningModel;
        } else if (provider != null && !safeTrim(provider.path("reasoningModel").asText("")).isBlank()) {
            model = safeTrim(provider.path("reasoningModel").asText(""));
        } else if (agent != null && safeTrim(agent.path("modelVersion").asText("")).toLowerCase(Locale.ROOT).startsWith("gpt-")) {
            model = safeTrim(agent.path("modelVersion").asText(""));
        }

        String apiKey = openAiApiKey;
        if (apiKey.isBlank() && provider != null) {
            apiKey = safeTrim(provider.path("apiKey").asText(""));
        }
        return new AgentRuntime(apiKey, model, agent);
    }

    private List<StageRuleRef> loadStageRules(UUID companyId, String agentId, AgentRuntime runtime, List<StageRef> stages) {
        List<JpaAiAgentStageRuleEntity> rows = stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId);
        if (!rows.isEmpty()) {
            List<StageRuleRef> rules = new ArrayList<>();
            for (JpaAiAgentStageRuleEntity row : rows) {
                Set<String> allowedFrom = parseStringSet(row.getAllowedFromStagesJson());
                boolean onlyForward = row.getOnlyForwardOverride() == null
                        ? featureProperties.defaultOnlyForward()
                        : row.getOnlyForwardOverride().booleanValue();
                rules.add(new StageRuleRef(
                        safeTrim(row.getStageId()),
                        row.isEnabled(),
                        safeTrim(row.getPrompt()),
                        row.getPriority() == null ? 0 : row.getPriority(),
                        onlyForward,
                        allowedFrom,
                        row.getUpdatedAt()
                ));
            }
            return rules;
        }

        JsonNode agent = runtime.agent();
        if (agent == null || agent.isMissingNode()) return List.of();
        JsonNode prompts = agent.path("capabilityConfigs").path("kanbanMoveCardStagePrompts");
        if (!prompts.isObject()) return List.of();
        Set<String> knownStageIds = new HashSet<>();
        for (StageRef stage : stages) knownStageIds.add(stage.id());
        List<StageRuleRef> fallbackRules = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : prompts.properties()) {
            String stageId = safeTrim(entry.getKey());
            String prompt = safeTrim(entry.getValue().asText(""));
            if (stageId.isBlank() || prompt.isBlank()) continue;
            if (!knownStageIds.contains(stageId)) continue;
            fallbackRules.add(new StageRuleRef(
                    stageId,
                    true,
                    prompt,
                    0,
                    featureProperties.defaultOnlyForward(),
                    Set.of(),
                    Instant.EPOCH
            ));
        }
        return fallbackRules;
    }

    private List<String> inferStructuredEvents(UUID companyId, UUID conversationId) {
        List<JpaAiAgentRunLogEntity> logs = runLogRepository.findTop10ByCompanyIdAndConversationIdOrderByCreatedAtDesc(companyId, conversationId);
        if (logs.isEmpty()) return List.of();
        List<String> events = new ArrayList<>();
        for (JpaAiAgentRunLogEntity row : logs) {
            JsonNode response = parseJson(row.getResponsePayloadJson(), "{}");
            String mode = safeTrim(response.path("mode").asText(""));
            if ("calendar_confirm".equalsIgnoreCase(mode) && !events.contains("meetingScheduled")) {
                events.add("meetingScheduled");
            }
            String eventId = safeTrim(response.path("eventId").asText(""));
            if (!eventId.isBlank() && !events.contains("meetingScheduled")) {
                events.add("meetingScheduled");
            }
        }
        return events;
    }

    private List<CompactMessage> toCompactMessages(List<JpaAtendimentoMessageEntity> messagesDesc) {
        if (messagesDesc == null || messagesDesc.isEmpty()) return List.of();
        List<CompactMessage> compact = new ArrayList<>();
        List<JpaAtendimentoMessageEntity> asc = new ArrayList<>(messagesDesc);
        asc.sort(Comparator.comparing(JpaAtendimentoMessageEntity::getCreatedAt));
        for (JpaAtendimentoMessageEntity row : asc) {
            String text = compactMessageText(row.getMessageText(), row.getMessageType());
            if (safeTrim(text).isBlank()) continue;
            compact.add(new CompactMessage(
                    row.getId(),
                    row.isFromMe(),
                    truncate(text, featureProperties.maxMessageChars()),
                    row.getCreatedAt()
            ));
        }
        return compact;
    }

    private String compactMessageText(String text, String messageType) {
        String normalized = safeTrim(text);
        if (!normalized.isBlank()) return normalized;
        String type = safeTrim(messageType).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "audio" -> "[audio]";
            case "image" -> "[imagem]";
            case "video" -> "[video]";
            case "document" -> "[documento]";
            case "location" -> "[localizacao]";
            default -> "";
        };
    }

    private List<StageRef> parseStages(String raw) {
        JsonNode node = parseJson(raw, "[]");
        if (!node.isArray()) return List.of();
        List<StageRef> stages = new ArrayList<>();
        for (JsonNode item : node) {
            String id = safeTrim(item.path("id").asText(""));
            if (id.isBlank()) continue;
            String name = safeTrim(item.path("title").asText(""));
            if (name.isBlank()) name = safeTrim(item.path("name").asText(""));
            String kind = safeTrim(item.path("kind").asText("intermediate")).toLowerCase(Locale.ROOT);
            int order = item.path("order").isNumber() ? item.path("order").asInt() : item.path("orderIndex").asInt(stages.size());
            boolean finalStage = "final".equals(kind) || isSpecialStageTitle(name);
            stages.add(new StageRef(id, name.isBlank() ? id : name, order, finalStage));
        }
        stages.sort(Comparator.comparingInt(StageRef::order));
        return stages;
    }

    private Map<String, String> parseLeadStageMap(String raw) {
        JsonNode node = parseJson(raw, "{}");
        Map<String, String> values = new HashMap<>();
        if (!node.isObject()) return values;
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = safeTrim(entry.getKey());
            String value = safeTrim(entry.getValue().asText(""));
            if (key.isBlank() || value.isBlank()) continue;
            values.put(key, value);
        }
        return values;
    }

    private StageRef resolveCurrentStage(List<StageRef> stages, String currentStageId) {
        if (stages.isEmpty()) return null;
        String stageId = safeTrim(currentStageId);
        if (!stageId.isBlank()) {
            for (StageRef stage : stages) {
                if (stage.id().equals(stageId)) return stage;
            }
        }
        return stages.get(0);
    }

    private String toJson(Map<String, String> map, String fallback) {
        try {
            return OBJECT_MAPPER.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Set<String> parseStringSet(String rawJson) {
        JsonNode node = parseJson(rawJson, "[]");
        Set<String> values = new LinkedHashSet<>();
        if (!node.isArray()) return values;
        for (JsonNode item : node) {
            String value = safeTrim(item.asText(""));
            if (!value.isBlank()) values.add(value);
        }
        return values;
    }

    private JsonNode parseJson(String raw, String fallbackJson) {
        try {
            return OBJECT_MAPPER.readTree(raw == null || raw.isBlank() ? fallbackJson : raw);
        } catch (Exception ex) {
            try {
                return OBJECT_MAPPER.readTree(fallbackJson);
            } catch (Exception impossible) {
                return OBJECT_MAPPER.createObjectNode();
            }
        }
    }

    private JpaCrmCompanyStateEntity defaultCrmEntity(UUID companyId) {
        Instant now = Instant.now();
        JpaCrmCompanyStateEntity row = new JpaCrmCompanyStateEntity();
        row.setCompanyId(companyId);
        row.setStagesJson("[]");
        row.setLeadStageMapJson("{}");
        row.setCustomFieldsJson("[]");
        row.setLeadFieldValuesJson("{}");
        row.setLeadFieldsOrderJson("[]");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private boolean isLowSignal(String textRaw) {
        String text = normalize(textRaw);
        if (text.isBlank()) return true;
        if (text.length() >= featureProperties.shortMessageMinLength() * 2) return false;
        for (String token : SIGNAL_TOKENS) {
            if (text.contains(token)) return false;
        }
        return true;
    }

    private boolean isSpecialStageTitle(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) return false;
        for (String token : SPECIAL_STAGE_TOKENS) {
            if (normalized.contains(token)) return true;
        }
        return false;
    }

    private String buildStageSetVersion(List<StageRef> stages, List<StageRuleRef> rules) {
        StringBuilder sb = new StringBuilder();
        for (StageRef stage : stages) {
            sb.append(stage.id()).append('|').append(stage.order()).append(';');
        }
        for (StageRuleRef rule : rules) {
            sb.append(rule.stageId())
                    .append('|').append(rule.enabled())
                    .append('|').append(rule.priority())
                    .append('|').append(rule.onlyForward())
                    .append('|').append(rule.prompt())
                    .append('|').append(rule.updatedAt() == null ? "" : rule.updatedAt().toString())
                    .append(';');
        }
        return sha256(sb.toString());
    }

    private String buildEvaluationKey(UUID conversationId, String cardId, UUID lastMessageId, String stageSetVersion) {
        return sha256(conversationId + "|" + safeTrim(cardId) + "|" + String.valueOf(lastMessageId) + "|" + safeTrim(stageSetVersion));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private double clampConfidence(double value) {
        if (value < 0.0d) return 0.0d;
        if (value > 1.0d) return 1.0d;
        return value;
    }

    private String fallbackModel() {
        return defaultOpenAiModel.isBlank() ? "gpt-5-mini" : defaultOpenAiModel;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalize(String value) {
        String base = safeTrim(value).toLowerCase(Locale.ROOT);
        return java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private String shorten(String value, int max) {
        String text = safeTrim(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max));
    }

    private String truncate(String value, int max) {
        String text = safeTrim(value);
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max));
    }

    private record LoadedContext(
            UUID companyId,
            UUID conversationId,
            String cardId,
            String agentId,
            List<StageRef> stages,
            StageRef currentStage,
            List<StageRuleRef> rules,
            List<CompactMessage> messages,
            CompactMessage latestMessage,
            List<String> events,
            String evaluationKey,
            String stageSetVersion,
            AgentRuntime runtime,
            JpaAiAgentKanbanStateEntity state
    ) {
    }

    private record StageRef(String id, String name, int order, boolean isFinalStage) {
    }

    private record StageRuleRef(
            String stageId,
            boolean enabled,
            String prompt,
            int priority,
            boolean onlyForward,
            Set<String> allowedFromStages,
            Instant updatedAt
    ) {
    }

    private record CompactMessage(UUID id, boolean fromMe, String text, Instant ts) {
    }

    private record CandidateStage(
            String id,
            String name,
            int order,
            String prompt,
            int priority,
            boolean onlyForward,
            Set<String> allowedFromStages
    ) {
    }

    private record AgentRuntime(String apiKey, String model, JsonNode agent) {
    }

    private record DecisionPayload(boolean move, String targetStageId, double confidence, String reason) {
    }

    private record LlmDecision(
            boolean move,
            String targetStageId,
            double confidence,
            String reason,
            String llmRequestId,
            int estimatedTokens
    ) {
    }

    private record GateDecision(boolean skip, String reason) {
        static GateDecision skip(String reason) {
            return new GateDecision(true, safeReason(reason));
        }

        static GateDecision continueEvaluation() {
            return new GateDecision(false, "");
        }

        private static String safeReason(String value) {
            return value == null || value.isBlank() ? "gating_skip" : value.trim();
        }
    }

    private record ValidationResult(boolean valid, String reason, String errorCode, String targetStageId) {
        static ValidationResult valid(String targetStageId) {
            return new ValidationResult(true, "", "", targetStageId);
        }

        static ValidationResult invalid(String reason, String errorCode, String targetStageId) {
            return new ValidationResult(false, reason, errorCode, targetStageId);
        }
    }

    private record ApplyMoveResult(boolean moved, String reason, String errorCode) {
        static ApplyMoveResult success() {
            return new ApplyMoveResult(true, "", "");
        }

        static ApplyMoveResult fail(String reason, String errorCode) {
            return new ApplyMoveResult(false, reason, errorCode);
        }
    }

    public record KanbanMoveEvaluationResult(
            String decision,
            boolean moved,
            String targetStageId,
            Double confidence,
            String reason,
            String errorCode,
            String evaluationKey,
            String llmRequestId
    ) {
    }
}

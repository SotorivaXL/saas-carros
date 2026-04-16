package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentCompanyStateEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorAgentRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorCompanyConfigRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorConversationStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorDecisionLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorAgentRuleEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorCompanyConfigEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorConversationStateEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorDecisionLogEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorEntity;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoConversationEntity;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoMessageEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.adapters.web.atendimentos.AtendimentoAutomationMessageService;
import com.io.appioweb.shared.errors.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SupervisorRoutingService {

    private static final Logger log = LoggerFactory.getLogger(SupervisorRoutingService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT = "Voce e um roteador deterministico de atendimento. Sua tarefa e escolher o agente de IA mais adequado para atender o lead, ou pedir UMA pergunta de triagem, ou transferir para humano. Responda APENAS com JSON valido, sem qualquer texto fora do JSON.";

    private final AiSupervisorRepositoryJpa supervisorRepository;
    private final AiSupervisorAgentRuleRepositoryJpa ruleRepository;
    private final AiSupervisorConversationStateRepositoryJpa stateRepository;
    private final AiSupervisorDecisionLogRepositoryJpa decisionLogRepository;
    private final AiSupervisorCompanyConfigRepositoryJpa companyConfigRepository;
    private final AtendimentoConversationRepositoryJpa conversationRepository;
    private final AtendimentoMessageRepositoryJpa messageRepository;
    private final AiAgentCompanyStateRepositoryJpa aiAgentStateRepository;
    private final SensitiveDataCrypto crypto;
    private final AiSupervisorLlmClient llmClient;
    private final AiSupervisorCandidateReducer candidateReducer;
    private final AiSupervisorDecisionParser decisionParser;
    private final AiSupervisorFeatureProperties featureProperties;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final AtendimentoAutomationMessageService automationMessageService;
    private final MeterRegistry meterRegistry;
    private final String openAiApiKey;
    private final String defaultOpenAiModel;

    public SupervisorRoutingService(
            AiSupervisorRepositoryJpa supervisorRepository,
            AiSupervisorAgentRuleRepositoryJpa ruleRepository,
            AiSupervisorConversationStateRepositoryJpa stateRepository,
            AiSupervisorDecisionLogRepositoryJpa decisionLogRepository,
            AiSupervisorCompanyConfigRepositoryJpa companyConfigRepository,
            AtendimentoConversationRepositoryJpa conversationRepository,
            AtendimentoMessageRepositoryJpa messageRepository,
            AiAgentCompanyStateRepositoryJpa aiAgentStateRepository,
            SensitiveDataCrypto crypto,
            AiSupervisorLlmClient llmClient,
            AiSupervisorCandidateReducer candidateReducer,
            AiSupervisorDecisionParser decisionParser,
            AiSupervisorFeatureProperties featureProperties,
            PlatformTransactionManager txManager,
            ApplicationEventPublisher eventPublisher,
            AtendimentoAutomationMessageService automationMessageService,
            MeterRegistry meterRegistry,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${OPENAI_DEFAULT_MODEL:gpt-5-mini}") String defaultOpenAiModel
    ) {
        this.supervisorRepository = supervisorRepository;
        this.ruleRepository = ruleRepository;
        this.stateRepository = stateRepository;
        this.decisionLogRepository = decisionLogRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.aiAgentStateRepository = aiAgentStateRepository;
        this.crypto = crypto;
        this.llmClient = llmClient;
        this.candidateReducer = candidateReducer;
        this.decisionParser = decisionParser;
        this.featureProperties = featureProperties;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.eventPublisher = eventPublisher;
        this.automationMessageService = automationMessageService;
        this.meterRegistry = meterRegistry;
        this.openAiApiKey = AiSupervisorSupport.safeTrim(openAiApiKey);
        this.defaultOpenAiModel = AiSupervisorSupport.safeTrim(defaultOpenAiModel);
    }

    public UUID findDefaultSupervisorId(UUID companyId) {
        if (!featureProperties.isEnabledFor(companyId)) return null;
        JpaAiSupervisorCompanyConfigEntity config = companyConfigRepository.findById(companyId).orElse(null);
        if (config != null && !config.isSupervisorEnabled()) return null;

        UUID configuredSupervisorId = config == null ? null : config.getDefaultSupervisorId();
        if (configuredSupervisorId != null) {
            JpaAiSupervisorEntity configured = supervisorRepository.findByIdAndCompanyId(configuredSupervisorId, companyId).orElse(null);
            if (configured != null && configured.isEnabled()) return configuredSupervisorId;
        }

        return supervisorRepository.findAllByCompanyIdAndEnabledTrueOrderByUpdatedAtDesc(companyId).stream()
                .map(JpaAiSupervisorEntity::getId)
                .findFirst()
                .orElse(null);
    }

    public RoutingResult routeOrTriageLead(UUID companyId, UUID supervisorId, UUID conversationId, UUID inboundMessageId) {
        return routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId, 0);
    }

    private RoutingResult routeOrTriageLead(UUID companyId, UUID supervisorId, UUID conversationId, UUID inboundMessageId, int attempt) {
        LoadedContext context = loadContext(companyId, supervisorId, conversationId, inboundMessageId);
        if (context.existingLog() != null) {
            meterRegistry.counter("supervisor_noop_total").increment();
            return resultFromExistingLog(context.existingLog());
        }

        GateDecision gate = applyGating(context);
        if (gate.skip()) {
            meterRegistry.counter("supervisor_noop_total").increment();
            persistNoopLog(context, gate.reason());
            log.info("supervisor_route noop correlationId={} companyId={} supervisorId={} reason={} evaluationKey={}",
                    conversationId, companyId, supervisorId, gate.reason(), context.evaluationKey());
            return RoutingResult.noop(gate.reason(), context.evaluationKey(), "");
        }

        CompactContext compactContext = buildCompactContext(context);
        List<AiSupervisorCandidateReducer.CandidateRef> allCandidates = buildCandidates(context);
        String leadText = compactContext.lastMessages().stream()
                .filter(item -> "lead".equals(item.role()))
                .map(CompactMessage::text)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        List<AiSupervisorCandidateReducer.CandidateRef> reducedCandidates = candidateReducer.reduce(
                allCandidates,
                leadText,
                featureProperties.candidateReductionThreshold(),
                featureProperties.maxCandidatesAfterReduction()
        );

        DecisionExecution decision;
        try {
            decision = decide(context, compactContext, reducedCandidates);
        } catch (BusinessException ex) {
            meterRegistry.counter("supervisor_errors_total").increment();
            log.warn("supervisor_route decision_error correlationId={} companyId={} supervisorId={} code={} reason={}",
                    conversationId, companyId, supervisorId, ex.code(), AiSupervisorSupport.safeTrim(ex.getMessage()));
            decision = fallbackFromError(context, reducedCandidates, ex.code(), ex.getMessage());
        }

        try {
            RoutingResult result = applyDecision(context, compactContext, decision);
            incrementMetrics(result.action());
            return result;
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
            if (attempt < featureProperties.retryOnOptimisticConflict()) {
                log.warn("supervisor_route retry correlationId={} companyId={} supervisorId={} attempt={} reason={}",
                        conversationId, companyId, supervisorId, attempt + 1, rootCauseMessage(ex));
                return routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId, attempt + 1);
            }
            meterRegistry.counter("supervisor_errors_total").increment();
            throw ex;
        }
    }

    private LoadedContext loadContext(UUID companyId, UUID supervisorId, UUID conversationId, UUID inboundMessageId) {
        JpaAiSupervisorEntity supervisor = supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)
                .orElseThrow(() -> new BusinessException("SUPERVISOR_NOT_FOUND", "Supervisor nao encontrado"));
        JpaAtendimentoConversationEntity conversation = conversationRepository.findByIdAndCompanyId(conversationId, companyId)
                .orElseThrow(() -> new BusinessException("ATENDIMENTO_CONVERSATION_NOT_FOUND", "Conversa nao encontrada"));
        JpaAtendimentoMessageEntity inboundMessage = messageRepository.findByIdAndCompanyId(inboundMessageId, companyId)
                .orElseThrow(() -> new BusinessException("ATENDIMENTO_MESSAGE_NOT_FOUND", "Mensagem de entrada nao encontrada"));
        if (!conversationId.equals(inboundMessage.getConversationId())) {
            throw new BusinessException("SUPERVISOR_MESSAGE_CONVERSATION_MISMATCH", "Mensagem nao pertence a conversa informada");
        }

        List<JpaAiSupervisorAgentRuleEntity> rules = ruleRepository
                .findAllByCompanyIdAndSupervisorIdAndEnabledTrueOrderByUpdatedAtDesc(companyId, supervisorId);
        JpaAiSupervisorConversationStateEntity state = stateRepository
                .findByCompanyIdAndSupervisorIdAndConversationId(companyId, supervisorId, conversationId)
                .orElse(null);
        CompanyAiRuntime companyAiRuntime = loadCompanyAiRuntime(companyId, supervisor.getProvider());
        List<String> humanChoiceOptions = AiSupervisorSupport.parseStringArray(supervisor.getHumanChoiceOptionsJson());
        String rulesVersionHash = buildRulesVersionHash(supervisor, rules);
        String evaluationKey = AiSupervisorSupport.sha256(supervisorId + "|" + conversationId + "|" + inboundMessageId + "|" + rulesVersionHash);
        JpaAiSupervisorDecisionLogEntity existingLog = decisionLogRepository
                .findFirstByCompanyIdAndSupervisorIdAndConversationIdAndEvaluationKeyOrderByCreatedAtDesc(
                        companyId,
                        supervisorId,
                        conversationId,
                        evaluationKey
                )
                .orElse(null);

        return new LoadedContext(
                companyId,
                supervisor,
                conversation,
                inboundMessage,
                rules,
                state,
                companyAiRuntime,
                humanChoiceOptions,
                rulesVersionHash,
                evaluationKey,
                existingLog
        );
    }

    private GateDecision applyGating(LoadedContext context) {
        if (!featureProperties.isEnabledFor(context.companyId())) {
            return GateDecision.skip("feature_disabled");
        }
        JpaAiSupervisorCompanyConfigEntity config = companyConfigRepository.findById(context.companyId()).orElse(null);
        if (config != null && !config.isSupervisorEnabled()) {
            return GateDecision.skip("company_disabled");
        }
        if (!context.supervisor().isEnabled()) {
            return GateDecision.skip("supervisor_disabled");
        }
        if (context.rules().isEmpty()) {
            return GateDecision.skip("no_enabled_agent_rules");
        }
        if (AiSupervisorSupport.trimToNull(context.conversation().getAssignedAgentId()) != null) {
            return GateDecision.skip("conversation_already_assigned");
        }
        if (context.conversation().isHumanHandoffRequested()) {
            return GateDecision.skip("human_handoff_requested");
        }
        if (context.inboundMessage().isFromMe()) {
            return GateDecision.skip("message_from_me");
        }
        if (context.state() != null) {
            if (context.inboundMessage().getId().equals(context.state().getLastEvaluatedMessageId())) {
                return GateDecision.skip("no_new_messages");
            }
            if (!context.state().isTriageAsked()
                    && context.state().getCooldownUntil() != null
                    && context.state().getCooldownUntil().isAfter(Instant.now())) {
                return GateDecision.skip("cooldown_active");
            }
        }
        return GateDecision.continueEvaluation();
    }

    private CompactContext buildCompactContext(LoadedContext context) {
        List<JpaAtendimentoMessageEntity> history = messageRepository
                .findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(context.conversation().getId(), context.companyId());
        if (history.isEmpty()) {
            throw new BusinessException("SUPERVISOR_NO_MESSAGES", "Nao ha mensagens para avaliar");
        }

        int inboundIndex = findMessageIndex(history, context.inboundMessage().getId());
        if (inboundIndex < 0) {
            throw new BusinessException("SUPERVISOR_INBOUND_MESSAGE_NOT_IN_TIMELINE", "Mensagem de entrada nao encontrada no historico");
        }

        List<CompactMessage> lastMessages = new ArrayList<>();
        if (context.state() != null && context.state().isTriageAsked() && context.state().getLastSupervisorQuestionMessageId() != null) {
            int questionIndex = findMessageIndex(history, context.state().getLastSupervisorQuestionMessageId());
            if (questionIndex >= 0) {
                JpaAtendimentoMessageEntity question = history.get(questionIndex);
                String questionText = compactMessageText(question);
                if (AiSupervisorSupport.trimToNull(questionText) != null) {
                    lastMessages.add(new CompactMessage(
                            question.getId(),
                            "supervisor",
                            truncateForPrompt(questionText, featureProperties.maxMessageChars()),
                            question.getCreatedAt()
                    ));
                }
                List<JpaAtendimentoMessageEntity> replies = extractLeadClusterAfterQuestion(history, questionIndex + 1, inboundIndex);
                for (JpaAtendimentoMessageEntity reply : replies) {
                    String text = compactMessageText(reply);
                    if (AiSupervisorSupport.trimToNull(text) == null) continue;
                    lastMessages.add(new CompactMessage(
                            reply.getId(),
                            "lead",
                            truncateForPrompt(text, featureProperties.maxMessageChars()),
                            reply.getCreatedAt()
                    ));
                }
            }
        } else {
            List<JpaAtendimentoMessageEntity> leadMessages = extractInitialLeadCluster(history, inboundIndex);
            for (JpaAtendimentoMessageEntity leadMessage : leadMessages) {
                String text = compactMessageText(leadMessage);
                if (AiSupervisorSupport.trimToNull(text) == null) continue;
                lastMessages.add(new CompactMessage(
                        leadMessage.getId(),
                        "lead",
                        truncateForPrompt(text, featureProperties.maxMessageChars()),
                        leadMessage.getCreatedAt()
                ));
            }
        }

        boolean isFirstContact = history.stream().noneMatch(JpaAtendimentoMessageEntity::isFromMe);
        boolean triageAskedAlready = context.state() != null && context.state().isTriageAsked();
        String contextSnippet = buildContextSnippet(lastMessages);
        return new CompactContext(List.copyOf(lastMessages), isFirstContact, triageAskedAlready, contextSnippet);
    }

    private List<AiSupervisorCandidateReducer.CandidateRef> buildCandidates(LoadedContext context) {
        List<AiSupervisorCandidateReducer.CandidateRef> candidates = new ArrayList<>();
        Map<String, String> agentNamesById = context.companyAiRuntime().agentNamesById();
        for (JpaAiSupervisorAgentRuleEntity rule : context.rules()) {
            String agentId = AiSupervisorSupport.safeTrim(rule.getAgentId());
            if (agentId.isBlank()) continue;
            String name = AiSupervisorSupport.trimToNull(agentNamesById.get(agentId));
            if (name == null) name = AiSupervisorSupport.trimToNull(rule.getAgentNameSnapshot());
            if (name == null) name = agentId;
            candidates.add(new AiSupervisorCandidateReducer.CandidateRef(
                    agentId,
                    name,
                    AiSupervisorSupport.truncate(rule.getTriageText(), featureProperties.maxCandidateTriageChars())
            ));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(AiSupervisorCandidateReducer.CandidateRef::agentName))
                .toList();
    }

    private DecisionExecution decide(LoadedContext context, CompactContext compactContext, List<AiSupervisorCandidateReducer.CandidateRef> candidates) {
        if (candidates.isEmpty()) {
            return fallbackFromError(context, candidates, "SUPERVISOR_NO_CANDIDATES", "Nenhum candidato disponivel para triagem");
        }
        String apiKey = context.companyAiRuntime().apiKey();
        if (apiKey.isBlank()) {
            throw new BusinessException("SUPERVISOR_LLM_API_KEY_MISSING", "Nao ha provedor configurado para o supervisor");
        }

        meterRegistry.counter("supervisor_llm_calls_total").increment();

        ObjectNode payload = buildPromptPayload(context, compactContext, candidates);
        String userPrompt;
        try {
            userPrompt = OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new BusinessException("SUPERVISOR_PROMPT_SERIALIZATION_ERROR", "Falha ao montar payload do supervisor");
        }

        AiSupervisorLlmClient.LlmResponse llmResponse = llmClient.classify(new AiSupervisorLlmClient.LlmRequest(
                apiKey,
                resolveModel(context.supervisor()),
                SYSTEM_PROMPT,
                userPrompt,
                featureProperties.maxOutputTokens()
        ));

        Set<String> candidateIds = new LinkedHashSet<>();
        for (AiSupervisorCandidateReducer.CandidateRef candidate : candidates) {
            candidateIds.add(candidate.agentId());
        }
        AiSupervisorDecisionParser.ParsedDecision parsed = decisionParser.parseStrict(
                llmResponse.outputText(),
                candidateIds,
                featureProperties.maxClarifyingQuestionChars()
        );
        return normalizeDecision(
                context,
                candidates,
                parsed,
                llmResponse.requestId(),
                llmResponse.inputTokens() + llmResponse.outputTokens()
        );
    }

    private DecisionExecution fallbackFromError(
            LoadedContext context,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates,
            String errorCode,
            String errorMessage
    ) {
        String shortMessage = AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(errorMessage), 220);
        if (context.state() == null || !context.state().isTriageAsked()) {
            return DecisionExecution.ask(
                    buildFallbackClarifyingQuestion(context.humanChoiceOptions(), context.supervisor()),
                    "fallback_after_llm_error",
                    0.2d,
                    errorCode,
                    shortMessage,
                    "",
                    0
            );
        }
        return finalFallbackDecision(context, candidates, "fallback_after_llm_error", errorCode, shortMessage, "", 0);
    }

    private DecisionExecution normalizeDecision(
            LoadedContext context,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates,
            AiSupervisorDecisionParser.ParsedDecision parsed,
            String llmRequestId,
            int estimatedTokens
    ) {
        if (parsed.action() == AiSupervisorAction.ASK_CLARIFYING && context.state() != null && context.state().isTriageAsked()) {
            return finalFallbackDecision(context, candidates, "ask_after_triage_fallback", "", "", llmRequestId, estimatedTokens);
        }
        if (parsed.action() == AiSupervisorAction.NO_ACTION) {
            if (context.state() != null && context.state().isTriageAsked()) {
                return finalFallbackDecision(context, candidates, "no_action_after_triage_fallback", "", "", llmRequestId, estimatedTokens);
            }
            return DecisionExecution.ask(
                    buildFallbackClarifyingQuestion(context.humanChoiceOptions(), context.supervisor()),
                    "fallback_clarifying_question",
                    parsed.confidence(),
                    "",
                    "",
                    llmRequestId,
                    estimatedTokens
            );
        }
        if (parsed.action() == AiSupervisorAction.HANDOFF_HUMAN && !context.supervisor().isHumanHandoffEnabled()) {
            return finalFallbackDecision(context, candidates, "handoff_disabled_fallback", "", "", llmRequestId, estimatedTokens);
        }

        return switch (parsed.action()) {
            case ASSIGN_AGENT -> DecisionExecution.assign(parsed.targetAgentId(), parsed.reason(), parsed.confidence(), "", "", llmRequestId, estimatedTokens);
            case ASK_CLARIFYING -> DecisionExecution.ask(parsed.messageToSend(), parsed.reason(), parsed.confidence(), "", "", llmRequestId, estimatedTokens);
            case HANDOFF_HUMAN -> {
                String queue = resolveHumanQueue(parsed.humanQueue(), context.humanChoiceOptions(), context.supervisor().getHumanHandoffTeam());
                String message = resolveHandoffMessage(context.supervisor(), context.humanChoiceOptions(), queue, parsed.messageToSend());
                yield DecisionExecution.handoff(queue, message, parsed.reason(), parsed.confidence(), "", "", llmRequestId, estimatedTokens);
            }
            case NO_ACTION -> DecisionExecution.noAction(parsed.reason(), parsed.confidence(), "", "", llmRequestId, estimatedTokens);
        };
    }

    private DecisionExecution finalFallbackDecision(
            LoadedContext context,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates,
            String reason,
            String errorCode,
            String errorMessageShort,
            String llmRequestId,
            int estimatedTokens
    ) {
        AiSupervisorCandidateReducer.CandidateRef defaultAgent = candidates.stream()
                .sorted(Comparator.comparing(AiSupervisorCandidateReducer.CandidateRef::agentName))
                .findFirst()
                .orElse(null);
        if (defaultAgent != null) {
            return DecisionExecution.assign(defaultAgent.agentId(), reason, 0.35d, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
        }
        if (context.supervisor().isHumanHandoffEnabled()) {
            String queue = resolveHumanQueue(
                    context.humanChoiceOptions().size() == 1 ? context.humanChoiceOptions().get(0) : null,
                    context.humanChoiceOptions(),
                    context.supervisor().getHumanHandoffTeam()
            );
            return DecisionExecution.handoff(
                    queue,
                    resolveHandoffMessage(context.supervisor(), context.humanChoiceOptions(), queue, null),
                    reason,
                    0.25d,
                    errorCode,
                    errorMessageShort,
                    llmRequestId,
                    estimatedTokens
            );
        }
        return DecisionExecution.noAction(reason, 0.1d, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
    }

    private ObjectNode buildPromptPayload(
            LoadedContext context,
            CompactContext compactContext,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates
    ) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode supervisor = OBJECT_MAPPER.createObjectNode();
        supervisor.put("name", AiSupervisorSupport.truncate(context.supervisor().getName(), 180));
        supervisor.put("communicationStyle", AiSupervisorSupport.truncate(context.supervisor().getCommunicationStyle(), 280));
        supervisor.put("profile", AiSupervisorSupport.truncate(context.supervisor().getProfile(), 280));
        supervisor.put("objective", AiSupervisorSupport.truncate(context.supervisor().getObjective(), 280));
        root.set("supervisor", supervisor);

        ObjectNode compact = OBJECT_MAPPER.createObjectNode();
        compact.put("isFirstContact", compactContext.isFirstContact());
        compact.put("triageAskedAlready", compactContext.triageAskedAlready());
        ArrayNode messages = OBJECT_MAPPER.createArrayNode();
        for (CompactMessage message : compactContext.lastMessages()) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("role", message.role());
            node.put("text", AiSupervisorSupport.truncate(message.text(), featureProperties.maxMessageChars()));
            node.put("ts", message.ts() == null ? "" : message.ts().toString());
            messages.add(node);
        }
        compact.set("lastMessages", messages);
        root.set("context", compact);

        ArrayNode candidatesNode = OBJECT_MAPPER.createArrayNode();
        for (AiSupervisorCandidateReducer.CandidateRef candidate : candidates) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("agentId", candidate.agentId());
            node.put("agentName", AiSupervisorSupport.truncate(candidate.agentName(), 180));
            node.put("triageText", AiSupervisorSupport.truncate(candidate.triageText(), featureProperties.maxCandidateTriageChars()));
            candidatesNode.add(node);
        }
        root.set("candidates", candidatesNode);

        ObjectNode humanHandoff = OBJECT_MAPPER.createObjectNode();
        humanHandoff.put("enabled", context.supervisor().isHumanHandoffEnabled());
        humanHandoff.put("userChoiceEnabled", context.supervisor().isHumanUserChoiceEnabled());
        ArrayNode options = OBJECT_MAPPER.createArrayNode();
        for (String option : context.humanChoiceOptions()) {
            String normalized = AiSupervisorSupport.truncate(option, 80);
            if (!normalized.isBlank()) options.add(normalized);
        }
        humanHandoff.set("options", options);
        root.set("humanHandoff", humanHandoff);

        ObjectNode constraints = OBJECT_MAPPER.createObjectNode();
        constraints.put("otherRules", AiSupervisorSupport.truncate(context.supervisor().getOtherRules(), featureProperties.maxOtherRulesChars()));
        constraints.put("maxClarifyingQuestions", 1);
        root.set("constraints", constraints);
        return root;
    }

    private RoutingResult applyDecision(LoadedContext context, CompactContext compactContext, DecisionExecution decision) {
        return transactionTemplate.execute(status -> {
            JpaAiSupervisorDecisionLogEntity existing = decisionLogRepository
                    .findFirstByCompanyIdAndSupervisorIdAndConversationIdAndEvaluationKeyOrderByCreatedAtDesc(
                            context.companyId(),
                            context.supervisor().getId(),
                            context.conversation().getId(),
                            context.evaluationKey()
                    )
                    .orElse(null);
            if (existing != null) {
                return resultFromExistingLog(existing);
            }

            JpaAiSupervisorConversationStateEntity state = stateRepository
                    .findByCompanyIdAndSupervisorIdAndConversationId(
                            context.companyId(),
                            context.supervisor().getId(),
                            context.conversation().getId()
                    )
                    .orElseGet(() -> newConversationState(context));
            if (context.inboundMessage().getId().equals(state.getLastEvaluatedMessageId())) {
                return RoutingResult.noop("no_new_messages", context.evaluationKey(), decision.errorCode());
            }

            JpaAtendimentoConversationEntity liveConversation = conversationRepository
                    .findByIdAndCompanyId(context.conversation().getId(), context.companyId())
                    .orElseThrow(() -> new BusinessException("ATENDIMENTO_CONVERSATION_NOT_FOUND", "Conversa nao encontrada"));

            if (AiSupervisorSupport.trimToNull(liveConversation.getAssignedAgentId()) != null) {
                persistDecisionLog(context, compactContext, DecisionExecution.noAction("conversation_already_assigned", 0.0d, "", "", "", 0));
                return RoutingResult.noop("conversation_already_assigned", context.evaluationKey(), "");
            }

            Instant now = Instant.now();
            AtendimentoAutomationMessageService.SentMessageResult sentMessage = null;
            String outboundMessage = switch (decision.action()) {
                case ASSIGN_AGENT -> context.supervisor().isNotifyContactOnAgentTransfer()
                        ? buildAgentTransferNotificationMessage()
                        : null;
                case ASK_CLARIFYING, HANDOFF_HUMAN -> AiSupervisorSupport.trimToNull(decision.messageToSend());
                case NO_ACTION -> null;
            };
            if (AiSupervisorSupport.trimToNull(outboundMessage) != null) {
                sentMessage = automationMessageService.sendAutomaticText(
                        context.companyId(),
                        context.conversation().getPhone(),
                        outboundMessage,
                        1
                );
                liveConversation = conversationRepository.findByIdAndCompanyId(context.conversation().getId(), context.companyId())
                        .orElseThrow(() -> new BusinessException("ATENDIMENTO_CONVERSATION_NOT_FOUND", "Conversa nao encontrada"));
            }

            updateStateForDecision(state, context, decision, sentMessage, now);
            updateConversationForDecision(liveConversation, context, decision, now);
            stateRepository.saveAndFlush(state);
            conversationRepository.saveAndFlush(liveConversation);
            persistDecisionLog(context, compactContext, decision);
            publishEvent(context, decision, sentMessage, now);

            log.info("supervisor_route action correlationId={} companyId={} supervisorId={} action={} targetAgentId={} evaluationKey={} errorCode={}",
                    context.conversation().getId(),
                    context.companyId(),
                    context.supervisor().getId(),
                    decision.action(),
                    AiSupervisorSupport.safeTrim(decision.targetAgentId()),
                    context.evaluationKey(),
                    AiSupervisorSupport.safeTrim(decision.errorCode()));

            return new RoutingResult(
                    decision.action(),
                    AiSupervisorSupport.trimToNull(decision.targetAgentId()),
                    sentMessage == null ? null : sentMessage.messageId(),
                    context.evaluationKey(),
                    AiSupervisorSupport.safeTrim(decision.reason()),
                    AiSupervisorSupport.safeTrim(decision.errorCode()),
                    false
            );
        });
    }

    private void persistNoopLog(LoadedContext context, String reason) {
        if (context.existingLog() != null) return;
        JpaAiSupervisorDecisionLogEntity row = new JpaAiSupervisorDecisionLogEntity();
        row.setId(UUID.randomUUID());
        row.setCompanyId(context.companyId());
        row.setSupervisorId(context.supervisor().getId());
        row.setConversationId(context.conversation().getId());
        row.setInboundMessageId(context.inboundMessage().getId());
        row.setAction(AiSupervisorAction.NO_ACTION.name());
        row.setConfidence(BigDecimal.ZERO);
        row.setReason(AiSupervisorSupport.truncate(reason, 180));
        row.setEvaluationKey(AiSupervisorSupport.truncate(context.evaluationKey(), 200));
        row.setContextSnippet(AiSupervisorSupport.trimToNull(AiSupervisorSupport.truncate(context.compactContextSnippet(), 400)));
        row.setCreatedAt(Instant.now());
        try {
            decisionLogRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException ignored) {
            // idempotent noop
        }
    }

    private void updateStateForDecision(
            JpaAiSupervisorConversationStateEntity state,
            LoadedContext context,
            DecisionExecution decision,
            AtendimentoAutomationMessageService.SentMessageResult sentMessage,
            Instant now
    ) {
        state.setLastEvaluatedMessageId(context.inboundMessage().getId());
        state.setLastDecisionAt(now);
        state.setCooldownUntil(now.plusSeconds(featureProperties.cooldownSeconds()));
        state.setUpdatedAt(now);
        switch (decision.action()) {
            case ASSIGN_AGENT -> {
                state.setAssignedAgentId(AiSupervisorSupport.trimToNull(decision.targetAgentId()));
                state.setTriageAsked(false);
            }
            case ASK_CLARIFYING -> {
                state.setAssignedAgentId(null);
                state.setTriageAsked(true);
                state.setLastSupervisorQuestionMessageId(sentMessage == null ? null : sentMessage.messageId());
            }
            case HANDOFF_HUMAN, NO_ACTION -> {
                state.setAssignedAgentId(null);
                state.setTriageAsked(false);
            }
        }
    }

    private void updateConversationForDecision(
            JpaAtendimentoConversationEntity conversation,
            LoadedContext context,
            DecisionExecution decision,
            Instant now
    ) {
        conversation.setUpdatedAt(now);
        switch (decision.action()) {
            case ASSIGN_AGENT -> {
                conversation.setAssignedAgentId(AiSupervisorSupport.trimToNull(decision.targetAgentId()));
                conversation.setHumanHandoffRequested(false);
                conversation.setHumanHandoffQueue(null);
                conversation.setHumanHandoffRequestedAt(null);
                conversation.setHumanUserChoiceRequired(false);
                conversation.setHumanChoiceOptionsJson("[]");
            }
            case ASK_CLARIFYING -> {
                conversation.setHumanHandoffRequested(false);
                conversation.setHumanHandoffQueue(null);
                conversation.setHumanHandoffRequestedAt(null);
                conversation.setHumanUserChoiceRequired(false);
                conversation.setHumanChoiceOptionsJson("[]");
            }
            case HANDOFF_HUMAN -> {
                conversation.setAssignedAgentId(null);
                conversation.setHumanHandoffRequested(true);
                conversation.setHumanHandoffQueue(resolveHumanQueue(
                        decision.humanQueue(),
                        context.humanChoiceOptions(),
                        context.supervisor().getHumanHandoffTeam()
                ));
                conversation.setHumanHandoffRequestedAt(now);
                boolean userChoiceRequired = context.supervisor().isHumanUserChoiceEnabled()
                        && !context.humanChoiceOptions().isEmpty()
                        && AiSupervisorSupport.trimToNull(conversation.getHumanHandoffQueue()) == null;
                conversation.setHumanUserChoiceRequired(userChoiceRequired);
                conversation.setHumanChoiceOptionsJson(AiSupervisorSupport.toJsonArray(context.humanChoiceOptions()));
            }
            case NO_ACTION -> {
                // preserve current flags on noop
            }
        }
    }

    private JpaAiSupervisorDecisionLogEntity persistDecisionLog(
            LoadedContext context,
            CompactContext compactContext,
            DecisionExecution decision
    ) {
        JpaAiSupervisorDecisionLogEntity row = new JpaAiSupervisorDecisionLogEntity();
        row.setId(UUID.randomUUID());
        row.setCompanyId(context.companyId());
        row.setSupervisorId(context.supervisor().getId());
        row.setConversationId(context.conversation().getId());
        row.setInboundMessageId(context.inboundMessage().getId());
        row.setAction(decision.action().name());
        row.setTargetAgentId(AiSupervisorSupport.trimToNull(decision.targetAgentId()));
        row.setConfidence(BigDecimal.valueOf(clampConfidence(decision.confidence())));
        row.setReason(AiSupervisorSupport.trimToNull(AiSupervisorSupport.truncate(decision.reason(), 180)));
        row.setEvaluationKey(AiSupervisorSupport.truncate(context.evaluationKey(), 200));
        row.setErrorCode(AiSupervisorSupport.trimToNull(AiSupervisorSupport.truncate(decision.errorCode(), 80)));
        row.setErrorMessageShort(AiSupervisorSupport.trimToNull(AiSupervisorSupport.truncate(decision.errorMessageShort(), 220)));
        row.setContextSnippet(AiSupervisorSupport.trimToNull(AiSupervisorSupport.truncate(compactContext.contextSnippet(), 400)));
        row.setCreatedAt(Instant.now());
        try {
            return decisionLogRepository.saveAndFlush(row);
        } catch (DataIntegrityViolationException duplicated) {
            return decisionLogRepository.findFirstByCompanyIdAndSupervisorIdAndConversationIdAndEvaluationKeyOrderByCreatedAtDesc(
                    context.companyId(),
                    context.supervisor().getId(),
                    context.conversation().getId(),
                    context.evaluationKey()
            ).orElse(row);
        }
    }

    private void publishEvent(
            LoadedContext context,
            DecisionExecution decision,
            AtendimentoAutomationMessageService.SentMessageResult sentMessage,
            Instant now
    ) {
        switch (decision.action()) {
            case ASSIGN_AGENT -> eventPublisher.publishEvent(new ConversationAssignedToAgentEvent(
                    context.companyId(),
                    context.supervisor().getId(),
                    context.conversation().getId(),
                    decision.targetAgentId(),
                    context.evaluationKey(),
                    now
            ));
            case ASK_CLARIFYING -> eventPublisher.publishEvent(new SupervisorAskedClarificationEvent(
                    context.companyId(),
                    context.supervisor().getId(),
                    context.conversation().getId(),
                    sentMessage == null ? null : sentMessage.messageId(),
                    context.evaluationKey(),
                    now
            ));
            case HANDOFF_HUMAN -> eventPublisher.publishEvent(new ConversationHandoffHumanRequestedEvent(
                    context.companyId(),
                    context.supervisor().getId(),
                    context.conversation().getId(),
                    resolveHumanQueue(decision.humanQueue(), context.humanChoiceOptions(), context.supervisor().getHumanHandoffTeam()),
                    context.supervisor().isHumanUserChoiceEnabled(),
                    context.humanChoiceOptions(),
                    context.evaluationKey(),
                    now
            ));
            case NO_ACTION -> {
            }
        }
    }

    private RoutingResult resultFromExistingLog(JpaAiSupervisorDecisionLogEntity existingLog) {
        AiSupervisorAction action = AiSupervisorAction.valueOf(existingLog.getAction());
        return new RoutingResult(
                action,
                AiSupervisorSupport.trimToNull(existingLog.getTargetAgentId()),
                null,
                AiSupervisorSupport.safeTrim(existingLog.getEvaluationKey()),
                AiSupervisorSupport.safeTrim(existingLog.getReason()),
                AiSupervisorSupport.safeTrim(existingLog.getErrorCode()),
                true
        );
    }

    private void incrementMetrics(AiSupervisorAction action) {
        switch (action) {
            case ASSIGN_AGENT -> meterRegistry.counter("supervisor_assign_total").increment();
            case ASK_CLARIFYING -> meterRegistry.counter("supervisor_ask_total").increment();
            case HANDOFF_HUMAN -> meterRegistry.counter("supervisor_handoff_total").increment();
            case NO_ACTION -> meterRegistry.counter("supervisor_noop_total").increment();
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private CompanyAiRuntime loadCompanyAiRuntime(UUID companyId, String supervisorProvider) {
        JpaAiAgentCompanyStateEntity state = aiAgentStateRepository.findById(companyId).orElse(null);
        if (state == null) {
            return new CompanyAiRuntime(Map.of(), openAiApiKey);
        }

        Map<String, String> agentNames = new LinkedHashMap<>();
        JsonNode agentsNode = AiSupervisorSupport.parseJson(state.getAgentsJson(), "[]");
        if (agentsNode.isArray()) {
            for (JsonNode agent : agentsNode) {
                String id = AiSupervisorSupport.safeTrim(agent.path("id").asText(""));
                String name = AiSupervisorSupport.safeTrim(agent.path("name").asText(""));
                if (!id.isBlank()) {
                    agentNames.put(id, name.isBlank() ? id : name);
                }
            }
        }

        String apiKey = openAiApiKey;
        String providerHint = AiSupervisorSupport.safeTrim(supervisorProvider).toLowerCase(Locale.ROOT);
        if (apiKey.isBlank()) {
            String providersJson;
            try {
                providersJson = crypto.decrypt(state.getProvidersJson());
            } catch (Exception ignored) {
                providersJson = "[]";
            }
            JsonNode providersNode = AiSupervisorSupport.parseJson(providersJson, "[]");
            if (providersNode.isArray()) {
                for (JsonNode provider : providersNode) {
                    String id = AiSupervisorSupport.safeTrim(provider.path("id").asText("")).toLowerCase(Locale.ROOT);
                    String type = AiSupervisorSupport.safeTrim(provider.path("type").asText("")).toLowerCase(Locale.ROOT);
                    if (!providerHint.isBlank() && !providerHint.equals(id) && !providerHint.equals(type)) continue;
                    String candidateKey = AiSupervisorSupport.safeTrim(provider.path("apiKey").asText(""));
                    if (!candidateKey.isBlank()) {
                        apiKey = candidateKey;
                        break;
                    }
                }
            }
        }
        return new CompanyAiRuntime(Map.copyOf(agentNames), apiKey);
    }

    private String buildRulesVersionHash(JpaAiSupervisorEntity supervisor, List<JpaAiSupervisorAgentRuleEntity> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append(supervisor.getId()).append('|')
                .append(supervisor.getUpdatedAt() == null ? "" : supervisor.getUpdatedAt().toString()).append('|')
                .append(supervisor.isEnabled()).append('|')
                .append(AiSupervisorSupport.safeTrim(supervisor.getModel())).append('|')
                .append(AiSupervisorSupport.safeTrim(supervisor.getProvider())).append('|')
                .append(AiSupervisorSupport.safeTrim(supervisor.getReasoningModelVersion())).append('|')
                .append(AiSupervisorSupport.safeTrim(supervisor.getOtherRules())).append('|');
        for (JpaAiSupervisorAgentRuleEntity rule : rules) {
            sb.append(rule.getId()).append('|')
                    .append(AiSupervisorSupport.safeTrim(rule.getAgentId())).append('|')
                    .append(rule.isEnabled()).append('|')
                    .append(AiSupervisorSupport.safeTrim(rule.getTriageText())).append('|')
                    .append(rule.getUpdatedAt() == null ? "" : rule.getUpdatedAt().toString()).append(';');
        }
        return AiSupervisorSupport.sha256(sb.toString());
    }

    private List<JpaAtendimentoMessageEntity> extractInitialLeadCluster(List<JpaAtendimentoMessageEntity> history, int inboundIndex) {
        List<JpaAtendimentoMessageEntity> cluster = new ArrayList<>();
        Instant previousTs = null;
        for (int i = inboundIndex; i >= 0 && cluster.size() < featureProperties.maxInitialMessages(); i--) {
            JpaAtendimentoMessageEntity message = history.get(i);
            if (message.isFromMe()) break;
            String text = compactMessageText(message);
            if (AiSupervisorSupport.trimToNull(text) == null) continue;
            if (previousTs != null && message.getCreatedAt() != null) {
                long seconds = Math.abs(previousTs.getEpochSecond() - message.getCreatedAt().getEpochSecond());
                if (seconds > featureProperties.initialBatchWindowSeconds()) break;
            }
            cluster.add(message);
            previousTs = message.getCreatedAt();
        }
        cluster.sort(Comparator.comparing(JpaAtendimentoMessageEntity::getCreatedAt));
        return cluster;
    }

    private List<JpaAtendimentoMessageEntity> extractLeadClusterAfterQuestion(List<JpaAtendimentoMessageEntity> history, int startIndex, int inboundIndex) {
        List<JpaAtendimentoMessageEntity> replies = new ArrayList<>();
        Instant previousTs = null;
        for (int i = startIndex; i <= inboundIndex && replies.size() < 2; i++) {
            JpaAtendimentoMessageEntity message = history.get(i);
            if (message.isFromMe()) break;
            String text = compactMessageText(message);
            if (AiSupervisorSupport.trimToNull(text) == null) continue;
            if (previousTs != null && message.getCreatedAt() != null) {
                long seconds = Math.abs(message.getCreatedAt().getEpochSecond() - previousTs.getEpochSecond());
                if (seconds > featureProperties.initialBatchWindowSeconds()) break;
            }
            replies.add(message);
            previousTs = message.getCreatedAt();
        }
        return replies;
    }

    private int findMessageIndex(List<JpaAtendimentoMessageEntity> history, UUID messageId) {
        for (int i = 0; i < history.size(); i++) {
            if (messageId.equals(history.get(i).getId())) return i;
        }
        return -1;
    }

    private String compactMessageText(JpaAtendimentoMessageEntity message) {
        String text = AiSupervisorSupport.trimToNull(message.getMessageText());
        if (text != null) return text;
        String type = AiSupervisorSupport.safeTrim(message.getMessageType()).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "audio" -> "[audio]";
            case "image" -> "[imagem]";
            case "video" -> "[video]";
            case "document" -> "[documento]";
            case "location" -> "[localizacao]";
            case "sticker" -> "[sticker]";
            default -> "";
        };
    }

    private String truncateForPrompt(String text, int max) {
        return AiSupervisorSupport.maskPii(AiSupervisorSupport.truncate(text, max));
    }

    private String buildContextSnippet(List<CompactMessage> messages) {
        if (messages == null || messages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CompactMessage message : messages) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(message.role()).append(": ").append(message.text());
        }
        return AiSupervisorSupport.maskPii(AiSupervisorSupport.truncate(sb.toString(), 400));
    }

    private String buildAgentTransferNotificationMessage() {
        return "Parece que sua duvida e com outra equipe. Estou transferindo seu atendimento agora.";
    }

    private String buildFallbackClarifyingQuestion(List<String> options, JpaAiSupervisorEntity supervisor) {
        if (supervisor.isHumanUserChoiceEnabled() && options != null && !options.isEmpty()) {
            List<String> limited = options.stream()
                    .map(item -> AiSupervisorSupport.truncate(item, 40))
                    .filter(item -> !item.isBlank())
                    .limit(3)
                    .toList();
            if (!limited.isEmpty()) {
                StringBuilder sb = new StringBuilder("Para eu te direcionar melhor, voce procura ");
                for (int i = 0; i < limited.size(); i++) {
                    if (i > 0) sb.append(i == limited.size() - 1 ? ", ou " : ", ");
                    sb.append("(").append(i + 1).append(") ").append(limited.get(i));
                }
                sb.append("?");
                return AiSupervisorSupport.truncate(sb.toString(), featureProperties.maxClarifyingQuestionChars());
            }
        }
        return "Para eu te direcionar melhor, qual e o principal objetivo do seu contato?";
    }

    private String resolveHandoffMessage(
            JpaAiSupervisorEntity supervisor,
            List<String> options,
            String resolvedQueue,
            String llmSuggestedMessage
    ) {
        if (!supervisor.isHumanHandoffSendMessage()) {
            return null;
        }
        String configuredMessage = AiSupervisorSupport.trimToNull(supervisor.getHumanHandoffMessage());
        if (configuredMessage != null) {
            return AiSupervisorSupport.truncate(configuredMessage, 500);
        }
        String suggested = AiSupervisorSupport.trimToNull(llmSuggestedMessage);
        if (suggested != null) {
            return AiSupervisorSupport.truncate(suggested, 500);
        }
        return buildDefaultHandoffMessage(supervisor, options, resolvedQueue);
    }

    private String buildDefaultHandoffMessage(JpaAiSupervisorEntity supervisor, List<String> options, String resolvedQueue) {
        if (AiSupervisorSupport.trimToNull(resolvedQueue) != null) {
            return "Vou encaminhar seu atendimento para o time humano responsavel.";
        }
        if (supervisor.isHumanUserChoiceEnabled() && options != null && !options.isEmpty()) {
            String joined = String.join(", ", options.stream()
                    .map(item -> AiSupervisorSupport.truncate(item, 30))
                    .limit(3)
                    .toList());
            return AiSupervisorSupport.truncate(
                    "Vou encaminhar seu atendimento para um atendente humano. Se preferir, responda com: " + joined + ".",
                    220
            );
        }
        return "Vou encaminhar seu atendimento para um atendente humano.";
    }

    private String resolveHumanQueue(String requestedQueue, List<String> options, String configuredQueue) {
        String normalized = AiSupervisorSupport.trimToNull(requestedQueue);
        if (normalized != null) {
            if (options == null || options.isEmpty()) return normalized;
            for (String option : options) {
                if (normalized.equalsIgnoreCase(option)) return option;
            }
            return null;
        }
        String fallback = AiSupervisorSupport.trimToNull(configuredQueue);
        if (fallback == null) return null;
        if (options == null || options.isEmpty()) return fallback;
        for (String option : options) {
            if (fallback.equalsIgnoreCase(option)) return option;
        }
        return fallback;
    }

    private JpaAiSupervisorConversationStateEntity newConversationState(LoadedContext context) {
        JpaAiSupervisorConversationStateEntity state = new JpaAiSupervisorConversationStateEntity();
        Instant now = Instant.now();
        state.setId(UUID.randomUUID());
        state.setCompanyId(context.companyId());
        state.setSupervisorId(context.supervisor().getId());
        state.setConversationId(context.conversation().getId());
        state.setCardId(context.conversation().getId().toString());
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        state.setVersion(0L);
        return state;
    }

    private double clampConfidence(double value) {
        if (value < 0.0d) return 0.0d;
        if (value > 1.0d) return 1.0d;
        return value;
    }

    private String resolveModel(JpaAiSupervisorEntity supervisor) {
        String model = AiSupervisorSupport.trimToNull(supervisor.getModel());
        if (model != null) return model;
        return defaultOpenAiModel.isBlank() ? "gpt-5-mini" : defaultOpenAiModel;
    }

    private record LoadedContext(
            UUID companyId,
            JpaAiSupervisorEntity supervisor,
            JpaAtendimentoConversationEntity conversation,
            JpaAtendimentoMessageEntity inboundMessage,
            List<JpaAiSupervisorAgentRuleEntity> rules,
            JpaAiSupervisorConversationStateEntity state,
            CompanyAiRuntime companyAiRuntime,
            List<String> humanChoiceOptions,
            String rulesVersionHash,
            String evaluationKey,
            JpaAiSupervisorDecisionLogEntity existingLog
    ) {
        String compactContextSnippet() {
            return inboundMessage == null
                    ? ""
                    : AiSupervisorSupport.maskPii(
                    AiSupervisorSupport.truncate(
                            AiSupervisorSupport.safeTrim(inboundMessage.getMessageText()),
                            400
                    )
            );
        }
    }

    private record CompanyAiRuntime(
            Map<String, String> agentNamesById,
            String apiKey
    ) {
    }

    private record CompactContext(
            List<CompactMessage> lastMessages,
            boolean isFirstContact,
            boolean triageAskedAlready,
            String contextSnippet
    ) {
    }

    private record CompactMessage(
            UUID id,
            String role,
            String text,
            Instant ts
    ) {
    }

    private record GateDecision(boolean skip, String reason) {
        static GateDecision skip(String reason) {
            return new GateDecision(true, reason == null || reason.isBlank() ? "gating_skip" : reason.trim());
        }

        static GateDecision continueEvaluation() {
            return new GateDecision(false, "");
        }
    }

    private record DecisionExecution(
            AiSupervisorAction action,
            String targetAgentId,
            String messageToSend,
            String humanQueue,
            String reason,
            double confidence,
            String errorCode,
            String errorMessageShort,
            String llmRequestId,
            int estimatedTokens
    ) {
        static DecisionExecution assign(String targetAgentId, String reason, double confidence, String errorCode, String errorMessageShort, String llmRequestId, int estimatedTokens) {
            return new DecisionExecution(AiSupervisorAction.ASSIGN_AGENT, targetAgentId, null, null, reason, confidence, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
        }

        static DecisionExecution ask(String messageToSend, String reason, double confidence, String errorCode, String errorMessageShort, String llmRequestId, int estimatedTokens) {
            return new DecisionExecution(AiSupervisorAction.ASK_CLARIFYING, null, messageToSend, null, reason, confidence, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
        }

        static DecisionExecution handoff(String humanQueue, String messageToSend, String reason, double confidence, String errorCode, String errorMessageShort, String llmRequestId, int estimatedTokens) {
            return new DecisionExecution(AiSupervisorAction.HANDOFF_HUMAN, null, messageToSend, humanQueue, reason, confidence, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
        }

        static DecisionExecution noAction(String reason, double confidence, String errorCode, String errorMessageShort, String llmRequestId, int estimatedTokens) {
            return new DecisionExecution(AiSupervisorAction.NO_ACTION, null, null, null, reason, confidence, errorCode, errorMessageShort, llmRequestId, estimatedTokens);
        }
    }

    public record RoutingResult(
            AiSupervisorAction action,
            String targetAgentId,
            UUID outboundMessageId,
            String evaluationKey,
            String reason,
            String errorCode,
            boolean duplicate
    ) {
        public static RoutingResult noop(String reason, String evaluationKey, String errorCode) {
            return new RoutingResult(AiSupervisorAction.NO_ACTION, null, null, evaluationKey, reason, errorCode, false);
        }

        public boolean assignedAgent() {
            return action == AiSupervisorAction.ASSIGN_AGENT && AiSupervisorSupport.trimToNull(targetAgentId) != null;
        }

        public boolean askedClarification() {
            return action == AiSupervisorAction.ASK_CLARIFYING;
        }

        public boolean humanHandoff() {
            return action == AiSupervisorAction.HANDOFF_HUMAN;
        }
    }
}

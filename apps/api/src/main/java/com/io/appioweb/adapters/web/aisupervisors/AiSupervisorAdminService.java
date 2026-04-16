package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentCompanyStateEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorAgentRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorCompanyConfigRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorAgentRuleEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorCompanyConfigEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorDistributionHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorSimulateHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.request.AiSupervisorUpsertHttpRequest;
import com.io.appioweb.adapters.web.aisupervisors.response.AiSupervisorHttpResponse;
import com.io.appioweb.adapters.web.aisupervisors.response.AiSupervisorSimulateHttpResponse;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AiSupervisorAdminService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SYSTEM_PROMPT = "Voce e um roteador deterministico de atendimento. Sua tarefa e escolher o agente de IA mais adequado para atender o lead, ou pedir UMA pergunta de triagem, ou transferir para humano. Responda APENAS com JSON valido, sem qualquer texto fora do JSON.";

    private static final List<String> HUMAN_HINT_KEYWORDS = List.of(
            "humano",
            "atendente",
            "pessoa",
            "especialista",
            "reclamacao",
            "cancelamento",
            "procon",
            "advogado"
    );

    private static final Set<String> ROUTING_STOPWORDS = Set.of(
            "de", "do", "da", "dos", "das",
            "para", "por", "com", "sem",
            "que", "qual", "quais",
            "uma", "um", "uns", "umas",
            "me", "te", "se", "nos", "voc", "voce", "voces",
            "preciso", "quero", "gostaria", "ajuda", "ol", "ola", "oi", "bom", "boa", "dia", "tarde", "noite",
            "fazer", "feito", "faco", "site", "vim", "vindo", "vinda"
    );

    private static final Map<String, Double> HIGH_SIGNAL_TOKEN_BOOSTS = Map.ofEntries(
            Map.entry("orcamento", 2.8d),
            Map.entry("proposta", 2.6d),
            Map.entry("contratar", 2.9d),
            Map.entry("contratacao", 2.9d),
            Map.entry("servico", 2.0d),
            Map.entry("plan", 1.8d),
            Map.entry("preco", 2.1d),
            Map.entry("valor", 1.9d),
            Map.entry("comercial", 2.5d),
            Map.entry("fatura", 2.4d),
            Map.entry("boleto", 2.6d),
            Map.entry("nota", 1.4d),
            Map.entry("suporte", 2.6d),
            Map.entry("erro", 2.0d),
            Map.entry("acesso", 2.2d),
            Map.entry("integracao", 2.4d),
            Map.entry("cancelamento", 2.8d),
            Map.entry("reclamacao", 2.5d)
    );

    private final AiSupervisorRepositoryJpa supervisorRepository;
    private final AiSupervisorAgentRuleRepositoryJpa ruleRepository;
    private final AiSupervisorCompanyConfigRepositoryJpa companyConfigRepository;
    private final AiAgentCompanyStateRepositoryJpa aiAgentStateRepository;
    private final AiSupervisorLlmClient llmClient;
    private final AiSupervisorDecisionParser decisionParser;
    private final AiSupervisorFeatureProperties featureProperties;
    private final AiSupervisorCandidateReducer candidateReducer;
    private final SensitiveDataCrypto crypto;
    private final String openAiApiKey;
    private final String defaultOpenAiModel;

    public AiSupervisorAdminService(
            AiSupervisorRepositoryJpa supervisorRepository,
            AiSupervisorAgentRuleRepositoryJpa ruleRepository,
            AiSupervisorCompanyConfigRepositoryJpa companyConfigRepository,
            AiAgentCompanyStateRepositoryJpa aiAgentStateRepository,
            AiSupervisorLlmClient llmClient,
            AiSupervisorDecisionParser decisionParser,
            AiSupervisorFeatureProperties featureProperties,
            AiSupervisorCandidateReducer candidateReducer,
            SensitiveDataCrypto crypto,
            @Value("${OPENAI_API_KEY:}") String openAiApiKey,
            @Value("${OPENAI_DEFAULT_MODEL:gpt-5-mini}") String defaultOpenAiModel
    ) {
        this.supervisorRepository = supervisorRepository;
        this.ruleRepository = ruleRepository;
        this.companyConfigRepository = companyConfigRepository;
        this.aiAgentStateRepository = aiAgentStateRepository;
        this.llmClient = llmClient;
        this.decisionParser = decisionParser;
        this.featureProperties = featureProperties;
        this.candidateReducer = candidateReducer;
        this.crypto = crypto;
        this.openAiApiKey = AiSupervisorSupport.safeTrim(openAiApiKey);
        this.defaultOpenAiModel = AiSupervisorSupport.safeTrim(defaultOpenAiModel);
    }

    @Transactional
    public AiSupervisorHttpResponse create(UUID companyId, AiSupervisorUpsertHttpRequest request) {
        Instant now = Instant.now();
        JpaAiSupervisorEntity entity = new JpaAiSupervisorEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompanyId(companyId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        applyUpsert(entity, request, now);
        supervisorRepository.saveAndFlush(entity);

        boolean defaultForCompany = shouldSetAsDefault(companyId, request);
        if (defaultForCompany) {
            upsertCompanyConfig(companyId, entity.getId(), true, now);
        }
        return toResponse(entity, defaultSupervisorId(companyId));
    }

    @Transactional
    public AiSupervisorHttpResponse update(UUID companyId, UUID supervisorId, AiSupervisorUpsertHttpRequest request) {
        JpaAiSupervisorEntity entity = requireSupervisor(companyId, supervisorId);
        Instant now = Instant.now();
        applyUpsert(entity, request, now);
        supervisorRepository.saveAndFlush(entity);

        if (Boolean.TRUE.equals(request.defaultForCompany())) {
            upsertCompanyConfig(companyId, entity.getId(), true, now);
        }
        return toResponse(entity, defaultSupervisorId(companyId));
    }

    @Transactional(readOnly = true)
    public AiSupervisorHttpResponse get(UUID companyId, UUID supervisorId) {
        return toResponse(requireSupervisor(companyId, supervisorId), defaultSupervisorId(companyId));
    }

    @Transactional(readOnly = true)
    public List<AiSupervisorHttpResponse> list(UUID companyId) {
        UUID defaultSupervisorId = defaultSupervisorId(companyId);
        return supervisorRepository.findAllByCompanyIdOrderByUpdatedAtDesc(companyId).stream()
                .map(item -> toResponse(item, defaultSupervisorId))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiSupervisorSimulateHttpResponse simulate(UUID companyId, UUID supervisorId, AiSupervisorSimulateHttpRequest request) {
        JpaAiSupervisorEntity supervisor = requireSupervisor(companyId, supervisorId);
        List<JpaAiSupervisorAgentRuleEntity> persistedRules = ruleRepository
                .findAllByCompanyIdAndSupervisorIdOrderByUpdatedAtDesc(companyId, supervisorId);
        Map<String, String> agentNames = loadAgentNames(companyId);

        List<SimulationRule> rules = resolveSimulationRules(request, persistedRules, agentNames);
        List<SimulationRule> enabledRules = rules.stream()
                .filter(SimulationRule::enabled)
                .filter(rule -> !rule.agentId().isBlank())
                .toList();

        if (enabledRules.isEmpty()) {
            if (supervisor.isHumanHandoffEnabled()) {
                return new AiSupervisorSimulateHttpResponse(
                        AiSupervisorAction.HANDOFF_HUMAN,
                        null,
                        null,
                        resolveSimulationHandoffMessage(supervisor),
                        0.31d,
                        "Nao ha agentes habilitados na distribuicao para simular atribuicao.",
                        AiSupervisorSupport.trimToNull(supervisor.getHumanHandoffTeam()),
                        List.of("sem candidatos habilitados")
                );
            }
            return new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.NO_ACTION,
                    null,
                    null,
                    null,
                    0.05d,
                    "Nenhum agente habilitado para simular roteamento.",
                    null,
                    List.of("sem candidatos habilitados")
            );
        }

        String leadMessage = AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request == null ? null : request.message()), 2000);
        if (leadMessage.isBlank()) {
            return askClarifyingResponse("Mensagem vazia para triagem.", supervisor, List.of("mensagem em branco"));
        }

        List<AiSupervisorCandidateReducer.CandidateRef> allCandidates = enabledRules.stream()
                .map(rule -> new AiSupervisorCandidateReducer.CandidateRef(
                        rule.agentId(),
                        rule.agentName(),
                        AiSupervisorSupport.truncate(rule.triageText(), featureProperties.maxCandidateTriageChars())
                ))
                .toList();
        List<AiSupervisorCandidateReducer.CandidateRef> reducedCandidates = candidateReducer.reduce(
                allCandidates,
                leadMessage,
                featureProperties.candidateReductionThreshold(),
                featureProperties.maxCandidatesAfterReduction()
        );

        AiSupervisorSimulateHttpResponse llmResult = trySimulateWithLlm(
                companyId,
                supervisor,
                request,
                leadMessage,
                reducedCandidates
        );
        if (llmResult != null) {
            return llmResult;
        }

        Set<String> reducedIds = reducedCandidates.stream()
                .map(AiSupervisorCandidateReducer.CandidateRef::agentId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        List<SimulationRule> reducedRules = enabledRules.stream()
                .filter(rule -> reducedIds.contains(rule.agentId()))
                .toList();
        List<SimulationRule> scoringRules = reducedRules.isEmpty() ? enabledRules : reducedRules;

        String normalizedMessage = AiSupervisorSupport.normalize(leadMessage);
        List<String> humanEvidence = HUMAN_HINT_KEYWORDS.stream()
                .filter(normalizedMessage::contains)
                .limit(3)
                .toList();
        if (supervisor.isHumanHandoffEnabled() && !humanEvidence.isEmpty()) {
            return new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.HANDOFF_HUMAN,
                    null,
                    null,
                    resolveSimulationHandoffMessage(supervisor),
                    0.88d,
                    "A mensagem indica necessidade de atendimento humano.",
                    AiSupervisorSupport.trimToNull(supervisor.getHumanHandoffTeam()),
                    sanitizeEvidence(humanEvidence)
            );
        }

        List<String> tokens = extractMeaningfulTokens(leadMessage);
        if (tokens.isEmpty()) {
            return askClarifyingResponse(
                    "Mensagem com pouco contexto util para decidir entre os agentes.",
                    supervisor,
                    List.of("contexto insuficiente")
            );
        }

        Map<String, Integer> tokenFrequency = buildRuleTokenFrequency(scoringRules);
        List<SimulationCandidateScore> scored = scoringRules.stream()
                .map(rule -> scoreRule(rule, normalizedMessage, tokens, tokenFrequency))
                .sorted(Comparator
                        .comparingDouble(SimulationCandidateScore::score).reversed()
                        .thenComparing(item -> item.rule().agentName()))
                .toList();

        SimulationCandidateScore best = scored.isEmpty() ? null : scored.get(0);
        SimulationCandidateScore second = scored.size() > 1 ? scored.get(1) : null;

        if (best == null || best.score() <= 0.0d) {
            return askClarifyingResponse("Nao houve aderencia suficiente para definir um agente.", supervisor, List.of("sem correspondencia por palavras-chave"));
        }

        boolean shouldAssign = shouldAssignCandidate(best, second, tokens.size());
        if (shouldAssign) {
            double confidence = calculateAssignmentConfidence(best, second, tokens.size());
            String reason = second == null
                    ? "Aderencia clara entre a mensagem e o agente selecionado."
                    : "O agente selecionado teve aderencia superior aos demais candidatos.";
            return new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.ASSIGN_AGENT,
                    best.rule().agentId(),
                    best.rule().agentName(),
                    null,
                    confidence,
                    AiSupervisorSupport.truncate(reason, 160),
                    null,
                    sanitizeEvidence(best.evidence())
            );
        }

        List<String> tieEvidence = new ArrayList<>();
        tieEvidence.add(best.rule().agentName());
        if (second != null) tieEvidence.add(second.rule().agentName());
        return askClarifyingResponse("Existe ambiguidade entre candidatos e e melhor perguntar antes de atribuir.", supervisor, tieEvidence);
    }

    @Transactional
    public AiSupervisorHttpResponse saveDistribution(UUID companyId, UUID supervisorId, AiSupervisorDistributionHttpRequest request) {
        JpaAiSupervisorEntity supervisor = requireSupervisor(companyId, supervisorId);
        Instant now = Instant.now();

        Map<String, String> agentNames = loadAgentNames(companyId);
        List<AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest> incoming =
                request == null || request.agents() == null ? List.of() : request.agents();

        LinkedHashMap<String, AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest> deduped = new LinkedHashMap<>();
        for (AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest item : incoming) {
            if (item == null) continue;
            String agentId = AiSupervisorSupport.safeTrim(item.agentId());
            if (agentId.isBlank()) continue;
            if (!agentNames.containsKey(agentId)) {
                throw new BusinessException("SUPERVISOR_AGENT_NOT_FOUND", "Agente informado nao existe: " + agentId);
            }
            deduped.put(agentId, item);
        }

        ruleRepository.deleteAllByCompanyIdAndSupervisorIdInBatch(companyId, supervisorId);

        List<JpaAiSupervisorAgentRuleEntity> rows = new ArrayList<>();
        for (Map.Entry<String, AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest> entry : deduped.entrySet()) {
            AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest item = entry.getValue();
            JpaAiSupervisorAgentRuleEntity row = new JpaAiSupervisorAgentRuleEntity();
            row.setId(UUID.randomUUID());
            row.setCompanyId(companyId);
            row.setSupervisorId(supervisorId);
            row.setAgentId(entry.getKey());
            row.setAgentNameSnapshot(agentNames.get(entry.getKey()));
            row.setEnabled(item.enabled() == null || item.enabled());
            row.setPriority(0);
            row.setTriageText(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(item.triageText()), 1200));
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            rows.add(row);
        }
        ruleRepository.saveAllAndFlush(rows);

        supervisor.setOtherRules(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request == null ? null : request.otherRules()), 2000));
        supervisor.setUpdatedAt(now);
        supervisorRepository.saveAndFlush(supervisor);
        return toResponse(supervisor, defaultSupervisorId(companyId));
    }

    private List<SimulationRule> resolveSimulationRules(
            AiSupervisorSimulateHttpRequest request,
            List<JpaAiSupervisorAgentRuleEntity> persistedRules,
            Map<String, String> agentNames
    ) {
        Map<String, JpaAiSupervisorAgentRuleEntity> persistedByAgentId = new LinkedHashMap<>();
        for (JpaAiSupervisorAgentRuleEntity row : persistedRules) {
            persistedByAgentId.put(AiSupervisorSupport.safeTrim(row.getAgentId()), row);
        }

        AiSupervisorDistributionHttpRequest override = request == null ? null : request.distribution();
        if (override != null) {
            LinkedHashMap<String, SimulationRule> overridden = new LinkedHashMap<>();
            List<AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest> overrideItems =
                    override.agents() == null ? List.of() : override.agents();
            for (AiSupervisorDistributionHttpRequest.AiSupervisorAgentDistributionItemHttpRequest item : overrideItems) {
                if (item == null) continue;
                String agentId = AiSupervisorSupport.safeTrim(item.agentId());
                if (agentId.isBlank()) continue;
                JpaAiSupervisorAgentRuleEntity persisted = persistedByAgentId.get(agentId);
                String agentName = AiSupervisorSupport.trimToNull(agentNames.get(agentId));
                if (agentName == null && persisted != null) {
                    agentName = AiSupervisorSupport.trimToNull(persisted.getAgentNameSnapshot());
                }
                if (agentName == null) agentName = agentId;
                overridden.put(agentId, new SimulationRule(
                        agentId,
                        agentName,
                        item.enabled() == null || item.enabled(),
                        AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(item.triageText()), 1200)
                ));
            }
            return List.copyOf(overridden.values());
        }

        return persistedRules.stream()
                .map(rule -> {
                    String agentId = AiSupervisorSupport.safeTrim(rule.getAgentId());
                    String agentName = AiSupervisorSupport.trimToNull(agentNames.get(agentId));
                    if (agentName == null) {
                        agentName = AiSupervisorSupport.trimToNull(rule.getAgentNameSnapshot());
                    }
                    if (agentName == null) agentName = agentId;
                    return new SimulationRule(
                            agentId,
                            agentName,
                            rule.isEnabled(),
                            AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(rule.getTriageText()), 1200)
                    );
                })
                .toList();
    }

    private AiSupervisorSimulateHttpResponse trySimulateWithLlm(
            UUID companyId,
            JpaAiSupervisorEntity supervisor,
            AiSupervisorSimulateHttpRequest request,
            String leadMessage,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) return null;

        String apiKey = resolveSimulationApiKey(companyId, supervisor.getProvider());
        if (apiKey.isBlank()) return null;

        String userPrompt;
        try {
            ObjectNode promptPayload = buildSimulationPromptPayload(supervisor, request, leadMessage, candidates);
            userPrompt = OBJECT_MAPPER.writeValueAsString(promptPayload);
        } catch (Exception ex) {
            return null;
        }

        try {
            AiSupervisorLlmClient.LlmResponse llmResponse = llmClient.classify(new AiSupervisorLlmClient.LlmRequest(
                    apiKey,
                    resolveModel(supervisor),
                    SYSTEM_PROMPT,
                    userPrompt,
                    featureProperties.maxOutputTokens()
            ));
            Set<String> candidateIds = candidates.stream()
                    .map(AiSupervisorCandidateReducer.CandidateRef::agentId)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);

            AiSupervisorDecisionParser.ParsedDecision parsed = decisionParser.parseStrict(
                    llmResponse.outputText(),
                    candidateIds,
                    featureProperties.maxClarifyingQuestionChars()
            );
            return toSimulationResponseFromLlm(supervisor, candidates, parsed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ObjectNode buildSimulationPromptPayload(
            JpaAiSupervisorEntity supervisor,
            AiSupervisorSimulateHttpRequest request,
            String leadMessage,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates
    ) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();

        ObjectNode supervisorNode = OBJECT_MAPPER.createObjectNode();
        supervisorNode.put("name", AiSupervisorSupport.truncate(supervisor.getName(), 180));
        supervisorNode.put("communicationStyle", AiSupervisorSupport.truncate(supervisor.getCommunicationStyle(), 280));
        supervisorNode.put("profile", AiSupervisorSupport.truncate(supervisor.getProfile(), 280));
        supervisorNode.put("objective", AiSupervisorSupport.truncate(supervisor.getObjective(), 280));
        root.set("supervisor", supervisorNode);

        ObjectNode contextNode = OBJECT_MAPPER.createObjectNode();
        contextNode.put("isFirstContact", true);
        contextNode.put("triageAskedAlready", false);
        ArrayNode messagesNode = OBJECT_MAPPER.createArrayNode();
        ObjectNode leadNode = OBJECT_MAPPER.createObjectNode();
        leadNode.put("role", "lead");
        leadNode.put("text", AiSupervisorSupport.truncate(AiSupervisorSupport.maskPii(leadMessage), featureProperties.maxMessageChars()));
        leadNode.put("ts", Instant.now().toString());
        messagesNode.add(leadNode);
        contextNode.set("lastMessages", messagesNode);
        root.set("context", contextNode);

        ArrayNode candidatesNode = OBJECT_MAPPER.createArrayNode();
        for (AiSupervisorCandidateReducer.CandidateRef candidate : candidates) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("agentId", candidate.agentId());
            node.put("agentName", AiSupervisorSupport.truncate(candidate.agentName(), 180));
            node.put("triageText", AiSupervisorSupport.truncate(candidate.triageText(), featureProperties.maxCandidateTriageChars()));
            candidatesNode.add(node);
        }
        root.set("candidates", candidatesNode);

        ObjectNode handoffNode = OBJECT_MAPPER.createObjectNode();
        handoffNode.put("enabled", supervisor.isHumanHandoffEnabled());
        handoffNode.put("userChoiceEnabled", supervisor.isHumanUserChoiceEnabled());
        ArrayNode optionsNode = OBJECT_MAPPER.createArrayNode();
        List<String> options = AiSupervisorSupport.parseStringArray(supervisor.getHumanChoiceOptionsJson());
        for (String option : options) {
            String normalized = AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(option), 80);
            if (!normalized.isBlank()) optionsNode.add(normalized);
        }
        handoffNode.set("options", optionsNode);
        root.set("humanHandoff", handoffNode);

        String otherRules = supervisor.getOtherRules();
        if (request != null && request.distribution() != null) {
            String overrideRules = AiSupervisorSupport.trimToNull(request.distribution().otherRules());
            if (overrideRules != null) {
                otherRules = overrideRules;
            }
        }

        ObjectNode constraintsNode = OBJECT_MAPPER.createObjectNode();
        constraintsNode.put("otherRules", AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(otherRules), featureProperties.maxOtherRulesChars()));
        constraintsNode.put("maxClarifyingQuestions", 1);
        root.set("constraints", constraintsNode);
        return root;
    }

    private AiSupervisorSimulateHttpResponse toSimulationResponseFromLlm(
            JpaAiSupervisorEntity supervisor,
            List<AiSupervisorCandidateReducer.CandidateRef> candidates,
            AiSupervisorDecisionParser.ParsedDecision parsed
    ) {
        Map<String, String> candidateNameById = new LinkedHashMap<>();
        for (AiSupervisorCandidateReducer.CandidateRef candidate : candidates) {
            candidateNameById.put(candidate.agentId(), candidate.agentName());
        }

        return switch (parsed.action()) {
            case ASSIGN_AGENT -> new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.ASSIGN_AGENT,
                    parsed.targetAgentId(),
                    candidateNameById.get(parsed.targetAgentId()),
                    null,
                    clamp(parsed.confidence()),
                    AiSupervisorSupport.truncate(parsed.reason(), 160),
                    null,
                    sanitizeEvidence(parsed.evidence())
            );
            case ASK_CLARIFYING -> new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.ASK_CLARIFYING,
                    null,
                    null,
                    AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(parsed.messageToSend()), featureProperties.maxClarifyingQuestionChars()),
                    clamp(parsed.confidence()),
                    AiSupervisorSupport.truncate(parsed.reason(), 160),
                    null,
                    sanitizeEvidence(parsed.evidence())
            );
            case HANDOFF_HUMAN -> {
                String queue = resolveHumanQueue(
                        parsed.humanQueue(),
                        AiSupervisorSupport.parseStringArray(supervisor.getHumanChoiceOptionsJson()),
                        supervisor.getHumanHandoffTeam()
                );
                yield new AiSupervisorSimulateHttpResponse(
                        AiSupervisorAction.HANDOFF_HUMAN,
                        null,
                        null,
                        resolveSimulationHandoffMessage(supervisor),
                        clamp(parsed.confidence()),
                        AiSupervisorSupport.truncate(parsed.reason(), 160),
                        queue,
                        sanitizeEvidence(parsed.evidence())
                );
            }
            case NO_ACTION -> new AiSupervisorSimulateHttpResponse(
                    AiSupervisorAction.NO_ACTION,
                    null,
                    null,
                    null,
                    clamp(parsed.confidence()),
                    AiSupervisorSupport.truncate(parsed.reason(), 160),
                    null,
                    sanitizeEvidence(parsed.evidence())
            );
        };
    }

    private String resolveSimulationApiKey(UUID companyId, String supervisorProvider) {
        if (!openAiApiKey.isBlank()) return openAiApiKey;

        JpaAiAgentCompanyStateEntity state = aiAgentStateRepository.findById(companyId).orElse(null);
        if (state == null) return "";

        String providerHint = AiSupervisorSupport.safeTrim(supervisorProvider).toLowerCase(Locale.ROOT);
        JsonNode providers = parseProviders(state);
        if (!providers.isArray()) return "";

        for (JsonNode provider : providers) {
            String id = AiSupervisorSupport.safeTrim(provider.path("id").asText("")).toLowerCase(Locale.ROOT);
            String type = AiSupervisorSupport.safeTrim(provider.path("type").asText("")).toLowerCase(Locale.ROOT);
            if (!providerHint.isBlank() && !providerHint.equals(id) && !providerHint.equals(type)) continue;
            String key = AiSupervisorSupport.safeTrim(provider.path("apiKey").asText(""));
            if (!key.isBlank()) return key;
        }

        for (JsonNode provider : providers) {
            String type = AiSupervisorSupport.safeTrim(provider.path("type").asText("")).toLowerCase(Locale.ROOT);
            if (!"openai".equals(type)) continue;
            String key = AiSupervisorSupport.safeTrim(provider.path("apiKey").asText(""));
            if (!key.isBlank()) return key;
        }

        return "";
    }

    private JsonNode parseProviders(JpaAiAgentCompanyStateEntity state) {
        if (state == null) return AiSupervisorSupport.parseJson("[]", "[]");
        try {
            return AiSupervisorSupport.parseJson(crypto.decrypt(state.getProvidersJson()), "[]");
        } catch (Exception ignored) {
            return AiSupervisorSupport.parseJson("[]", "[]");
        }
    }

    private String resolveModel(JpaAiSupervisorEntity supervisor) {
        String model = AiSupervisorSupport.trimToNull(supervisor.getModel());
        if (model != null) return model;
        return defaultOpenAiModel.isBlank() ? "gpt-5-mini" : defaultOpenAiModel;
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

    private SimulationCandidateScore scoreRule(
            SimulationRule rule,
            String normalizedMessage,
            List<String> tokens,
            Map<String, Integer> tokenFrequency
    ) {
        String normalizedName = AiSupervisorSupport.normalize(rule.agentName());
        String normalizedTriage = AiSupervisorSupport.normalize(rule.triageText());
        String searchable = (normalizedName + " " + normalizedTriage).trim();
        Set<String> candidateTokens = new LinkedHashSet<>(extractMeaningfulTokens(searchable));
        Set<String> candidateTokenRoots = candidateTokens.stream()
                .map(this::normalizeTokenForMatching)
                .filter(token -> !token.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        double score = 0.0d;
        List<String> evidence = new ArrayList<>();

        if (!normalizedMessage.isBlank() && searchable.contains(normalizedMessage)) {
            score += 2.4d;
            evidence.add("aderencia total");
        }

        for (String token : tokens) {
            String tokenRoot = normalizeTokenForMatching(token);
            if (!matchesCandidateToken(tokenRoot, candidateTokens, candidateTokenRoots, searchable)) continue;
            int frequency = Math.max(1, tokenFrequency.getOrDefault(tokenRoot, 1));
            double idfWeight = 2.2d / (double) frequency;
            double nameBoost = normalizedName.contains(tokenRoot) ? 0.45d : 0.0d;
            double highSignalBoost = HIGH_SIGNAL_TOKEN_BOOSTS.getOrDefault(tokenRoot, 0.0d);
            score += idfWeight + nameBoost + highSignalBoost;
            if (evidence.size() < 3) evidence.add(token);
        }

        return new SimulationCandidateScore(rule, score, sanitizeEvidence(evidence));
    }

    private List<String> extractMeaningfulTokens(String text) {
        return AiSupervisorSupport.tokenize(text).stream()
                .map(AiSupervisorSupport::safeTrim)
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> token.length() >= 3)
                .filter(token -> !ROUTING_STOPWORDS.contains(token))
                .toList();
    }

    private Map<String, Integer> buildRuleTokenFrequency(List<SimulationRule> rules) {
        Map<String, Integer> frequency = new HashMap<>();
        for (SimulationRule rule : rules) {
            Set<String> uniqueTokens = extractMeaningfulTokens(rule.agentName() + " " + rule.triageText()).stream()
                    .map(this::normalizeTokenForMatching)
                    .filter(token -> !token.isBlank())
                    .collect(LinkedHashSet::new, Set::add, Set::addAll);
            for (String token : uniqueTokens) {
                frequency.merge(token, 1, Integer::sum);
            }
        }
        return frequency;
    }

    private boolean matchesCandidateToken(
            String tokenRoot,
            Set<String> candidateTokens,
            Set<String> candidateTokenRoots,
            String searchable
    ) {
        if (tokenRoot.isBlank()) return false;
        if (candidateTokens.contains(tokenRoot) || candidateTokenRoots.contains(tokenRoot)) return true;
        if (tokenRoot.length() >= 5 && searchable.contains(tokenRoot)) return true;
        if (tokenRoot.length() < 4) return false;

        for (String candidateRoot : candidateTokenRoots) {
            if (candidateRoot.length() < 4) continue;
            if (candidateRoot.startsWith(tokenRoot) || tokenRoot.startsWith(candidateRoot)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTokenForMatching(String token) {
        String normalized = AiSupervisorSupport.safeTrim(token).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        if (normalized.endsWith("coes")) normalized = normalized.substring(0, normalized.length() - 4) + "cao";
        if (normalized.endsWith("oes")) normalized = normalized.substring(0, normalized.length() - 3) + "ao";
        if (normalized.endsWith("ais") && normalized.length() > 5) normalized = normalized.substring(0, normalized.length() - 3) + "al";
        if (normalized.endsWith("is") && normalized.length() > 4) normalized = normalized.substring(0, normalized.length() - 2) + "l";
        if (normalized.endsWith("s") && normalized.length() > 4) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private boolean shouldAssignCandidate(SimulationCandidateScore best, SimulationCandidateScore second, int tokenCount) {
        if (best == null) return false;
        if (best.score() <= 0.0d) return false;
        if (second == null) return best.score() >= 0.95d;

        double secondScore = Math.max(0.0001d, second.score());
        double ratio = best.score() / secondScore;
        double delta = best.score() - second.score();

        if (best.score() >= 2.0d && ratio >= 1.35d) return true;
        if (delta >= 1.25d) return true;
        if (tokenCount <= 2) return ratio >= 1.55d && best.score() >= 1.2d;
        return ratio >= 1.45d;
    }

    private double calculateAssignmentConfidence(SimulationCandidateScore best, SimulationCandidateScore second, int tokenCount) {
        if (best == null) return 0.0d;
        double normalizedBest = Math.min(1.0d, best.score() / Math.max(1.0d, tokenCount * 1.8d));
        double ratioBoost = 0.0d;
        if (second != null && second.score() > 0.0d) {
            double ratio = best.score() / second.score();
            ratioBoost = Math.min(0.18d, Math.max(0.0d, (ratio - 1.0d) * 0.12d));
        } else if (second == null) {
            ratioBoost = 0.12d;
        }
        return clamp(0.58d + (normalizedBest * 0.24d) + ratioBoost);
    }

    private AiSupervisorSimulateHttpResponse askClarifyingResponse(
            String reason,
            JpaAiSupervisorEntity supervisor,
            List<String> evidence
    ) {
        return new AiSupervisorSimulateHttpResponse(
                AiSupervisorAction.ASK_CLARIFYING,
                null,
                null,
                buildClarifyingQuestion(supervisor),
                0.41d,
                AiSupervisorSupport.truncate(reason, 160),
                null,
                sanitizeEvidence(evidence)
        );
    }

    private List<String> sanitizeEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) return List.of();
        List<String> sanitized = new ArrayList<>();
        for (String item : evidence) {
            String value = AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(item), 80);
            if (value.isBlank()) continue;
            sanitized.add(value);
            if (sanitized.size() >= 3) break;
        }
        return List.copyOf(sanitized);
    }

    private String buildClarifyingQuestion(JpaAiSupervisorEntity supervisor) {
        List<String> options = AiSupervisorSupport.parseStringArray(supervisor.getHumanChoiceOptionsJson()).stream()
                .map(option -> AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(option), 30))
                .filter(option -> !option.isBlank())
                .limit(3)
                .toList();

        if (supervisor.isHumanUserChoiceEnabled() && !options.isEmpty()) {
            StringBuilder sb = new StringBuilder("Para eu te direcionar melhor, voce busca ");
            for (int i = 0; i < options.size(); i++) {
                if (i > 0) sb.append(i == options.size() - 1 ? ", ou " : ", ");
                sb.append("(").append(i + 1).append(") ").append(options.get(i));
            }
            sb.append("?");
            return AiSupervisorSupport.truncate(sb.toString(), 220);
        }

        return "Qual e o principal objetivo do seu contato hoje?";
    }

    private String resolveSimulationHandoffMessage(JpaAiSupervisorEntity supervisor) {
        if (!supervisor.isHumanHandoffSendMessage()) return null;
        String configured = AiSupervisorSupport.trimToNull(supervisor.getHumanHandoffMessage());
        if (configured != null) {
            return AiSupervisorSupport.truncate(configured, 500);
        }
        return "Estamos transferindo seu atendimento para um de nossos especialistas, por favor aguarde!";
    }

    private double clamp(double value) {
        if (value < 0.0d) return 0.0d;
        if (value > 1.0d) return 1.0d;
        return value;
    }

    private void applyUpsert(JpaAiSupervisorEntity entity, AiSupervisorUpsertHttpRequest request, Instant now) {
        String name = AiSupervisorSupport.trimToNull(request == null ? null : request.name());
        if (name == null) {
            throw new BusinessException("SUPERVISOR_NAME_REQUIRED", "Nome do supervisor e obrigatorio");
        }
        entity.setName(AiSupervisorSupport.truncate(name, 180));
        entity.setCommunicationStyle(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.communicationStyle()), 2000));
        entity.setProfile(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.profile()), 2000));
        entity.setObjective(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.objective()), 2000));
        entity.setReasoningModelVersion(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.reasoningModelVersion()), 80));
        entity.setProvider(resolveProvider(request.provider()));
        entity.setModel(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.model()), 120));
        entity.setHumanHandoffEnabled(request.humanHandoffEnabled() != null && request.humanHandoffEnabled());
        String humanHandoffTeam = AiSupervisorSupport.trimToNull(request.humanHandoffTeam());
        if (humanHandoffTeam == null) {
            humanHandoffTeam = AiSupervisorSupport.trimToNull(entity.getHumanHandoffTeam());
        }
        if (humanHandoffTeam == null) {
            humanHandoffTeam = "Geral";
        }
        String agentIssueHandoffTeam = AiSupervisorSupport.trimToNull(request.agentIssueHandoffTeam());
        if (agentIssueHandoffTeam == null) {
            agentIssueHandoffTeam = humanHandoffTeam;
        }
        entity.setNotifyContactOnAgentTransfer(request.notifyContactOnAgentTransfer() != null && request.notifyContactOnAgentTransfer());
        entity.setHumanHandoffTeam(AiSupervisorSupport.truncate(humanHandoffTeam, 120));
        entity.setHumanHandoffSendMessage(request.humanHandoffSendMessage() != null && request.humanHandoffSendMessage());
        entity.setHumanHandoffMessage(AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(request.humanHandoffMessage()), 500));
        entity.setAgentIssueHandoffTeam(AiSupervisorSupport.truncate(agentIssueHandoffTeam, 120));
        entity.setAgentIssueSendMessage(request.agentIssueSendMessage() != null && request.agentIssueSendMessage());
        entity.setHumanUserChoiceEnabled(request.humanUserChoiceEnabled() != null && request.humanUserChoiceEnabled());
        entity.setHumanChoiceOptionsJson(AiSupervisorSupport.toJsonArray(sanitizeOptions(request.humanChoiceOptions())));
        entity.setEnabled(request.enabled() == null || request.enabled());
        if (AiSupervisorSupport.trimToNull(entity.getOtherRules()) == null) {
            entity.setOtherRules("");
        }
        entity.setUpdatedAt(now);
    }

    private JpaAiSupervisorEntity requireSupervisor(UUID companyId, UUID supervisorId) {
        return supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)
                .orElseThrow(() -> new BusinessException("SUPERVISOR_NOT_FOUND", "Supervisor nao encontrado"));
    }

    private AiSupervisorHttpResponse toResponse(JpaAiSupervisorEntity entity, UUID defaultSupervisorId) {
        List<JpaAiSupervisorAgentRuleEntity> rules = ruleRepository
                .findAllByCompanyIdAndSupervisorIdOrderByUpdatedAtDesc(entity.getCompanyId(), entity.getId());
        List<AiSupervisorHttpResponse.AgentRuleHttpResponse> agents = rules.stream()
                .sorted(Comparator.comparing(rule -> AiSupervisorSupport.safeTrim(rule.getAgentNameSnapshot())))
                .map(rule -> new AiSupervisorHttpResponse.AgentRuleHttpResponse(
                        rule.getAgentId(),
                        AiSupervisorSupport.safeTrim(rule.getAgentNameSnapshot()),
                        rule.isEnabled(),
                        AiSupervisorSupport.safeTrim(rule.getTriageText()),
                        rule.getUpdatedAt()
                ))
                .toList();
        return new AiSupervisorHttpResponse(
                entity.getId(),
                entity.getName(),
                entity.getCommunicationStyle(),
                entity.getProfile(),
                entity.getObjective(),
                entity.getReasoningModelVersion(),
                entity.getProvider(),
                entity.getModel(),
                entity.isHumanHandoffEnabled(),
                entity.isNotifyContactOnAgentTransfer(),
                AiSupervisorSupport.safeTrim(entity.getHumanHandoffTeam()),
                entity.isHumanHandoffSendMessage(),
                AiSupervisorSupport.safeTrim(entity.getHumanHandoffMessage()),
                AiSupervisorSupport.safeTrim(entity.getAgentIssueHandoffTeam()),
                entity.isAgentIssueSendMessage(),
                entity.isHumanUserChoiceEnabled(),
                AiSupervisorSupport.parseStringArray(entity.getHumanChoiceOptionsJson()),
                entity.isEnabled(),
                entity.getId().equals(defaultSupervisorId),
                new AiSupervisorHttpResponse.DistributionHttpResponse(entity.getOtherRules(), agents),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private Map<String, String> loadAgentNames(UUID companyId) {
        Map<String, String> names = new LinkedHashMap<>();
        var state = aiAgentStateRepository.findById(companyId).orElse(null);
        if (state == null) return names;
        JsonNode agentsNode = AiSupervisorSupport.parseJson(state.getAgentsJson(), "[]");
        if (!agentsNode.isArray()) return names;
        for (JsonNode agent : agentsNode) {
            String id = AiSupervisorSupport.safeTrim(agent.path("id").asText(""));
            String name = AiSupervisorSupport.safeTrim(agent.path("name").asText(""));
            if (!id.isBlank()) {
                names.put(id, name.isBlank() ? id : name);
            }
        }
        return names;
    }

    private List<String> sanitizeOptions(List<String> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : raw) {
            String normalized = AiSupervisorSupport.truncate(AiSupervisorSupport.safeTrim(item), 80);
            if (!normalized.isBlank()) values.add(normalized);
        }
        return List.copyOf(values);
    }

    private String resolveProvider(String rawProvider) {
        String provider = AiSupervisorSupport.safeTrim(rawProvider).toLowerCase(Locale.ROOT);
        return provider.isBlank() ? "openai" : AiSupervisorSupport.truncate(provider, 40);
    }

    private boolean shouldSetAsDefault(UUID companyId, AiSupervisorUpsertHttpRequest request) {
        if (Boolean.TRUE.equals(request.defaultForCompany())) return true;
        return defaultSupervisorId(companyId) == null;
    }

    private UUID defaultSupervisorId(UUID companyId) {
        return companyConfigRepository.findById(companyId)
                .map(JpaAiSupervisorCompanyConfigEntity::getDefaultSupervisorId)
                .orElse(null);
    }

    private void upsertCompanyConfig(UUID companyId, UUID supervisorId, boolean enabled, Instant now) {
        JpaAiSupervisorCompanyConfigEntity config = companyConfigRepository.findById(companyId).orElseGet(() -> {
            JpaAiSupervisorCompanyConfigEntity created = new JpaAiSupervisorCompanyConfigEntity();
            created.setCompanyId(companyId);
            created.setCreatedAt(now);
            return created;
        });
        config.setDefaultSupervisorId(supervisorId);
        config.setSupervisorEnabled(enabled);
        config.setUpdatedAt(now);
        companyConfigRepository.saveAndFlush(config);
    }

    private record SimulationRule(
            String agentId,
            String agentName,
            boolean enabled,
            String triageText
    ) {
    }

    private record SimulationCandidateScore(
            SimulationRule rule,
            double score,
            List<String> evidence
    ) {
    }
}

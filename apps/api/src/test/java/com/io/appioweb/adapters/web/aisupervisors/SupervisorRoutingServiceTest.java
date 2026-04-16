package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorAgentRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorCompanyConfigRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorConversationStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorDecisionLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.AiSupervisorRepositoryJpa;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorAgentRuleEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorConversationStateEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorDecisionLogEntity;
import com.io.appioweb.adapters.persistence.aisupervisors.JpaAiSupervisorEntity;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoConversationEntity;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoMessageEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import com.io.appioweb.adapters.web.atendimentos.AtendimentoAutomationMessageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorRoutingServiceTest {

    private final AiSupervisorRepositoryJpa supervisorRepository = mock(AiSupervisorRepositoryJpa.class);
    private final AiSupervisorAgentRuleRepositoryJpa ruleRepository = mock(AiSupervisorAgentRuleRepositoryJpa.class);
    private final AiSupervisorConversationStateRepositoryJpa stateRepository = mock(AiSupervisorConversationStateRepositoryJpa.class);
    private final AiSupervisorDecisionLogRepositoryJpa decisionLogRepository = mock(AiSupervisorDecisionLogRepositoryJpa.class);
    private final AiSupervisorCompanyConfigRepositoryJpa companyConfigRepository = mock(AiSupervisorCompanyConfigRepositoryJpa.class);
    private final AtendimentoConversationRepositoryJpa conversationRepository = mock(AtendimentoConversationRepositoryJpa.class);
    private final AtendimentoMessageRepositoryJpa messageRepository = mock(AtendimentoMessageRepositoryJpa.class);
    private final AiAgentCompanyStateRepositoryJpa aiAgentStateRepository = mock(AiAgentCompanyStateRepositoryJpa.class);
    private final SensitiveDataCrypto crypto = mock(SensitiveDataCrypto.class);
    private final AiSupervisorLlmClient llmClient = mock(AiSupervisorLlmClient.class);
    private final AtendimentoAutomationMessageService automationMessageService = mock(AtendimentoAutomationMessageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private SupervisorRoutingService service;
    private UUID companyId;
    private UUID supervisorId;
    private UUID conversationId;
    private UUID inboundMessageId;
    private UUID outboundQuestionId;
    private AtomicReference<JpaAtendimentoConversationEntity> conversationRef;
    private AtomicReference<JpaAiSupervisorConversationStateEntity> stateRef;
    private AtomicReference<JpaAiSupervisorDecisionLogEntity> logRef;
    private JpaAiSupervisorEntity supervisor;
    private JpaAtendimentoMessageEntity inboundMessage;
    private JpaAiSupervisorAgentRuleEntity rule;

    @BeforeEach
    void setUp() {
        AiSupervisorFeatureProperties feature = new AiSupervisorFeatureProperties(
                true,
                "",
                20,
                15,
                3,
                280,
                260,
                700,
                220,
                10,
                8,
                180,
                1
        );
        service = new SupervisorRoutingService(
                supervisorRepository,
                ruleRepository,
                stateRepository,
                decisionLogRepository,
                companyConfigRepository,
                conversationRepository,
                messageRepository,
                aiAgentStateRepository,
                crypto,
                llmClient,
                new AiSupervisorCandidateReducer(),
                new AiSupervisorDecisionParser(),
                feature,
                new NoOpTransactionManager(),
                eventPublisher,
                automationMessageService,
                new SimpleMeterRegistry(),
                "test-openai-key",
                "gpt-5-mini"
        );

        companyId = UUID.randomUUID();
        supervisorId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        inboundMessageId = UUID.randomUUID();
        outboundQuestionId = UUID.randomUUID();

        supervisor = supervisor(false, false, List.of());
        inboundMessage = inboundMessage(conversationId, inboundMessageId, false, "Preciso de suporte tecnico");
        rule = rule("agent-support", "Agente Suporte", 10);

        conversationRef = new AtomicReference<>(conversation());
        stateRef = new AtomicReference<>(null);
        logRef = new AtomicReference<>(null);

        when(supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)).thenReturn(Optional.of(supervisor));
        when(ruleRepository.findAllByCompanyIdAndSupervisorIdAndEnabledTrueOrderByUpdatedAtDesc(companyId, supervisorId))
                .thenReturn(List.of(rule));
        when(companyConfigRepository.findById(companyId)).thenReturn(Optional.empty());
        when(conversationRepository.findByIdAndCompanyId(conversationId, companyId))
                .thenAnswer(invocation -> Optional.ofNullable(copyConversation(conversationRef.get())));
        when(messageRepository.findByIdAndCompanyId(inboundMessageId, companyId))
                .thenReturn(Optional.of(inboundMessage));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(conversationId, companyId))
                .thenReturn(List.of(inboundMessage));
        when(stateRepository.findByCompanyIdAndSupervisorIdAndConversationId(companyId, supervisorId, conversationId))
                .thenAnswer(invocation -> Optional.ofNullable(stateRef.get()));
        when(stateRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JpaAiSupervisorConversationStateEntity saved = invocation.getArgument(0);
            stateRef.set(saved);
            return saved;
        });
        when(conversationRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JpaAtendimentoConversationEntity saved = copyConversation(invocation.getArgument(0));
            conversationRef.set(saved);
            return saved;
        });
        when(decisionLogRepository.findFirstByCompanyIdAndSupervisorIdAndConversationIdAndEvaluationKeyOrderByCreatedAtDesc(
                eq(companyId), eq(supervisorId), eq(conversationId), anyString()
        )).thenAnswer(invocation -> Optional.ofNullable(logRef.get()));
        when(decisionLogRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            JpaAiSupervisorDecisionLogEntity saved = invocation.getArgument(0);
            logRef.set(saved);
            return saved;
        });
        when(aiAgentStateRepository.findById(companyId)).thenReturn(Optional.empty());
    }

    @Test
    void gatingSkipsWhenConversationAlreadyHasAssignedAgentAndDoesNotCallLlm() {
        conversationRef.get().setAssignedAgentId("agent-existing");

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.NO_ACTION, result.action());
        assertEquals("conversation_already_assigned", result.reason());
        verify(llmClient, never()).classify(any());
    }

    @Test
    void triageAskedStateNeverAsksAgainAndFallsBackToAssign() {
        supervisor = supervisor(false, false, List.of());
        when(supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)).thenReturn(Optional.of(supervisor));

        UUID questionId = UUID.randomUUID();
        JpaAiSupervisorConversationStateEntity state = new JpaAiSupervisorConversationStateEntity();
        state.setId(UUID.randomUUID());
        state.setCompanyId(companyId);
        state.setSupervisorId(supervisorId);
        state.setConversationId(conversationId);
        state.setCreatedAt(Instant.parse("2026-03-06T10:00:00Z"));
        state.setUpdatedAt(Instant.parse("2026-03-06T10:00:00Z"));
        state.setTriageAsked(true);
        state.setLastSupervisorQuestionMessageId(questionId);
        stateRef.set(state);

        JpaAtendimentoMessageEntity question = inboundMessage(conversationId, questionId, true, "Voce precisa de suporte ou comercial?");
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(conversationId, companyId))
                .thenReturn(List.of(question, inboundMessage));
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-ask-again",
                "{\"action\":\"ASK_CLARIFYING\",\"targetAgentId\":null,\"messageToSend\":\"Pode detalhar melhor?\",\"humanQueue\":null,\"confidence\":0.4,\"reason\":\"ainda incerto\"}",
                10,
                5
        ));

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.ASSIGN_AGENT, result.action());
        assertEquals("agent-support", result.targetAgentId());
        verify(automationMessageService, never()).sendAutomaticText(any(), anyString(), anyString(), any(Integer.class));
    }

    @Test
    void idempotencyPreventsRepeatingSideEffectsForSameEvaluationKey() {
        supervisor = supervisor(true, false, List.of());
        when(supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)).thenReturn(Optional.of(supervisor));
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-ask",
                "{\"action\":\"ASK_CLARIFYING\",\"targetAgentId\":null,\"messageToSend\":\"Qual e o assunto principal?\",\"humanQueue\":null,\"confidence\":0.6,\"reason\":\"precisa de triagem\"}",
                10,
                6
        ));
        when(automationMessageService.sendAutomaticText(companyId, conversationRef.get().getPhone(), "Qual e o assunto principal?", 1))
                .thenReturn(new AtendimentoAutomationMessageService.SentMessageResult(conversationId, outboundQuestionId, "zapi-1"));

        var first = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);
        var second = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.ASK_CLARIFYING, first.action());
        assertEquals(AiSupervisorAction.ASK_CLARIFYING, second.action());
        assertTrue(second.duplicate());
        verify(automationMessageService).sendAutomaticText(companyId, conversationRef.get().getPhone(), "Qual e o assunto principal?", 1);
    }

    @Test
    void assignRouteUpdatesConversationAssignedAgentAndCreatesAuditLog() {
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-assign",
                "{\"action\":\"ASSIGN_AGENT\",\"targetAgentId\":\"agent-support\",\"messageToSend\":null,\"humanQueue\":null,\"confidence\":0.95,\"reason\":\"suporte tecnico\"}",
                12,
                7
        ));

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.ASSIGN_AGENT, result.action());
        assertEquals("agent-support", conversationRef.get().getAssignedAgentId());
        assertNotNull(logRef.get());
        assertEquals("ASSIGN_AGENT", logRef.get().getAction());
    }

    @Test
    void askRouteSendsMessageAndMarksState() {
        supervisor = supervisor(true, false, List.of());
        when(supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)).thenReturn(Optional.of(supervisor));
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-ask",
                "{\"action\":\"ASK_CLARIFYING\",\"targetAgentId\":null,\"messageToSend\":\"Qual area voce procura?\",\"humanQueue\":null,\"confidence\":0.61,\"reason\":\"duvida de intencao\"}",
                10,
                6
        ));
        when(automationMessageService.sendAutomaticText(companyId, conversationRef.get().getPhone(), "Qual area voce procura?", 1))
                .thenReturn(new AtendimentoAutomationMessageService.SentMessageResult(conversationId, outboundQuestionId, "zapi-2"));

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.ASK_CLARIFYING, result.action());
        assertTrue(stateRef.get().isTriageAsked());
        assertEquals(outboundQuestionId, stateRef.get().getLastSupervisorQuestionMessageId());
    }

    @Test
    void handoffRouteFlagsConversationAndCreatesAuditLog() {
        supervisor = supervisor(true, true, List.of("Financeiro", "Suporte"));
        when(supervisorRepository.findByIdAndCompanyId(supervisorId, companyId)).thenReturn(Optional.of(supervisor));
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-handoff",
                "{\"action\":\"HANDOFF_HUMAN\",\"targetAgentId\":null,\"messageToSend\":\"Vou encaminhar voce para um atendente humano.\",\"humanQueue\":\"Financeiro\",\"confidence\":0.74,\"reason\":\"demanda financeira\"}",
                14,
                8
        ));
        when(automationMessageService.sendAutomaticText(companyId, conversationRef.get().getPhone(), "Vou encaminhar voce para um atendente humano.", 1))
                .thenReturn(new AtendimentoAutomationMessageService.SentMessageResult(conversationId, outboundQuestionId, "zapi-3"));

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.HANDOFF_HUMAN, result.action());
        assertTrue(conversationRef.get().isHumanHandoffRequested());
        assertEquals("Financeiro", conversationRef.get().getHumanHandoffQueue());
        assertEquals("HANDOFF_HUMAN", logRef.get().getAction());
    }

    @Test
    void retriesOnceAfterOptimisticConflictAndReevaluates() {
        AtomicInteger stateSaveCount = new AtomicInteger();
        when(stateRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            if (stateSaveCount.getAndIncrement() == 0) {
                conversationRef.set(conversation());
                throw new OptimisticLockingFailureException("conflict");
            }
            JpaAiSupervisorConversationStateEntity saved = invocation.getArgument(0);
            stateRef.set(saved);
            return saved;
        });
        when(llmClient.classify(any())).thenReturn(new AiSupervisorLlmClient.LlmResponse(
                "req-assign-retry",
                "{\"action\":\"ASSIGN_AGENT\",\"targetAgentId\":\"agent-support\",\"messageToSend\":null,\"humanQueue\":null,\"confidence\":0.91,\"reason\":\"suporte tecnico\"}",
                12,
                7
        ));

        var result = service.routeOrTriageLead(companyId, supervisorId, conversationId, inboundMessageId);

        assertEquals(AiSupervisorAction.ASSIGN_AGENT, result.action());
        assertEquals("agent-support", conversationRef.get().getAssignedAgentId());
        assertTrue(stateSaveCount.get() >= 2);
    }

    private JpaAiSupervisorEntity supervisor(boolean humanEnabled, boolean userChoiceEnabled, List<String> options) {
        JpaAiSupervisorEntity entity = new JpaAiSupervisorEntity();
        entity.setId(supervisorId);
        entity.setCompanyId(companyId);
        entity.setName("Supervisor IA");
        entity.setCommunicationStyle("curto");
        entity.setProfile("triagem");
        entity.setObjective("direcionar leads");
        entity.setReasoningModelVersion("v1");
        entity.setProvider("openai");
        entity.setModel("gpt-5-mini");
        entity.setOtherRules("");
        entity.setHumanHandoffEnabled(humanEnabled);
        entity.setHumanUserChoiceEnabled(userChoiceEnabled);
        entity.setHumanChoiceOptionsJson(AiSupervisorSupport.toJsonArray(options));
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.parse("2026-03-06T09:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-06T09:00:00Z"));
        return entity;
    }

    private JpaAiSupervisorAgentRuleEntity rule(String agentId, String agentName, int priority) {
        JpaAiSupervisorAgentRuleEntity entity = new JpaAiSupervisorAgentRuleEntity();
        entity.setId(UUID.randomUUID());
        entity.setCompanyId(companyId);
        entity.setSupervisorId(supervisorId);
        entity.setAgentId(agentId);
        entity.setAgentNameSnapshot(agentName);
        entity.setTriageText("Atende demandas de suporte tecnico e incidentes.");
        entity.setEnabled(true);
        entity.setPriority(priority);
        entity.setCreatedAt(Instant.parse("2026-03-06T09:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-06T09:00:00Z"));
        return entity;
    }

    private JpaAtendimentoConversationEntity conversation() {
        JpaAtendimentoConversationEntity entity = new JpaAtendimentoConversationEntity();
        entity.setId(conversationId);
        entity.setCompanyId(companyId);
        entity.setPhone("5511999999999");
        entity.setDisplayName("Lead");
        entity.setStatus("NEW");
        entity.setHumanChoiceOptionsJson("[]");
        entity.setCreatedAt(Instant.parse("2026-03-06T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-06T10:00:00Z"));
        return entity;
    }

    private JpaAtendimentoMessageEntity inboundMessage(UUID localConversationId, UUID messageId, boolean fromMe, String text) {
        JpaAtendimentoMessageEntity entity = new JpaAtendimentoMessageEntity();
        entity.setId(messageId);
        entity.setConversationId(localConversationId);
        entity.setCompanyId(companyId);
        entity.setPhone("5511999999999");
        entity.setMessageText(text);
        entity.setMessageType("text");
        entity.setFromMe(fromMe);
        entity.setCreatedAt(Instant.parse("2026-03-06T10:01:00Z"));
        return entity;
    }

    private JpaAtendimentoConversationEntity copyConversation(JpaAtendimentoConversationEntity source) {
        if (source == null) return null;
        JpaAtendimentoConversationEntity entity = new JpaAtendimentoConversationEntity();
        entity.setId(source.getId());
        entity.setCompanyId(source.getCompanyId());
        entity.setPhone(source.getPhone());
        entity.setDisplayName(source.getDisplayName());
        entity.setStatus(source.getStatus());
        entity.setAssignedUserId(source.getAssignedUserId());
        entity.setAssignedUserName(source.getAssignedUserName());
        entity.setAssignedAgentId(source.getAssignedAgentId());
        entity.setHumanHandoffRequested(source.isHumanHandoffRequested());
        entity.setHumanHandoffQueue(source.getHumanHandoffQueue());
        entity.setHumanHandoffRequestedAt(source.getHumanHandoffRequestedAt());
        entity.setHumanUserChoiceRequired(source.isHumanUserChoiceRequired());
        entity.setHumanChoiceOptionsJson(source.getHumanChoiceOptionsJson());
        entity.setLastMessageText(source.getLastMessageText());
        entity.setLastMessageAt(source.getLastMessageAt());
        entity.setCreatedAt(source.getCreatedAt());
        entity.setUpdatedAt(source.getUpdatedAt());
        return entity;
    }

    private static class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}

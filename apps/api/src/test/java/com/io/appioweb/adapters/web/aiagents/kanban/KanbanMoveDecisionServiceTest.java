package com.io.appioweb.adapters.web.aiagents.kanban;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanMoveAttemptRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanMoveDecision;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentRunLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentStageRuleRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentKanbanMoveAttemptEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentKanbanStateEntity;
import com.io.appioweb.adapters.persistence.aiagents.JpaAiAgentStageRuleEntity;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoMessageEntity;
import com.io.appioweb.adapters.persistence.crm.CrmCompanyStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.crm.JpaCrmCompanyStateEntity;
import com.io.appioweb.adapters.security.SensitiveDataCrypto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KanbanMoveDecisionServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CrmCompanyStateRepositoryJpa crmStateRepository = mock(CrmCompanyStateRepositoryJpa.class);
    private final AtendimentoMessageRepositoryJpa messageRepository = mock(AtendimentoMessageRepositoryJpa.class);
    private final AiAgentStageRuleRepositoryJpa stageRuleRepository = mock(AiAgentStageRuleRepositoryJpa.class);
    private final AiAgentKanbanMoveAttemptRepositoryJpa attemptRepository = mock(AiAgentKanbanMoveAttemptRepositoryJpa.class);
    private final AiAgentKanbanStateRepositoryJpa stateRepository = mock(AiAgentKanbanStateRepositoryJpa.class);
    private final AiAgentRunLogRepositoryJpa runLogRepository = mock(AiAgentRunLogRepositoryJpa.class);
    private final AiAgentCompanyStateRepositoryJpa aiAgentStateRepository = mock(AiAgentCompanyStateRepositoryJpa.class);
    private final SensitiveDataCrypto crypto = mock(SensitiveDataCrypto.class);
    private final AiKanbanMoveLlmClient llmClient = mock(AiKanbanMoveLlmClient.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private KanbanMoveDecisionService service;
    private UUID companyId;
    private UUID conversationId;
    private String agentId;
    private String cardId;

    @BeforeEach
    void setUp() {
        KanbanMoveFeatureProperties feature = new KanbanMoveFeatureProperties(
                true,
                "",
                true,
                6,
                240,
                20,
                true,
                4,
                180
        );
        service = new KanbanMoveDecisionService(
                crmStateRepository,
                messageRepository,
                stageRuleRepository,
                attemptRepository,
                stateRepository,
                runLogRepository,
                aiAgentStateRepository,
                crypto,
                llmClient,
                feature,
                new NoOpTransactionManager(),
                eventPublisher,
                "gpt-5-mini",
                "test-api-key"
        );

        companyId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        agentId = "agent-main";
        cardId = conversationId.toString();

        when(attemptRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stateRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runLogRepository.findTop10ByCompanyIdAndConversationIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(aiAgentStateRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void gatingSkipsWhenNoNewMessagesAndDoesNotCallLlm() {
        JpaAtendimentoMessageEntity latest = message(conversationId, companyId, false, "quero fechar", Instant.parse("2026-03-05T10:00:00Z"));
        JpaAiAgentKanbanStateEntity state = new JpaAiAgentKanbanStateEntity();
        state.setCompanyId(companyId);
        state.setAgentId(agentId);
        state.setConversationId(conversationId);
        state.setCardId(cardId);
        state.setLastEvaluatedMessageId(latest.getId());
        state.setLastEvaluatedMessageAt(latest.getCreatedAt());
        state.setLastDecisionAt(Instant.parse("2026-03-05T09:59:00Z"));

        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(crmState(cardId, "stage_new")));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(latest));
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(rule(companyId, agentId, "stage_qualified", true, "Mover quando qualificar", 10, null, "[]")));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.of(state));

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("NO_MOVE", result.decision());
        assertEquals("no_new_messages", result.reason());
        verify(llmClient, never()).classify(any());
    }

    @Test
    void candidateSelectionUsesCurrentNextTwoAndSpecialStages() throws Exception {
        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(crmState(cardId, "stage_qualified")));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(message(conversationId, companyId, false, "quero avancar com a proposta", Instant.parse("2026-03-05T10:10:00Z"))));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.empty());
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(
                        rule(companyId, agentId, "stage_new", true, "prompt new", 5, null, "[]"),
                        rule(companyId, agentId, "stage_qualified", true, "prompt qual", 5, null, "[]"),
                        rule(companyId, agentId, "stage_proposal", true, "prompt prop", 5, null, "[]"),
                        rule(companyId, agentId, "stage_meeting", true, "prompt meeting", 5, null, "[]"),
                        rule(companyId, agentId, "stage_lost", true, "prompt lost", 5, null, "[]")
                ));

        ArgumentCaptor<AiKanbanMoveLlmClient.LlmRequest> requestCaptor = ArgumentCaptor.forClass(AiKanbanMoveLlmClient.LlmRequest.class);
        when(llmClient.classify(any())).thenReturn(
                new AiKanbanMoveLlmClient.LlmResponse("req-1", "{\"move\":false,\"targetStageId\":null,\"confidence\":0.2,\"reason\":\"sem mudanca\"}", 20, 10)
        );

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("NO_MOVE", result.decision());
        verify(llmClient).classify(requestCaptor.capture());
        var payload = OBJECT_MAPPER.readTree(requestCaptor.getValue().userPrompt());
        var candidates = payload.path("candidates");
        List<String> candidateIds = List.of(
                candidates.get(0).path("id").asText(),
                candidates.get(1).path("id").asText(),
                candidates.get(2).path("id").asText(),
                candidates.get(3).path("id").asText()
        );
        assertFalse(candidateIds.contains("stage_new"));
        assertTrue(candidateIds.contains("stage_qualified"));
        assertTrue(candidateIds.contains("stage_proposal"));
        assertTrue(candidateIds.contains("stage_meeting"));
        assertTrue(candidateIds.contains("stage_lost"));
    }

    @Test
    void allowedFromRuleCanSkipAllCandidatesWithoutLlmCall() {
        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(crmState(cardId, "stage_qualified")));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(message(conversationId, companyId, false, "enviei proposta", Instant.parse("2026-03-05T10:20:00Z"))));
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(
                        rule(companyId, agentId, "stage_proposal", true, "move para proposta", 10, null, "[\"stage_new\"]")
                ));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.empty());

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("NO_MOVE", result.decision());
        assertEquals("no_candidate_stage", result.reason());
        verify(llmClient, never()).classify(any());
    }

    @Test
    void invalidJsonFromLlmReturnsErrorAndDoesNotMove() {
        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(crmState(cardId, "stage_new")));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(message(conversationId, companyId, false, "quero proposta", Instant.parse("2026-03-05T10:30:00Z"))));
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(rule(companyId, agentId, "stage_proposal", true, "move proposal", 10, null, "[]")));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.empty());
        when(llmClient.classify(any())).thenReturn(
                new AiKanbanMoveLlmClient.LlmResponse("req-2", "{\"move\":false,\"targetStageId\":null,\"confidence\":0.4,\"reason\":\"ok\"} lixo", 10, 5)
        );

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("ERROR", result.decision());
        assertEquals("KANBAN_LLM_JSON_PARSE_ERROR", result.errorCode());
        verify(crmStateRepository, never()).findByCompanyIdForUpdate(any());
    }

    @Test
    void appliesMoveAndCreatesAuditAttempt() {
        JpaCrmCompanyStateEntity state = crmState(cardId, "stage_new");
        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(state));
        when(crmStateRepository.findByCompanyIdForUpdate(companyId)).thenReturn(Optional.of(state));
        when(crmStateRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(message(conversationId, companyId, false, "quero receber a proposta", Instant.parse("2026-03-05T10:40:00Z"))));
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(rule(companyId, agentId, "stage_proposal", true, "proposal", 10, null, "[]")));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.empty());
        when(attemptRepository.existsByCompanyIdAndAgentIdAndConversationIdAndCardIdAndEvaluationKeyAndDecision(
                eq(companyId), eq(agentId), eq(conversationId), eq(cardId), anyString(), eq(AiAgentKanbanMoveDecision.MOVE)
        )).thenReturn(false);
        when(llmClient.classify(any())).thenReturn(
                new AiKanbanMoveLlmClient.LlmResponse("req-3", "{\"move\":true,\"targetStageId\":\"stage_proposal\",\"confidence\":0.93,\"reason\":\"proposta solicitada\"}", 15, 10)
        );

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("MOVE", result.decision());
        assertTrue(result.moved());
        assertEquals("stage_proposal", result.targetStageId());

        ArgumentCaptor<JpaCrmCompanyStateEntity> crmCaptor = ArgumentCaptor.forClass(JpaCrmCompanyStateEntity.class);
        verify(crmStateRepository).saveAndFlush(crmCaptor.capture());
        assertTrue(crmCaptor.getValue().getLeadStageMapJson().contains("stage_proposal"));

        ArgumentCaptor<JpaAiAgentKanbanMoveAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(JpaAiAgentKanbanMoveAttemptEntity.class);
        verify(attemptRepository).saveAndFlush(attemptCaptor.capture());
        assertEquals(AiAgentKanbanMoveDecision.MOVE, attemptCaptor.getValue().getDecision());
    }

    @Test
    void concurrentStageChangePreventsMove() {
        JpaCrmCompanyStateEntity initial = crmState(cardId, "stage_new");
        JpaCrmCompanyStateEntity locked = crmState(cardId, "stage_qualified");
        when(crmStateRepository.findById(companyId)).thenReturn(Optional.of(initial));
        when(crmStateRepository.findByCompanyIdForUpdate(companyId)).thenReturn(Optional.of(locked));
        when(messageRepository.findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(eq(conversationId), eq(companyId), any()))
                .thenReturn(List.of(message(conversationId, companyId, false, "enviar proposta", Instant.parse("2026-03-05T10:50:00Z"))));
        when(stageRuleRepository.findAllByCompanyIdAndAgentIdOrderByPriorityDescUpdatedAtDesc(companyId, agentId))
                .thenReturn(List.of(rule(companyId, agentId, "stage_proposal", true, "proposal", 10, null, "[]")));
        when(stateRepository.findByCompanyIdAndAgentIdAndConversationIdAndCardId(companyId, agentId, conversationId, cardId))
                .thenReturn(Optional.empty());
        when(attemptRepository.existsByCompanyIdAndAgentIdAndConversationIdAndCardIdAndEvaluationKeyAndDecision(
                eq(companyId), eq(agentId), eq(conversationId), eq(cardId), anyString(), eq(AiAgentKanbanMoveDecision.MOVE)
        )).thenReturn(false);
        when(llmClient.classify(any())).thenReturn(
                new AiKanbanMoveLlmClient.LlmResponse("req-4", "{\"move\":true,\"targetStageId\":\"stage_proposal\",\"confidence\":0.85,\"reason\":\"proposta solicitada\"}", 15, 10)
        );

        var result = service.evaluateAndMaybeMoveCard(companyId, conversationId, cardId, agentId);

        assertEquals("NO_MOVE", result.decision());
        assertEquals("KANBAN_CONCURRENT_STAGE_CHANGED", result.errorCode());
        verify(crmStateRepository, never()).saveAndFlush(eq(locked));
    }

    private JpaCrmCompanyStateEntity crmState(String localCardId, String stageId) {
        JpaCrmCompanyStateEntity row = new JpaCrmCompanyStateEntity();
        row.setCompanyId(companyId);
        row.setStagesJson("""
                [
                  {"id":"stage_new","title":"Novo","kind":"initial","order":0},
                  {"id":"stage_qualified","title":"Qualificado","kind":"intermediate","order":1},
                  {"id":"stage_proposal","title":"Proposta","kind":"intermediate","order":2},
                  {"id":"stage_meeting","title":"Agendado","kind":"intermediate","order":3},
                  {"id":"stage_lost","title":"Perdido","kind":"final","order":4}
                ]
                """);
        row.setLeadStageMapJson("{\"" + localCardId + "\":\"" + stageId + "\"}");
        row.setCustomFieldsJson("[]");
        row.setLeadFieldValuesJson("{}");
        row.setLeadFieldsOrderJson("[]");
        row.setCreatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        return row;
    }

    private JpaAiAgentStageRuleEntity rule(
            UUID localCompanyId,
            String localAgentId,
            String stageId,
            boolean enabled,
            String prompt,
            Integer priority,
            Boolean onlyForwardOverride,
            String allowedFromJson
    ) {
        JpaAiAgentStageRuleEntity row = new JpaAiAgentStageRuleEntity();
        row.setId(UUID.randomUUID());
        row.setCompanyId(localCompanyId);
        row.setAgentId(localAgentId);
        row.setStageId(stageId);
        row.setEnabled(enabled);
        row.setPrompt(prompt);
        row.setPriority(priority);
        row.setOnlyForwardOverride(onlyForwardOverride);
        row.setAllowedFromStagesJson(allowedFromJson);
        row.setCreatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        row.setUpdatedAt(Instant.parse("2026-03-05T00:00:00Z"));
        return row;
    }

    private JpaAtendimentoMessageEntity message(
            UUID localConversationId,
            UUID localCompanyId,
            boolean fromMe,
            String text,
            Instant createdAt
    ) {
        JpaAtendimentoMessageEntity row = new JpaAtendimentoMessageEntity();
        row.setId(UUID.randomUUID());
        row.setConversationId(localConversationId);
        row.setCompanyId(localCompanyId);
        row.setPhone("5511999999999");
        row.setMessageText(text);
        row.setMessageType("text");
        row.setFromMe(fromMe);
        row.setCreatedAt(createdAt);
        return row;
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

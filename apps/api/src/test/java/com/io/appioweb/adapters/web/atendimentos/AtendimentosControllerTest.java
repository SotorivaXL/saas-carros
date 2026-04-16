package com.io.appioweb.adapters.web.atendimentos;

import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanMoveAttemptRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentKanbanStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.aiagents.AiAgentRunLogRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.googlecalendar.AiAgentCalendarSuggestionStateRepositoryJpa;
import com.io.appioweb.adapters.web.aiagents.AiAgentOrchestrationService;
import com.io.appioweb.adapters.web.aiagents.kanban.KanbanMoveDecisionService;
import com.io.appioweb.adapters.web.aisupervisors.SupervisorRoutingService;
import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import com.io.appioweb.application.auth.port.out.CurrentUserPort;
import com.io.appioweb.application.auth.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AtendimentosControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private CompanyRepositoryPort companies;

    @Mock
    private CurrentUserPort currentUser;

    @Mock
    private UserRepositoryPort users;

    @Mock
    private AiAgentRunLogRepositoryJpa aiAgentRunLogs;

    @Mock
    private AiAgentKanbanMoveAttemptRepositoryJpa aiAgentKanbanMoveAttempts;

    @Mock
    private AiAgentKanbanStateRepositoryJpa aiAgentKanbanStates;

    @Mock
    private AiAgentCalendarSuggestionStateRepositoryJpa aiAgentCalendarSuggestionStates;

    @Mock
    private AtendimentoConversationRepositoryJpa conversations;

    @Mock
    private AtendimentoMessageRepositoryJpa messages;

    @Mock
    private AtendimentoSessionLifecycleService sessionLifecycleService;

    @Mock
    private AiAgentOrchestrationService aiAgentOrchestration;

    @Mock
    private KanbanMoveDecisionService kanbanMoveDecisionService;

    @Mock
    private SupervisorRoutingService supervisorRoutingService;

    @InjectMocks
    private AtendimentosController controller;

    @Test
    void receiveWebhookReturnsGoneWhenWhatsappChannelIsRemoved() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(
                """
                {
                  "phone": "5511999999999",
                  "messageId": "msg-1",
                  "fromMe": true
                }
                """
        );

        ResponseEntity<Void> response = controller.receiveWebhook("instance-1", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void receiveWebhookIgnoresLegacyPayloadsWhenWhatsappChannelIsRemoved() throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(
                """
                {
                  "phone": "5511999999999",
                  "messageId": "msg-2",
                  "fromMe": false
                }
                """
        );

        ResponseEntity<Void> response = controller.receiveWebhook("instance-1", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
    }
}

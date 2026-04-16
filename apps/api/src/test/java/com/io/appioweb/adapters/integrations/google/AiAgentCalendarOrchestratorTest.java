package com.io.appioweb.adapters.integrations.google;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiAgentCalendarOrchestratorTest {

    @Test
    void consultationReturnsThreeSuggestedSlots() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        when(calendarClient.getConnectionStatus(any())).thenReturn(connectedStatus());
        when(schedulingService.loadActiveSuggestion(any(), any())).thenReturn(null);
        when(schedulingService.suggestSlots(any(), any(), any(), anyInt(), anyString())).thenReturn(new SchedulingSuggestionResult(
                "America/Sao_Paulo",
                List.of(
                        new SchedulingSlot(Instant.parse("2026-03-05T13:00:00Z"), Instant.parse("2026-03-05T13:30:00Z"), "05/03 10:00", 1),
                        new SchedulingSlot(Instant.parse("2026-03-05T13:30:00Z"), Instant.parse("2026-03-05T14:00:00Z"), "05/03 10:30", 2),
                        new SchedulingSlot(Instant.parse("2026-03-05T14:00:00Z"), Instant.parse("2026-03-05T14:30:00Z"), "05/03 11:00", 3)
                ),
                Instant.parse("2026-03-04T12:00:00Z"),
                Instant.parse("2026-03-04T12:30:00Z")
        ));

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Quais horarios voce tem amanha?",
                "Maria"
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("1) 05/03 10:00"));
        assertTrue(response.finalText().contains("2) 05/03 10:30"));
        assertTrue(response.finalText().contains("3) 05/03 11:00"));
        verify(schedulingService).suggestSlots(any(), any(), any(), eq(30), eq("America/Sao_Paulo"));
    }

    @Test
    void mixedQuestionWithPodeSerStillFetchesRealAvailability() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        when(calendarClient.getConnectionStatus(any())).thenReturn(connectedStatus());
        when(schedulingService.loadActiveSuggestion(any(), any())).thenReturn(null);
        when(schedulingService.suggestSlots(any(), any(), any(), anyInt(), anyString())).thenReturn(new SchedulingSuggestionResult(
                "America/Sao_Paulo",
                List.of(
                        new SchedulingSlot(Instant.parse("2026-03-05T16:00:00Z"), Instant.parse("2026-03-05T16:30:00Z"), "05/03 13:00", 1),
                        new SchedulingSlot(Instant.parse("2026-03-05T16:30:00Z"), Instant.parse("2026-03-05T17:00:00Z"), "05/03 13:30", 2),
                        new SchedulingSlot(Instant.parse("2026-03-05T17:00:00Z"), Instant.parse("2026-03-05T17:30:00Z"), "05/03 14:00", 3)
                ),
                Instant.parse("2026-03-04T12:00:00Z"),
                Instant.parse("2026-03-04T12:30:00Z")
        ));

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Pode ser amanha a tarde? Que horario voces tem disponivel?",
                "Maria",
                true
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("1) 05/03 13:00"));
        assertFalse(response.finalText().contains("expiraram"));
        verify(schedulingService).suggestSlots(any(), any(), any(), eq(30), eq("America/Sao_Paulo"));
    }

    @Test
    void confirmationOfFirstOptionCreatesCalendarEvent() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        UUID companyId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(calendarClient.getConnectionStatus(companyId)).thenReturn(connectedStatus());
        SchedulingSlot firstSlot = new SchedulingSlot(
                Instant.parse("2026-03-05T13:00:00Z"),
                Instant.parse("2026-03-05T13:30:00Z"),
                "05/03 10:00",
                1
        );
        SchedulingService.StoredSuggestionState state = SchedulingService.StoredSuggestionState.of(
                "America/Sao_Paulo",
                List.of(firstSlot),
                Instant.parse("2026-03-04T12:00:00Z"),
                Instant.parse("2026-03-04T12:30:00Z"),
                SchedulingService.DesiredDateWindow.of(
                        Instant.parse("2026-03-05T00:00:00Z"),
                        Instant.parse("2026-03-06T00:00:00Z")
                ),
                30
        );
        when(schedulingService.loadActiveSuggestion(companyId, conversationId)).thenReturn(state);
        when(schedulingService.resolveSuggestedSlot(companyId, conversationId, "a primeira")).thenReturn(firstSlot);
        when(schedulingService.isSlotStillFree(companyId, firstSlot)).thenReturn(true);
        when(calendarClient.createEventWithMeet(eq(companyId), eq("primary"), anyString(), anyString(), eq(firstSlot.start()), eq(firstSlot.end()), eq("America/Sao_Paulo")))
                .thenReturn(new GoogleCalendarEventResult("evt-1", "https://calendar.google.com/event?1", "https://meet.google.com/abc-defg"));

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                companyId,
                conversationId,
                "a primeira",
                "Maria"
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("Agendado para"));
        assertTrue(response.finalText().contains("https://meet.google.com/abc-defg"));
        verify(calendarClient).createEventWithMeet(eq(companyId), eq("primary"), anyString(), anyString(), eq(firstSlot.start()), eq(firstSlot.end()), eq("America/Sao_Paulo"));
        verify(schedulingService).clearSuggestion(companyId, conversationId);
    }

    @Test
    void conflictDoesNotCreateEventAndReturnsAlternatives() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        UUID companyId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(calendarClient.getConnectionStatus(companyId)).thenReturn(connectedStatus());
        SchedulingSlot firstSlot = new SchedulingSlot(
                Instant.parse("2026-03-05T13:00:00Z"),
                Instant.parse("2026-03-05T13:30:00Z"),
                "05/03 10:00",
                1
        );
        SchedulingService.StoredSuggestionState state = SchedulingService.StoredSuggestionState.of(
                "America/Sao_Paulo",
                List.of(firstSlot),
                Instant.parse("2026-03-04T12:00:00Z"),
                Instant.parse("2026-03-04T12:30:00Z"),
                SchedulingService.DesiredDateWindow.of(
                        Instant.parse("2026-03-05T00:00:00Z"),
                        Instant.parse("2026-03-06T00:00:00Z")
                ),
                30
        );
        when(schedulingService.loadActiveSuggestion(companyId, conversationId)).thenReturn(state);
        when(schedulingService.resolveSuggestedSlot(companyId, conversationId, "a primeira")).thenReturn(firstSlot);
        when(schedulingService.isSlotStillFree(companyId, firstSlot)).thenReturn(false);
        when(schedulingService.suggestAlternatives(companyId, conversationId)).thenReturn(new SchedulingSuggestionResult(
                "America/Sao_Paulo",
                List.of(
                        new SchedulingSlot(Instant.parse("2026-03-05T14:00:00Z"), Instant.parse("2026-03-05T14:30:00Z"), "05/03 11:00", 1),
                        new SchedulingSlot(Instant.parse("2026-03-05T14:30:00Z"), Instant.parse("2026-03-05T15:00:00Z"), "05/03 11:30", 2)
                ),
                Instant.parse("2026-03-04T12:05:00Z"),
                Instant.parse("2026-03-04T12:35:00Z")
        ));

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                companyId,
                conversationId,
                "a primeira",
                "Maria"
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("indisponivel"));
        assertTrue(response.finalText().contains("1) 05/03 11:00"));
        verify(calendarClient, never()).createEventWithMeet(any(), anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void schedulingDisabledBlocksCalendarFlow() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Quais horarios voce tem amanha?",
                "Maria",
                false
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("nao esta habilitado"));
        verify(calendarClient, never()).getConnectionStatus(any());
        verify(schedulingService, never()).suggestSlots(any(), any(), any(), anyInt(), anyString());
    }

    @Test
    void disconnectedGoogleBlocksCalendarFlow() {
        SchedulingService schedulingService = mock(SchedulingService.class);
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        when(calendarClient.getConnectionStatus(any())).thenReturn(new GoogleOAuthConnectionResult(
                UUID.randomUUID(),
                "",
                "scope-a",
                com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus.DISCONNECTED,
                Instant.parse("2026-03-04T12:00:00Z")
        ));

        AiAgentCalendarOrchestrator orchestrator = new AiAgentCalendarOrchestrator(
                schedulingService,
                calendarClient,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        AiAgentCalendarOrchestrator.CalendarFlowResponse response = orchestrator.maybeHandleMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Quais horarios voce tem amanha?",
                "Maria",
                true
        );

        assertNotNull(response);
        assertTrue(response.finalText().contains("nao esta conectada"));
        verify(schedulingService, never()).suggestSlots(any(), any(), any(), anyInt(), anyString());
    }

    private static GoogleOAuthConnectionResult connectedStatus() {
        return new GoogleOAuthConnectionResult(
                UUID.randomUUID(),
                "owner@example.com",
                "scope-a",
                com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus.CONNECTED,
                Instant.parse("2026-03-04T12:00:00Z")
        );
    }
}

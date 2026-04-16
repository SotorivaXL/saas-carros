package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.persistence.auth.CompanyRepositoryJpa;
import com.io.appioweb.adapters.persistence.auth.JpaCompanyEntity;
import com.io.appioweb.adapters.persistence.googlecalendar.AiAgentCalendarSuggestionStateRepositoryJpa;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulingServiceTest {

    @Test
    void suggestSlotsUsesBusyWindowsToReturnRealFreeOptions() {
        GoogleCalendarClient calendarClient = mock(GoogleCalendarClient.class);
        CompanyRepositoryJpa companyRepository = mock(CompanyRepositoryJpa.class);
        AiAgentCalendarSuggestionStateRepositoryJpa suggestionRepository = mock(AiAgentCalendarSuggestionStateRepositoryJpa.class);

        UUID companyId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(companyWithBusinessHours()));
        when(calendarClient.freeBusy(eq(companyId), any(), any(), eq("primary"))).thenReturn(List.of(
                new GoogleCalendarBusyWindow(Instant.parse("2026-03-05T12:00:00Z"), Instant.parse("2026-03-05T12:30:00Z")),
                new GoogleCalendarBusyWindow(Instant.parse("2026-03-05T13:00:00Z"), Instant.parse("2026-03-05T13:30:00Z"))
        ));
        when(suggestionRepository.findByCompanyIdAndConversationId(companyId, conversationId)).thenReturn(Optional.empty());
        when(suggestionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingService service = new SchedulingService(
                calendarClient,
                companyRepository,
                suggestionRepository,
                Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC)
        );

        SchedulingSuggestionResult result = service.suggestSlots(
                companyId,
                conversationId,
                SchedulingService.DesiredDateWindow.of(
                        Instant.parse("2026-03-05T11:00:00Z"),
                        Instant.parse("2026-03-05T21:00:00Z")
                ),
                30,
                "America/Sao_Paulo"
        );

        assertEquals(3, result.slots().size());
        assertEquals(Instant.parse("2026-03-05T12:30:00Z"), result.slots().get(0).start());
        assertEquals(Instant.parse("2026-03-05T13:30:00Z"), result.slots().get(1).start());
        assertEquals(Instant.parse("2026-03-05T14:00:00Z"), result.slots().get(2).start());
    }

    private static JpaCompanyEntity companyWithBusinessHours() {
        JpaCompanyEntity company = new JpaCompanyEntity();
        company.setId(UUID.randomUUID());
        company.setName("Empresa");
        company.setEmail("empresa@test.local");
        company.setBusinessHoursStart("09:00");
        company.setBusinessHoursEnd("18:00");
        company.setBusinessHoursWeeklyJson("""
                {
                  "monday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "tuesday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "wednesday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "thursday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "friday":{"active":true,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "saturday":{"active":false,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"},
                  "sunday":{"active":false,"start":"09:00","lunchStart":"12:00","lunchEnd":"13:00","end":"18:00"}
                }
                """);
        return company;
    }
}

package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.persistence.googlecalendar.GoogleConnectionStatus;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiAgentCalendarOrchestrator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter CONFIRM_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm", new Locale("pt", "BR"));
    private static final Pattern DAY_MONTH_PATTERN = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{4}))?\\b");
    private static final Pattern DURATION_MIN_PATTERN = Pattern.compile("\\b(\\d{1,3})\\s*(?:min|minuto|minutos)\\b");
    private static final Pattern DURATION_HOUR_PATTERN = Pattern.compile("\\b(\\d{1,2})\\s*(?:h|hora|horas)\\b");

    private final SchedulingService schedulingService;
    private final GoogleCalendarClient calendarClient;
    private final Clock clock;

    @Autowired
    public AiAgentCalendarOrchestrator(
            SchedulingService schedulingService,
            GoogleCalendarClient calendarClient
    ) {
        this(schedulingService, calendarClient, Clock.systemUTC());
    }

    public AiAgentCalendarOrchestrator(
            SchedulingService schedulingService,
            GoogleCalendarClient calendarClient,
            Clock clock
    ) {
        this.schedulingService = schedulingService;
        this.calendarClient = calendarClient;
        this.clock = clock;
    }

    public CalendarFlowResponse maybeHandleMessage(UUID companyId, UUID conversationId, String customerMessage, String contactName) {
        return maybeHandleMessage(companyId, conversationId, customerMessage, contactName, true);
    }

    public CalendarFlowResponse maybeHandleMessage(
            UUID companyId,
            UUID conversationId,
            String customerMessage,
            String contactName,
            boolean schedulingEnabled
    ) {
        String normalized = normalize(customerMessage);
        if (normalized.isBlank()) return null;
        boolean availabilityQuery = isAvailabilityQuery(normalized);
        boolean strongConfirmation = isStrongConfirmationMessage(normalized);

        SchedulingService.StoredSuggestionState state = schedulingService.loadActiveSuggestion(companyId, conversationId);
        boolean schedulingRelated = state != null || availabilityQuery || strongConfirmation;
        if (!schedulingRelated) {
            return null;
        }
        if (!schedulingEnabled) {
            return buildResponse(
                    "Este agente nao esta habilitado para agendar reunioes. Ative a habilidade de agendamento nas configuracoes do agente para usar essa funcao.",
                    buildMeta("calendar_disabled", customerMessage),
                    buildMeta("calendar_disabled", "skill_missing")
            );
        }
        if (calendarClient.getConnectionStatus(companyId).status() != GoogleConnectionStatus.CONNECTED) {
            return buildResponse(
                    "A conta Google Calendar desta empresa nao esta conectada. Conecte o Google na aba de provedores antes de agendar reunioes.",
                    buildMeta("calendar_unavailable", customerMessage),
                    buildMeta("calendar_unavailable", "google_disconnected")
            );
        }

        if (state != null) {
            SchedulingSlot selected = schedulingService.resolveSuggestedSlot(companyId, conversationId, customerMessage);
            if (selected != null) {
                return confirmSuggestedSlot(companyId, conversationId, customerMessage, contactName, state, selected);
            }
            if (strongConfirmation && !availabilityQuery) {
                return buildResponse(
                        "Nao consegui identificar qual dos horarios sugeridos voce escolheu. Responda com 1, 2, 3 ou com o horario exato mostrado.",
                        buildMeta("calendar_confirm_unresolved", customerMessage),
                        buildMeta("calendar_confirm_unresolved", state.timeZone())
                );
            }
        }

        if (state == null && strongConfirmation && !availabilityQuery) {
            return buildResponse(
                    "Os horarios sugeridos anteriormente expiraram. Se quiser, eu posso consultar horarios reais de novo agora.",
                    buildMeta("calendar_confirm_expired", customerMessage),
                    buildMeta("calendar_confirm_expired", "expired")
            );
        }
        String timeZone = "America/Sao_Paulo";
        int durationMinutes = parseDurationMinutes(normalized);
        SchedulingService.DesiredDateWindow window = determineWindow(normalized, timeZone);

        try {
            SchedulingSuggestionResult suggestions = schedulingService.suggestSlots(companyId, conversationId, window, durationMinutes, timeZone);
            if (suggestions.slots().isEmpty()) {
                return buildResponse(
                        "Nao encontrei horarios livres reais nesse intervalo. Se quiser, me diga outro dia ou periodo para eu consultar de novo.",
                        buildMeta("calendar_suggest", customerMessage),
                        buildMeta("calendar_suggest_empty", suggestions.timeZone())
                );
            }
            return buildResponse(
                    formatSlotsMessage(suggestions.slots()),
                    buildSuggestionRequestMeta(customerMessage, window, durationMinutes, timeZone),
                    buildSuggestionResponseMeta(suggestions)
            );
        } catch (BusinessException ex) {
            return buildResponse(
                    ex.getMessage(),
                    buildMeta("calendar_suggest_error", customerMessage),
                    buildMeta("calendar_suggest_error", ex.getMessage())
            );
        }
    }

    private CalendarFlowResponse confirmSuggestedSlot(
            UUID companyId,
            UUID conversationId,
            String customerMessage,
            String contactName,
            SchedulingService.StoredSuggestionState state,
            SchedulingSlot selected
    ) {
        try {
            if (!schedulingService.isSlotStillFree(companyId, selected)) {
                SchedulingSuggestionResult alternatives = schedulingService.suggestAlternatives(companyId, conversationId);
                if (alternatives.slots().isEmpty()) {
                    return buildResponse(
                            "Esse horario acabou de ficar indisponivel e nao encontrei outra opcao livre no mesmo intervalo. Me diga outro periodo para eu consultar.",
                            buildMeta("calendar_conflict", customerMessage),
                            buildMeta("calendar_conflict", "no-alternatives")
                    );
                }
                return buildResponse(
                        "Esse horario acabou de ficar indisponivel. Aqui estao novas opcoes reais:\n" + formatSlotsList(alternatives.slots()),
                        buildMeta("calendar_conflict", customerMessage),
                        buildSuggestionResponseMeta(alternatives)
                );
            }

            GoogleCalendarEventResult event = calendarClient.createEventWithMeet(
                    companyId,
                    "primary",
                    buildSummary(contactName),
                    "Agendamento criado automaticamente para a conversa " + conversationId,
                    selected.start(),
                    selected.end(),
                    state.timeZone()
            );
            schedulingService.clearSuggestion(companyId, conversationId);
            String confirmation = "Agendado para " + CONFIRM_FORMATTER.format(selected.start().atZone(ZoneId.of(state.timeZone()))) + ".";
            if (event.meetLink() != null && !event.meetLink().isBlank()) {
                confirmation += " Link do Google Meet: " + event.meetLink();
            } else {
                confirmation += " O Google Calendar ainda nao retornou o link do Meet no tempo esperado.";
            }
            return buildResponse(
                    confirmation,
                    buildMeta("calendar_confirm", customerMessage),
                    buildEventResponseMeta(event, selected, state.timeZone())
            );
        } catch (BusinessException ex) {
            return buildResponse(
                    ex.getMessage(),
                    buildMeta("calendar_confirm_error", customerMessage),
                    buildMeta("calendar_confirm_error", ex.getMessage())
            );
        }
    }

    private SchedulingService.DesiredDateWindow determineWindow(String normalized, String timeZone) {
        ZoneId zone = ZoneId.of(timeZone);
        ZonedDateTime now = clock.instant().atZone(zone);
        LocalTime periodStart = LocalTime.MIN;
        LocalTime periodEnd = LocalTime.MAX.withSecond(59).withNano(0);
        if (normalized.contains("manha") || normalized.contains("manhã")) {
            periodStart = LocalTime.of(9, 0);
            periodEnd = LocalTime.of(12, 0);
        } else if (normalized.contains("tarde")) {
            periodStart = LocalTime.of(13, 0);
            periodEnd = LocalTime.of(18, 0);
        }

        LocalDate explicitDate = parseExplicitDate(normalized, now.toLocalDate());
        if (explicitDate == null && normalized.contains("amanha")) {
            explicitDate = now.toLocalDate().plusDays(1);
        }
        if (explicitDate == null && normalized.contains("amanhã")) {
            explicitDate = now.toLocalDate().plusDays(1);
        }
        if (explicitDate == null && normalized.contains("hoje")) {
            explicitDate = now.toLocalDate();
        }
        if (explicitDate == null) {
            explicitDate = parseNextWeekday(normalized, now.toLocalDate());
        }

        if (explicitDate != null) {
            ZonedDateTime start = explicitDate.atTime(periodStart).atZone(zone);
            ZonedDateTime end = explicitDate.atTime(periodEnd).atZone(zone);
            if (explicitDate.equals(now.toLocalDate()) && start.isBefore(now)) {
                start = now;
            }
            return new SchedulingService.DesiredDateWindow(start.toInstant(), end.toInstant());
        }

        Instant start = roundUp(now.toInstant(), zone);
        Instant end = start.plus(Duration.ofDays(3));
        return new SchedulingService.DesiredDateWindow(start, end);
    }

    private LocalDate parseExplicitDate(String normalized, LocalDate baseDate) {
        Matcher matcher = DAY_MONTH_PATTERN.matcher(normalized);
        if (!matcher.find()) return null;
        int day = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int year = matcher.group(3) == null ? baseDate.getYear() : Integer.parseInt(matcher.group(3));
        try {
            LocalDate parsed = LocalDate.of(year, month, day);
            if (parsed.isBefore(baseDate) && matcher.group(3) == null) {
                parsed = parsed.plusYears(1);
            }
            return parsed;
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parseNextWeekday(String normalized, LocalDate baseDate) {
        List<WeekdayToken> tokens = List.of(
                new WeekdayToken("segunda", DayOfWeek.MONDAY),
                new WeekdayToken("terca", DayOfWeek.TUESDAY),
                new WeekdayToken("terça", DayOfWeek.TUESDAY),
                new WeekdayToken("quarta", DayOfWeek.WEDNESDAY),
                new WeekdayToken("quinta", DayOfWeek.THURSDAY),
                new WeekdayToken("sexta", DayOfWeek.FRIDAY),
                new WeekdayToken("sabado", DayOfWeek.SATURDAY),
                new WeekdayToken("sábado", DayOfWeek.SATURDAY),
                new WeekdayToken("domingo", DayOfWeek.SUNDAY)
        );
        for (WeekdayToken token : tokens) {
            if (!normalized.contains(token.keyword())) continue;
            int delta = token.day().getValue() - baseDate.getDayOfWeek().getValue();
            if (delta <= 0) delta += 7;
            return baseDate.plusDays(delta);
        }
        return null;
    }

    private int parseDurationMinutes(String normalized) {
        Matcher minuteMatcher = DURATION_MIN_PATTERN.matcher(normalized);
        if (minuteMatcher.find()) {
            return Math.max(30, Integer.parseInt(minuteMatcher.group(1)));
        }
        Matcher hourMatcher = DURATION_HOUR_PATTERN.matcher(normalized);
        if (hourMatcher.find()) {
            return Math.max(30, Integer.parseInt(hourMatcher.group(1)) * 60);
        }
        return 30;
    }

    private boolean isAvailabilityQuery(String normalized) {
        return normalized.contains("horario")
                || normalized.contains("horários")
                || normalized.contains("agenda")
                || normalized.contains("agendar")
                || normalized.contains("agendamento")
                || normalized.contains("dispon")
                || normalized.contains("marcar")
                || normalized.contains("reuniao")
                || normalized.contains("reunião")
                || normalized.contains("meet");
    }

    private boolean isStrongConfirmationMessage(String normalized) {
        return normalized.contains("primeira")
                || normalized.contains("segunda")
                || normalized.contains("terceira")
                || normalized.contains("confirmo")
                || normalized.contains("fechado")
                || normalized.contains("pode ser")
                || normalized.contains("esse horario")
                || normalized.contains("esse horário")
                || normalized.contains("mesmo");
    }

    private String formatSlotsMessage(List<SchedulingSlot> slots) {
        return "Encontrei estes horarios reais:\n" + formatSlotsList(slots);
    }

    private String formatSlotsList(List<SchedulingSlot> slots) {
        StringBuilder sb = new StringBuilder();
        for (SchedulingSlot slot : slots) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(slot.index()).append(") ").append(slot.label());
        }
        return sb.toString();
    }

    private String buildSummary(String contactName) {
        String clean = contactName == null ? "" : contactName.trim();
        return clean.isBlank() ? "Reuniao com cliente" : "Reuniao com " + clean;
    }

    private Instant roundUp(Instant instant, ZoneId zone) {
        ZonedDateTime zoned = instant.atZone(zone).withSecond(0).withNano(0);
        int mod = zoned.getMinute() % 30;
        if (mod == 0) return zoned.toInstant();
        return zoned.plusMinutes(30 - mod).toInstant();
    }

    private CalendarFlowResponse buildResponse(String finalText, JsonNode requestMeta, JsonNode responseMeta) {
        return new CalendarFlowResponse(finalText, requestMeta, responseMeta);
    }

    private JsonNode buildSuggestionRequestMeta(String customerMessage, SchedulingService.DesiredDateWindow window, int durationMinutes, String timeZone) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("mode", "calendar_suggest");
        node.put("customerMessage", customerMessage == null ? "" : customerMessage);
        node.put("windowStart", window.start().toString());
        node.put("windowEnd", window.end().toString());
        node.put("durationMinutes", durationMinutes);
        node.put("timeZone", timeZone);
        return node;
    }

    private JsonNode buildSuggestionResponseMeta(SchedulingSuggestionResult suggestions) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("mode", "calendar_suggest");
        node.put("timeZone", suggestions.timeZone());
        node.put("generatedAt", suggestions.generatedAt().toString());
        node.put("expiresAt", suggestions.expiresAt().toString());
        var slots = OBJECT_MAPPER.createArrayNode();
        for (SchedulingSlot slot : suggestions.slots()) {
            ObjectNode item = OBJECT_MAPPER.createObjectNode();
            item.put("index", slot.index());
            item.put("label", slot.label());
            item.put("start", slot.start().toString());
            item.put("end", slot.end().toString());
            slots.add(item);
        }
        node.set("slots", slots);
        return node;
    }

    private JsonNode buildEventResponseMeta(GoogleCalendarEventResult event, SchedulingSlot slot, String timeZone) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("mode", "calendar_confirm");
        node.put("eventId", event.eventId());
        node.put("htmlLink", event.htmlLink());
        node.put("meetLink", event.meetLink());
        node.put("slotStart", slot.start().toString());
        node.put("slotEnd", slot.end().toString());
        node.put("timeZone", timeZone);
        return node;
    }

    private JsonNode buildMeta(String mode, String value) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("mode", mode);
        node.put("value", value == null ? "" : value);
        return node;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CalendarFlowResponse(String finalText, JsonNode requestMeta, JsonNode responseMeta) { }

    private record WeekdayToken(String keyword, DayOfWeek day) { }
}

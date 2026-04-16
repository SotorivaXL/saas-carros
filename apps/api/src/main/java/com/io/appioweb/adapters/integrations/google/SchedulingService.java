package com.io.appioweb.adapters.integrations.google;

import com.io.appioweb.adapters.persistence.auth.CompanyRepositoryJpa;
import com.io.appioweb.adapters.persistence.auth.JpaCompanyEntity;
import com.io.appioweb.adapters.persistence.googlecalendar.AiAgentCalendarSuggestionStateRepositoryJpa;
import com.io.appioweb.adapters.persistence.googlecalendar.JpaAiAgentCalendarSuggestionStateEntity;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SchedulingService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration SUGGESTION_TTL = Duration.ofMinutes(30);
    private static final Duration SLOT_INCREMENT = Duration.ofMinutes(30);
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm", new Locale("pt", "BR"));

    private final GoogleCalendarClient calendarClient;
    private final CompanyRepositoryJpa companies;
    private final AiAgentCalendarSuggestionStateRepositoryJpa suggestionStateRepository;
    private final Clock clock;

    @Autowired
    public SchedulingService(
            GoogleCalendarClient calendarClient,
            CompanyRepositoryJpa companies,
            AiAgentCalendarSuggestionStateRepositoryJpa suggestionStateRepository
    ) {
        this(calendarClient, companies, suggestionStateRepository, Clock.systemUTC());
    }

    public SchedulingService(
            GoogleCalendarClient calendarClient,
            CompanyRepositoryJpa companies,
            AiAgentCalendarSuggestionStateRepositoryJpa suggestionStateRepository,
            Clock clock
    ) {
        this.calendarClient = calendarClient;
        this.companies = companies;
        this.suggestionStateRepository = suggestionStateRepository;
        this.clock = clock;
    }

    @Transactional
    public SchedulingSuggestionResult suggestSlots(
            UUID companyId,
            UUID conversationId,
            DesiredDateWindow desiredDateWindow,
            int durationMinutes,
            String timeZone
    ) {
        if (companyId == null || conversationId == null) {
            throw new BusinessException("SCHEDULING_INVALID_CONTEXT", "Contexto invalido para calcular horarios");
        }
        ZoneId zone = resolveZone(timeZone);
        int resolvedDuration = Math.max(30, durationMinutes);
        if (desiredDateWindow == null || desiredDateWindow.start() == null || desiredDateWindow.end() == null || !desiredDateWindow.end().isAfter(desiredDateWindow.start())) {
            throw new BusinessException("SCHEDULING_INVALID_WINDOW", "Janela de disponibilidade invalida");
        }

        JpaCompanyEntity company = companies.findById(companyId)
                .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "Empresa nao encontrada"));

        List<GoogleCalendarBusyWindow> busyWindows = calendarClient.freeBusy(companyId, desiredDateWindow.start(), desiredDateWindow.end(), "primary");
        List<SchedulingSlot> slots = computeFreeSlots(company, busyWindows, desiredDateWindow, resolvedDuration, zone);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(SUGGESTION_TTL);
        persistSuggestionState(companyId, conversationId, zone.getId(), slots, desiredDateWindow, resolvedDuration, now, expiresAt);
        return new SchedulingSuggestionResult(zone.getId(), slots, now, expiresAt);
    }

    public StoredSuggestionState loadActiveSuggestion(UUID companyId, UUID conversationId) {
        if (companyId == null || conversationId == null) return null;
        JpaAiAgentCalendarSuggestionStateEntity entity = suggestionStateRepository.findByCompanyIdAndConversationId(companyId, conversationId).orElse(null);
        if (entity == null) return null;
        if (entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(clock.instant())) {
            return null;
        }
        return new StoredSuggestionState(
                entity.getTimezone(),
                parseSlots(entity.getSlotsJson()),
                entity.getGeneratedAt(),
                entity.getExpiresAt(),
                parseWindow(entity.getContextJson()),
                parseDurationMinutes(entity.getContextJson())
        );
    }

    public SchedulingSlot resolveSuggestedSlot(UUID companyId, UUID conversationId, String customerMessage) {
        StoredSuggestionState state = loadActiveSuggestion(companyId, conversationId);
        if (state == null || state.slots().isEmpty()) return null;

        Integer byIndex = parseChosenIndex(customerMessage);
        if (byIndex != null) {
            for (SchedulingSlot slot : state.slots()) {
                if (slot.index() == byIndex) return slot;
            }
        }

        LocalTime chosenTime = parseChosenTime(customerMessage);
        if (chosenTime != null) {
            ZoneId zone = resolveZone(state.timeZone());
            for (SchedulingSlot slot : state.slots()) {
                LocalTime slotTime = slot.start().atZone(zone).toLocalTime().withSecond(0).withNano(0);
                if (slotTime.equals(chosenTime)) return slot;
            }
        }
        return null;
    }

    public boolean isSlotStillFree(UUID companyId, SchedulingSlot slot) {
        List<GoogleCalendarBusyWindow> busyWindows = calendarClient.freeBusy(companyId, slot.start(), slot.end(), "primary");
        return busyWindows.stream().noneMatch(busy -> overlaps(slot.start(), slot.end(), busy.start(), busy.end()));
    }

    @Transactional
    public void clearSuggestion(UUID companyId, UUID conversationId) {
        suggestionStateRepository.findByCompanyIdAndConversationId(companyId, conversationId)
                .ifPresent(suggestionStateRepository::delete);
    }

    public SchedulingSuggestionResult suggestAlternatives(UUID companyId, UUID conversationId) {
        StoredSuggestionState state = loadActiveSuggestion(companyId, conversationId);
        if (state == null || state.desiredDateWindow() == null) {
            return new SchedulingSuggestionResult("America/Sao_Paulo", List.of(), clock.instant(), clock.instant().plus(SUGGESTION_TTL));
        }
        Instant newStart = state.desiredDateWindow().start();
        if (newStart.isBefore(clock.instant())) {
            newStart = clock.instant();
        }
        return suggestSlots(
                companyId,
                conversationId,
                new DesiredDateWindow(newStart, state.desiredDateWindow().end()),
                state.durationMinutes(),
                state.timeZone()
        );
    }

    private List<SchedulingSlot> computeFreeSlots(
            JpaCompanyEntity company,
            List<GoogleCalendarBusyWindow> busyWindows,
            DesiredDateWindow window,
            int durationMinutes,
            ZoneId zone
    ) {
        List<GoogleCalendarBusyWindow> sortedBusy = new ArrayList<>(busyWindows);
        sortedBusy.sort(Comparator.comparing(GoogleCalendarBusyWindow::start));
        Duration duration = Duration.ofMinutes(durationMinutes);
        List<SchedulingSlot> result = new ArrayList<>();

        ZonedDateTime cursorDate = window.start().atZone(zone).toLocalDate().atStartOfDay(zone);
        LocalDate endDate = window.end().atZone(zone).toLocalDate();
        while (!cursorDate.toLocalDate().isAfter(endDate) && result.size() < 3) {
            for (TimeRange range : resolveBusinessRanges(company, cursorDate.toLocalDate(), zone)) {
                Instant candidate = maxInstant(range.start(), window.start());
                candidate = roundUp(candidate, zone);
                while (!candidate.plus(duration).isAfter(range.end()) && !candidate.plus(duration).isAfter(window.end())) {
                    Instant candidateEnd = candidate.plus(duration);
                    if (candidate.isBefore(window.start())) {
                        candidate = candidate.plus(SLOT_INCREMENT);
                        continue;
                    }
                    if (!overlapsAnyBusy(sortedBusy, candidate, candidateEnd)) {
                        int index = result.size() + 1;
                        result.add(new SchedulingSlot(
                                candidate,
                                candidateEnd,
                                buildLabel(candidate, zone),
                                index
                        ));
                        if (result.size() >= 3) break;
                    }
                    candidate = candidate.plus(SLOT_INCREMENT);
                }
                if (result.size() >= 3) break;
            }
            cursorDate = cursorDate.plusDays(1);
        }
        return result;
    }

    private List<TimeRange> resolveBusinessRanges(JpaCompanyEntity company, LocalDate date, ZoneId zone) {
        JsonNode weekly = parseJson(company.getBusinessHoursWeeklyJson(), "{}");
        String dayKey = switch (date.getDayOfWeek()) {
            case MONDAY -> "monday";
            case TUESDAY -> "tuesday";
            case WEDNESDAY -> "wednesday";
            case THURSDAY -> "thursday";
            case FRIDAY -> "friday";
            case SATURDAY -> "saturday";
            case SUNDAY -> "sunday";
        };
        JsonNode day = weekly.path(dayKey);
        boolean active = day.path("active").asBoolean(isWeekday(date));
        if (!active) return List.of();

        String startRaw = trimOr(day.path("start").asText(""), trimOr(company.getBusinessHoursStart(), "09:00"));
        String lunchStartRaw = trimOr(day.path("lunchStart").asText(""), "12:00");
        String lunchEndRaw = trimOr(day.path("lunchEnd").asText(""), "13:00");
        String endRaw = trimOr(day.path("end").asText(""), trimOr(company.getBusinessHoursEnd(), "18:00"));

        LocalTime start = parseTimeValue(startRaw, LocalTime.of(9, 0));
        LocalTime lunchStart = parseTimeValue(lunchStartRaw, LocalTime.of(12, 0));
        LocalTime lunchEnd = parseTimeValue(lunchEndRaw, LocalTime.of(13, 0));
        LocalTime end = parseTimeValue(endRaw, LocalTime.of(18, 0));

        List<TimeRange> ranges = new ArrayList<>();
        if (start.isBefore(lunchStart)) {
            ranges.add(new TimeRange(date.atTime(start).atZone(zone).toInstant(), date.atTime(lunchStart).atZone(zone).toInstant()));
        }
        if (lunchEnd.isBefore(end)) {
            ranges.add(new TimeRange(date.atTime(lunchEnd).atZone(zone).toInstant(), date.atTime(end).atZone(zone).toInstant()));
        }
        if (ranges.isEmpty() && start.isBefore(end)) {
            ranges.add(new TimeRange(date.atTime(start).atZone(zone).toInstant(), date.atTime(end).atZone(zone).toInstant()));
        }
        return ranges;
    }

    private void persistSuggestionState(
            UUID companyId,
            UUID conversationId,
            String timeZone,
            List<SchedulingSlot> slots,
            DesiredDateWindow desiredDateWindow,
            int durationMinutes,
            Instant generatedAt,
            Instant expiresAt
    ) {
        JpaAiAgentCalendarSuggestionStateEntity entity = suggestionStateRepository.findByCompanyIdAndConversationId(companyId, conversationId)
                .orElseGet(JpaAiAgentCalendarSuggestionStateEntity::new);
        if (entity.getId() == null) entity.setId(UUID.randomUUID());
        entity.setCompanyId(companyId);
        entity.setConversationId(conversationId);
        entity.setTimezone(timeZone);
        entity.setSlotsJson(toSlotsJson(slots));
        entity.setContextJson(toContextJson(desiredDateWindow, durationMinutes));
        entity.setGeneratedAt(generatedAt);
        entity.setExpiresAt(expiresAt);
        entity.setUpdatedAt(generatedAt);
        suggestionStateRepository.saveAndFlush(entity);
    }

    private String toSlotsJson(List<SchedulingSlot> slots) {
        ArrayNode array = OBJECT_MAPPER.createArrayNode();
        for (SchedulingSlot slot : slots) {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("startIso", slot.start().toString());
            node.put("endIso", slot.end().toString());
            node.put("label", slot.label());
            node.put("index", slot.index());
            array.add(node);
        }
        return toJson(array);
    }

    private String toContextJson(DesiredDateWindow window, int durationMinutes) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("windowStart", window.start().toString());
        node.put("windowEnd", window.end().toString());
        node.put("durationMinutes", durationMinutes);
        return toJson(node);
    }

    private List<SchedulingSlot> parseSlots(String raw) {
        JsonNode array = parseJson(raw, "[]");
        List<SchedulingSlot> slots = new ArrayList<>();
        if (!array.isArray()) return slots;
        for (JsonNode node : array) {
            Instant start = parseInstant(node.path("startIso").asText(""));
            Instant end = parseInstant(node.path("endIso").asText(""));
            if (start == null || end == null || !end.isAfter(start)) continue;
            slots.add(new SchedulingSlot(
                    start,
                    end,
                    trimOr(node.path("label").asText(""), buildLabel(start, resolveZone("America/Sao_Paulo"))),
                    Math.max(1, node.path("index").asInt(slots.size() + 1))
            ));
        }
        slots.sort(Comparator.comparing(SchedulingSlot::index));
        return slots;
    }

    private DesiredDateWindow parseWindow(String raw) {
        JsonNode node = parseJson(raw, "{}");
        Instant start = parseInstant(node.path("windowStart").asText(""));
        Instant end = parseInstant(node.path("windowEnd").asText(""));
        if (start == null || end == null || !end.isAfter(start)) return null;
        return new DesiredDateWindow(start, end);
    }

    private int parseDurationMinutes(String raw) {
        JsonNode node = parseJson(raw, "{}");
        return Math.max(30, node.path("durationMinutes").asInt(30));
    }

    private Integer parseChosenIndex(String customerMessage) {
        String normalized = normalize(customerMessage);
        if (normalized.contains("primeira") || normalized.contains("primeiro") || normalized.matches(".*\\b1\\b.*")) return 1;
        if (normalized.contains("segunda") || normalized.contains("segundo") || normalized.matches(".*\\b2\\b.*")) return 2;
        if (normalized.contains("terceira") || normalized.contains("terceiro") || normalized.matches(".*\\b3\\b.*")) return 3;
        return null;
    }

    private LocalTime parseChosenTime(String customerMessage) {
        String normalized = normalize(customerMessage);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{1,2})(?::(\\d{2}))?\\s*h?\\b").matcher(normalized);
        if (!matcher.find()) return null;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }

    private ZoneId resolveZone(String timeZone) {
        String raw = trimOr(timeZone, "America/Sao_Paulo");
        try {
            return ZoneId.of(raw);
        } catch (Exception ex) {
            return ZoneId.of("America/Sao_Paulo");
        }
    }

    private Instant roundUp(Instant instant, ZoneId zone) {
        ZonedDateTime zoned = instant.atZone(zone).withSecond(0).withNano(0);
        int minute = zoned.getMinute();
        int mod = minute % 30;
        if (mod == 0) return zoned.toInstant();
        return zoned.plusMinutes(30 - mod).toInstant();
    }

    private boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private boolean overlapsAnyBusy(List<GoogleCalendarBusyWindow> busyWindows, Instant start, Instant end) {
        for (GoogleCalendarBusyWindow busy : busyWindows) {
            if (overlaps(start, end, busy.start(), busy.end())) return true;
        }
        return false;
    }

    private Instant maxInstant(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private String buildLabel(Instant instant, ZoneId zone) {
        return LABEL_FORMATTER.format(instant.atZone(zone));
    }

    private boolean isWeekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> true;
            default -> false;
        };
    }

    private LocalTime parseTimeValue(String raw, LocalTime fallback) {
        try {
            return LocalTime.parse(trimOr(raw, fallback.toString()));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private JsonNode parseJson(String raw, String fallback) {
        try {
            return OBJECT_MAPPER.readTree(trimOr(raw, fallback));
        } catch (Exception ex) {
            try {
                return OBJECT_MAPPER.readTree(fallback);
            } catch (Exception ignored) {
                return OBJECT_MAPPER.createObjectNode();
            }
        }
    }

    private String toJson(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (Exception ex) {
            throw new BusinessException("SCHEDULING_JSON_ERROR", "Nao foi possivel persistir sugestoes de horarios");
        }
    }

    private Instant parseInstant(String raw) {
        String value = trimOr(raw, "");
        if (value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalize(String value) {
        return trimOr(value, "").toLowerCase(Locale.ROOT);
    }

    private String trimOr(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private record TimeRange(Instant start, Instant end) { }

    public record DesiredDateWindow(Instant start, Instant end) {
        public static DesiredDateWindow of(Instant start, Instant end) {
            return new DesiredDateWindow(start, end);
        }
    }

    public record StoredSuggestionState(
            String timeZone,
            List<SchedulingSlot> slots,
            Instant generatedAt,
            Instant expiresAt,
            DesiredDateWindow desiredDateWindow,
            int durationMinutes
    ) {
        public static StoredSuggestionState of(
                String timeZone,
                List<SchedulingSlot> slots,
                Instant generatedAt,
                Instant expiresAt,
                DesiredDateWindow desiredDateWindow,
                int durationMinutes
        ) {
            return new StoredSuggestionState(timeZone, slots, generatedAt, expiresAt, desiredDateWindow, durationMinutes);
        }
    }
}

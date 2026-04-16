package com.io.appioweb.adapters.web.atendimentos;

import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoConversationRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.AtendimentoMessageRepositoryJpa;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoConversationEntity;
import com.io.appioweb.adapters.persistence.atendimentos.JpaAtendimentoMessageEntity;
import com.io.appioweb.application.auth.port.out.CompanyRepositoryPort;
import com.io.appioweb.realtime.RealtimeGateway;
import com.io.appioweb.shared.errors.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AtendimentoAutomationMessageService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CompanyRepositoryPort companies;
    private final AtendimentoConversationRepositoryJpa conversations;
    private final AtendimentoMessageRepositoryJpa messages;
    private final RealtimeGateway realtime;

    public AtendimentoAutomationMessageService(
            CompanyRepositoryPort companies,
            AtendimentoConversationRepositoryJpa conversations,
            AtendimentoMessageRepositoryJpa messages,
            RealtimeGateway realtime
    ) {
        this.companies = companies;
        this.conversations = conversations;
        this.messages = messages;
        this.realtime = realtime;
    }

    public SentMessageResult sendAutomaticText(UUID companyId, String phone, String message, int delayTypingSeconds) {
        throw new BusinessException(
                "ATENDIMENTO_CHANNEL_REMOVED",
                "O canal de atendimento via WhatsApp/Z-API foi removido desta operação."
        );
    }

    private PersistedMessage persistOutgoingMessage(
            UUID companyId,
            String phone,
            String text,
            String zapiMessageId
    ) {
        JpaAtendimentoConversationEntity conversation = resolveConversation(companyId, phone, phone);
        Instant eventAt = Instant.now();

        conversation.setLastMessageText(trimToNull(text));
        conversation.setLastMessageAt(eventAt);
        if (trimToNull(conversation.getStatus()) == null) {
            conversation.setStatus("NEW");
        }
        conversation.setUpdatedAt(Instant.now());
        if (trimToNull(conversation.getHumanChoiceOptionsJson()) == null) {
            conversation.setHumanChoiceOptionsJson("[]");
        }
        conversations.saveAndFlush(conversation);

        JpaAtendimentoMessageEntity message = new JpaAtendimentoMessageEntity();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversation.getId());
        message.setCompanyId(companyId);
        message.setPhone(conversation.getPhone());
        message.setMessageText(trimToNull(text));
        message.setMessageType("text");
        message.setFromMe(true);
        message.setZapiMessageId(trimToNull(zapiMessageId));
        message.setStatus("SENT");
        message.setCreatedAt(eventAt);
        try {
            messages.saveAndFlush(message);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateMessageViolation(ex)) {
                JpaAtendimentoMessageEntity existing = messages.findByCompanyIdAndZapiMessageId(companyId, zapiMessageId).orElse(null);
                realtime.messageChanged(companyId, conversation.getId());
                realtime.conversationChanged(companyId, conversation.getId());
                if (existing != null) {
                    return new PersistedMessage(conversation.getId(), existing.getId());
                }
                return new PersistedMessage(conversation.getId(), null);
            }
            throw ex;
        }

        realtime.messageChanged(companyId, conversation.getId());
        realtime.conversationChanged(companyId, conversation.getId());
        return new PersistedMessage(conversation.getId(), message.getId());
    }

    private JpaAtendimentoConversationEntity resolveConversation(UUID companyId, String phone, String displayName) {
        String normalizedPhone = normalizePhone(phone);
        List<JpaAtendimentoConversationEntity> existing = conversations.findAllByCompanyIdAndPhoneIn(companyId, equivalentPhones(normalizedPhone));
        if (!existing.isEmpty()) {
            return existing.stream()
                    .max(this::compareConversationRecency)
                    .orElse(existing.get(0));
        }

        JpaAtendimentoConversationEntity created = new JpaAtendimentoConversationEntity();
        created.setId(UUID.randomUUID());
        created.setCompanyId(companyId);
        created.setPhone(normalizedPhone);
        created.setDisplayName(trimToNull(displayName) != null ? trimToNull(displayName) : normalizedPhone);
        created.setStatus("NEW");
        created.setHumanChoiceOptionsJson("[]");
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());
        try {
            return conversations.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateConversationViolation(ex)) {
                List<JpaAtendimentoConversationEntity> afterRace = conversations.findAllByCompanyIdAndPhoneIn(companyId, equivalentPhones(normalizedPhone));
                if (!afterRace.isEmpty()) {
                    return afterRace.stream()
                            .max(this::compareConversationRecency)
                            .orElse(afterRace.get(0));
                }
            }
            throw ex;
        }
    }

    private int compareConversationRecency(JpaAtendimentoConversationEntity a, JpaAtendimentoConversationEntity b) {
        Instant aLast = a.getLastMessageAt();
        Instant bLast = b.getLastMessageAt();
        if (aLast != null && bLast != null) {
            int byLast = aLast.compareTo(bLast);
            if (byLast != 0) return byLast;
        } else if (aLast != null) {
            return 1;
        } else if (bLast != null) {
            return -1;
        }
        Instant aUpdated = a.getUpdatedAt();
        Instant bUpdated = b.getUpdatedAt();
        if (aUpdated != null && bUpdated != null) return aUpdated.compareTo(bUpdated);
        if (aUpdated != null) return 1;
        if (bUpdated != null) return -1;
        return 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private static List<String> equivalentPhones(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized.isBlank()) return List.of();

        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(normalized);

        if (normalized.startsWith("55")) {
            String national = normalized.substring(2);
            if (!national.isBlank()) set.add(national);

            if (normalized.length() == 13 && normalized.charAt(4) == '9') {
                set.add(normalized.substring(0, 4) + normalized.substring(5));
                if (national.length() == 11 && national.charAt(2) == '9') {
                    set.add(national.substring(0, 2) + national.substring(3));
                }
            } else if (normalized.length() == 12) {
                set.add(normalized.substring(0, 4) + "9" + normalized.substring(4));
                if (national.length() == 10) {
                    set.add(national.substring(0, 2) + "9" + national.substring(2));
                }
            }
        } else if (normalized.length() == 10 || normalized.length() == 11) {
            String withCountry = "55" + normalized;
            set.add(withCountry);
            if (normalized.length() == 11 && normalized.charAt(2) == '9') {
                set.add("55" + normalized.substring(0, 2) + normalized.substring(3));
            } else if (normalized.length() == 10) {
                set.add("55" + normalized.substring(0, 2) + "9" + normalized.substring(2));
            }
        }
        return List.copyOf(set);
    }

    private static boolean isDuplicateMessageViolation(Throwable throwable) {
        String message = rootCauseMessage(throwable).toLowerCase();
        return message.contains("uq_atendimento_messages_company_zapi_message_id")
                || message.contains("atendimento_messages")
                || message.contains("duplicate");
    }

    private static boolean isDuplicateConversationViolation(Throwable throwable) {
        String message = rootCauseMessage(throwable).toLowerCase();
        return message.contains("atendimento_conversations")
                || message.contains("duplicate")
                || message.contains("unique");
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record PersistedMessage(UUID conversationId, UUID messageId) {
    }

    public record SentMessageResult(UUID conversationId, UUID messageId, String zapiMessageId) {
    }
}

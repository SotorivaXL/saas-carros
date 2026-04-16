package com.io.appioweb.adapters.persistence.atendimentos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtendimentoMessageRepositoryJpa extends JpaRepository<JpaAtendimentoMessageEntity, UUID> {
    List<JpaAtendimentoMessageEntity> findAllByConversationIdAndCompanyIdOrderByCreatedAtAsc(UUID conversationId, UUID companyId);
    List<JpaAtendimentoMessageEntity> findAllByConversationIdAndCompanyIdOrderByCreatedAtDesc(UUID conversationId, UUID companyId, Pageable pageable);
    List<JpaAtendimentoMessageEntity> findAllByConversationIdInAndCompanyIdOrderByCreatedAtAsc(List<UUID> conversationIds, UUID companyId);
    List<JpaAtendimentoMessageEntity> findAllByCompanyIdAndZapiMessageIdIn(UUID companyId, List<String> zapiMessageIds);
    Optional<JpaAtendimentoMessageEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<JpaAtendimentoMessageEntity> findByCompanyIdAndZapiMessageId(UUID companyId, String zapiMessageId);
    Optional<JpaAtendimentoMessageEntity> findFirstByConversationIdAndCompanyIdOrderByCreatedAtDesc(UUID conversationId, UUID companyId);
}

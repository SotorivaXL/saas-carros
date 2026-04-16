package com.io.appioweb.adapters.persistence.atendimentos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtendimentoConversationRepositoryJpa extends JpaRepository<JpaAtendimentoConversationEntity, UUID> {
    List<JpaAtendimentoConversationEntity> findAllByCompanyIdOrderByLastMessageAtDescUpdatedAtDesc(UUID companyId);
    Optional<JpaAtendimentoConversationEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<JpaAtendimentoConversationEntity> findByCompanyIdAndPhone(UUID companyId, String phone);
    List<JpaAtendimentoConversationEntity> findAllByCompanyIdAndPhoneIn(UUID companyId, List<String> phones);
    List<JpaAtendimentoConversationEntity> findAllByCompanyIdAndContactLid(UUID companyId, String contactLid);
    boolean existsByCompanyIdAndAssignedTeamId(UUID companyId, UUID assignedTeamId);
}

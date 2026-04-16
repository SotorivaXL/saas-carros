package com.io.appioweb.adapters.persistence.atendimentos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AtendimentoSessionLabelRepositoryJpa extends JpaRepository<JpaAtendimentoSessionLabelEntity, UUID> {
    List<JpaAtendimentoSessionLabelEntity> findAllByCompanyIdAndSessionIdIn(UUID companyId, Collection<UUID> sessionIds);
    List<JpaAtendimentoSessionLabelEntity> findAllByCompanyIdAndSessionId(UUID companyId, UUID sessionId);
    void deleteAllByCompanyIdAndSessionId(UUID companyId, UUID sessionId);
}

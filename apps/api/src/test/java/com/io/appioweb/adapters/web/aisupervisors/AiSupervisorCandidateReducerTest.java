package com.io.appioweb.adapters.web.aisupervisors;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSupervisorCandidateReducerTest {

    private final AiSupervisorCandidateReducer reducer = new AiSupervisorCandidateReducer();

    @Test
    void reducesMoreThanTenCandidatesToTopEightByKeywordMatch() {
        List<AiSupervisorCandidateReducer.CandidateRef> candidates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String name = i == 9 ? "Agente Suporte Tecnico" : "Agente " + i;
            String triage = i == 9 ? "Resolve suporte tecnico, falhas, bugs e incidente." : "Fluxo generico " + i;
            candidates.add(new AiSupervisorCandidateReducer.CandidateRef("agent-" + i, name, triage));
        }

        List<AiSupervisorCandidateReducer.CandidateRef> reduced = reducer.reduce(
                candidates,
                "Preciso de suporte tecnico para um bug no sistema",
                10,
                8
        );

        assertEquals(8, reduced.size());
        assertTrue(reduced.stream().anyMatch(item -> "agent-9".equals(item.agentId())));
    }
}

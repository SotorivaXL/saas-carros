package com.io.appioweb.adapters.web.aisupervisors;

import com.io.appioweb.shared.errors.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSupervisorDecisionParserTest {

    private final AiSupervisorDecisionParser parser = new AiSupervisorDecisionParser();

    @Test
    void rejectsTrailingTextOutsideJson() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parseStrict(
                "{\"action\":\"ASSIGN_AGENT\",\"targetAgentId\":\"agent-1\",\"messageToSend\":null,\"humanQueue\":null,\"confidence\":0.8,\"reason\":\"ok\"} lixo",
                Set.of("agent-1"),
                220
        ));

        assertEquals("SUPERVISOR_LLM_JSON_PARSE_ERROR", ex.code());
    }

    @Test
    void rejectsWrongTypes() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parseStrict(
                "{\"action\":\"ASSIGN_AGENT\",\"targetAgentId\":123,\"messageToSend\":null,\"humanQueue\":null,\"confidence\":\"0.8\",\"reason\":\"ok\"}",
                Set.of("agent-1"),
                220
        ));

        assertEquals("SUPERVISOR_LLM_SCHEMA_INVALID", ex.code());
    }

    @Test
    void rejectsTargetAgentOutsideCandidates() {
        BusinessException ex = assertThrows(BusinessException.class, () -> parser.parseStrict(
                "{\"action\":\"ASSIGN_AGENT\",\"targetAgentId\":\"agent-x\",\"messageToSend\":null,\"humanQueue\":null,\"confidence\":0.8,\"reason\":\"ok\"}",
                Set.of("agent-1"),
                220
        ));

        assertEquals("SUPERVISOR_LLM_TARGET_AGENT_INVALID", ex.code());
    }
}

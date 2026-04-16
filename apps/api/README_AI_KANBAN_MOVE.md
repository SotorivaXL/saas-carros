# AI Kanban Move Card

## Configurar prompts por etapa

1. Salve as regras por agente em `PUT /ai-agents/{agentId}/kanban/stage-rules`.
2. Body esperado:

```json
{
  "rules": [
    {
      "stageId": "stage_agendado",
      "enabled": true,
      "prompt": "Mover quando reuniao estiver confirmada com data/hora ou link gerado.",
      "priority": 100,
      "onlyForwardOverride": true,
      "allowedFromStages": ["stage_qualificado"]
    }
  ]
}
```

3. Consultar regras: `GET /ai-agents/{agentId}/kanban/stage-rules`.

## Como o gating funciona

Nivel 1 (sem LLM):
- feature flag por empresa (`ai.kanban-move.enabled`, `ai.kanban-move.enabled-companies`)
- sem mensagem nova desde `lastEvaluatedMessageId` -> skip
- sem regra habilitada com prompt -> skip
- cooldown ativo (`ai.kanban-move.cooldown-seconds`, default 20s) -> skip
- mensagem de baixo sinal / muito curta -> skip
- card em etapa final (quando `ai.kanban-move.block-when-final-stage=true`) -> skip

Nivel 2 (com LLM):
- envia somente `N` mensagens (`ai.kanban-move.max-messages`, default 6)
- cada mensagem truncada (`ai.kanban-move.max-message-chars`, default 240)
- candidatos reduzidos: etapa atual + proximas 2 + etapas especiais finais
- `temperature=0`, `top_p=1`, resposta JSON curta e estrita

## Debug e auditoria

Tabelas:
- `ai_agent_stage_rules`: configuracao das regras por etapa
- `ai_agent_kanban_state`: cache de avaliacao (`lastEvaluatedMessageId`, cooldown)
- `ai_agent_kanban_move_attempts`: auditoria de cada tentativa (`MOVE`, `NO_MOVE`, `ERROR`)

Campos importantes em auditoria:
- `evaluation_key`
- `from_stage_id` / `to_stage_id`
- `reason`
- `error_code` / `error_message_short`
- `llm_request_id`

Endpoint interno para disparo manual:
- `POST /internal/ai/kanban/evaluate`

```json
{
  "conversationId": "uuid",
  "cardId": "uuid-ou-string",
  "agentId": "agent_id",
  "events": ["meetingScheduled"],
  "force": true
}
```

## Testes

Executar somente os testes da feature:

```bash
./mvnw.cmd -q -Dtest=KanbanMoveDecisionServiceTest test
```

Executar suite completa:

```bash
./mvnw.cmd -q test
```

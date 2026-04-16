# Supervisores IA

## Como configurar

1. Crie um supervisor em `POST /api/ai/supervisors`.
2. Defina nome, estilo de comunicacao, perfil, objetivo, provider/model e regras de handoff humano.
3. Marque `defaultForCompany=true` no supervisor que deve receber a triagem automatica por padrao.
4. Salve a distribuicao em `PUT /api/ai/supervisors/{id}/distribution`.

## Como cadastrar agentes na distribuicao

- Informe apenas agentes que ja existem em `ai_agent_company_state.agents_json`.
- Para cada agente, preencha um `triageText` curto e especifico.
- Foque em intencao, escopo e limites do agente.
- Exemplo bom:
  `Atende suporte tecnico, erros, falhas, integracoes quebradas e incidentes em producao.`
- Exemplo ruim:
  `Ajuda em varias coisas do sistema.`

## Como funciona a triagem em 1 pergunta

- O supervisor roda apenas quando a conversa ainda nao tem `assignedAgentId` e nao esta em handoff humano.
- No primeiro contato ele decide entre:
  `ASSIGN_AGENT`, `ASK_CLARIFYING`, `HANDOFF_HUMAN` ou `NO_ACTION`.
- `ASK_CLARIFYING` so pode acontecer uma vez.
- Quando o lead responde a pergunta do supervisor, a proxima avaliacao cai obrigatoriamente em:
  `ASSIGN_AGENT` ou `HANDOFF_HUMAN`.
- Se o LLM falhar:
  primeira rodada -> pergunta fallback curta.
  segunda rodada -> fallback para agente padrao por prioridade ou handoff humano.

## Economia de tokens

- Gating antes do LLM:
  sem supervisor default, sem regras ativas, conversa ja atribuida, cooldown ativo ou mesma mensagem ja avaliada -> nao chama modelo.
- Contexto compacto:
  no maximo 3 mensagens iniciais do lead, truncadas, e quando houver triagem previa apenas pergunta + resposta.
- Reducao de candidatos:
  acima de 10 agentes, o runtime reduz para ate 8 por matching de palavras-chave e prioridade.
- Resposta do modelo:
  JSON curto, `temperature=0`, `top_p=1`.

## Como debugar

- Estado por conversa:
  tabela `ai_supervisor_conversation_state`
- Regras e distribuicao:
  tabelas `ai_supervisors` e `ai_supervisor_agent_rules`
- Auditoria de decisoes:
  tabela `ai_supervisor_decision_logs`
- Flags de handoff/atribuicao:
  colunas novas em `atendimento_conversations`

Campos uteis para debug:

- `evaluation_key`: idempotencia da avaliacao
- `action`: decisao final aplicada
- `target_agent_id`: agente escolhido
- `error_code` e `error_message_short`: falhas de parser/LLM/fallback
- `context_snippet`: trecho mascarado e truncado do contexto enviado

## Observacoes

- O runtime publica eventos Spring locais:
  `ConversationAssignedToAgentEvent`,
  `ConversationHandoffHumanRequestedEvent`,
  `SupervisorAskedClarificationEvent`.
- Nao existe fila humana dedicada no projeto hoje.
  A implementacao minima usa flags em `atendimento_conversations` para abrir o handoff com auditoria e permitir evolucao posterior.

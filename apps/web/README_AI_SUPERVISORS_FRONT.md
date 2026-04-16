# Frontend de Supervisores IA

## Rotas criadas

- `/protected/ai/supervisores`
- `/protected/ai/supervisores/novo`
- `/protected/ai/supervisores/:id`
- `/protected/ai/supervisores/:id/distribuicao`

Aliases com redirecionamento:

- `/ai/supervisores`
- `/ai/supervisores/novo`
- `/ai/supervisores/:id`
- `/ai/supervisores/:id/distribuicao`

## Como rodar

```bash
cd apps/web
npm run dev
```

Build validado com:

```bash
npm run build
```

## Endpoints esperados

O frontend usa proxies locais em `src/app/api/ai-supervisors` e espera estes contratos no backend:

- `GET /api/ai/supervisors`
- `POST /api/ai/supervisors`
- `GET /api/ai/supervisors/{id}`
- `PUT /api/ai/supervisors/{id}`
- `PUT /api/ai/supervisors/{id}/distribution`

Para listar agentes disponiveis na distribuicao, o frontend reutiliza:

- `GET /api/ai-agents/state`

## Simulacao

Hoje o backend ainda nao expoe um endpoint dedicado de simulacao. Por isso:

- o card "Teste de triagem" tenta `POST /api/ai-supervisors/{id}/simulate`
- por padrao, esse proxy responde `501` com instrucao de implementacao
- para desenvolvimento local, ative mock com `NEXT_PUBLIC_USE_MOCK_AI_SUPERVISORS=true`
- tambem e possivel usar `?mockSupervisors=1` na URL

No modo mock, a simulacao usa heuristica local baseada na mensagem do lead e nos `triageText` configurados.

## Observacoes de UX

- a lista tem busca, paginacao client-side, duplicacao e toggle de status
- o formulario usa `react-hook-form` + `zod`
- a distribuicao avisa sobre alteracoes nao salvas no `beforeunload`
- o salvar da distribuicao fica bloqueado quando houver agente habilitado sem `triageText`

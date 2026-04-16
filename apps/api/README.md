# API

## Google Calendar + Google Meet

Variaveis necessarias:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `GOOGLE_OAUTH_SCOPES`
- `APP_DB_ENCRYPTION_KEY`

Resumo de configuracao no Google Cloud Console:

1. Ative a Google Calendar API no projeto.
2. Crie credenciais OAuth 2.0 do tipo Web Application.
3. Cadastre a URL do frontend em `Authorized redirect URIs`, usando o mesmo valor de `GOOGLE_OAUTH_REDIRECT_URI`.
   Exemplo: `https://seu-dominio-web/api/integrations/google/oauth/callback`.
4. Inclua escopos que cubram Calendar e offline access. O backend envia `access_type=offline` e `prompt=consent`.

Endpoints principais:

- `GET /api/integrations/google/oauth/status`
- `GET /api/integrations/google/oauth/start`
- `GET /api/integrations/google/oauth/callback`
- `POST /api/integrations/google/oauth/disconnect`

Logs e debug:

- `GoogleCalendarClient` registra status HTTP e retries sem expor tokens.
- `AiAgentOrchestrationService` grava o fluxo deterministico de agenda em `ai_agent_run_log`.
- Sugestoes persistidas ficam em `ai_agent_calendar_suggestion_state`.

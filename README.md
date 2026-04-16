# IOAuto

IOAuto e uma base de micro-saas multi-tenant para operacoes automotivas.

O projeto foi reorganizado para abandonar o foco anterior em CRM com agentes de IA, mantendo apenas a base de autenticacao, tenancy e atendimento humano. Agora a stack esta orientada para:

- atendimento humano omnichannel com Z-API
- origem da conversa visivel no inbox
- cadastro unificado de veiculos
- fila de publicacao por integracao
- assinatura recorrente automatizada com Stripe
- estrutura preparada para WebMotors e proximos marketplaces

## Stack

- `apps/web`: Next.js 15 + React 19 + TypeScript
- `apps/api`: Spring Boot 4 + Java 25
- PostgreSQL 16
- Redis 7

## Estrutura

```text
apps/
|-- api/
|   |-- src/main/java/com/io/appioweb/
|   |   |-- adapters/persistence/ioauto
|   |   |-- adapters/web/ioauto
|   |   |-- adapters/web/atendimentos
|   |   |-- application/auth
|   |   `-- config
|   `-- src/main/resources/db/migration
`-- web/
    |-- src/app
    |-- src/core
    `-- src/modules/ioauto
```

## Modulos novos

### Backend

- `V29__ioauto_core.sql`
  Cria tabelas de billing, signup, integracoes, veiculos e publicacoes.
- `V30__atendimento_conversation_source_platform.sql`
  Adiciona `source_platform` e `source_reference` em conversas.
- `adapters/web/ioauto`
  Exponhe endpoints de dashboard, estoque, integracoes, publicacoes e billing.
- `IoAutoBillingService`
  Faz checkout Stripe, ativa tenant apos pagamento, sincroniza assinatura e abre portal do cliente.

### Frontend

- landing publica em `/`
- checkout publico em `/assinar`
- pagina de sucesso em `/assinar/sucesso`
- area protegida com:
  - `/protected/dashboard`
  - `/protected/conversas`
  - `/protected/estoque`
  - `/protected/publicacoes`
  - `/protected/integracoes`
  - `/protected/assinatura`

## Billing recorrente

O fluxo de assinatura funciona assim:

1. o lead preenche o formulario publico
2. o backend cria um `signup_intent`
3. o Stripe Checkout abre em modo assinatura
4. quando o pagamento e confirmado, o tenant e provisionado
5. a empresa, equipe inicial, usuario admin e integracoes padrao sao criados automaticamente
6. o cliente pode entrar no sistema com o e-mail e senha definidos no cadastro

Variaveis principais:

- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `STRIPE_PRICE_ID`
- `APP_PUBLIC_URL`

## Desenvolvimento local

Suba a infra:

```bash
docker compose -f dev-infra/docker-compose.yml up -d
```

Backend:

```bash
cd apps/api
./mvnw spring-boot:run
```

Frontend:

```bash
cd apps/web
npm install
npm run dev
```

## Deploy

Use:

- `.env.vps.example`
- `docker-compose.vps.yml`
- `DEPLOY_AAPANEL.md`

O deploy foi simplificado para:

- `postgres`
- `api`
- `web`

Redis pode continuar compartilhado da VPS.

## Observacoes de integracao

- Z-API continua sendo o canal principal para atendimento humano.
- WebMotors ja possui estrutura de configuracao e publicacao por tenant.
- As chamadas especificas do WebService da WebMotors podem ser conectadas na camada de integracoes sem mudar o modelo do produto.

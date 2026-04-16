alter table atendimento_conversations
    add column if not exists source_platform varchar(40) not null default 'WHATSAPP',
    add column if not exists source_reference varchar(180);

create index if not exists idx_atendimento_conversations_company_source_platform
    on atendimento_conversations (company_id, source_platform);

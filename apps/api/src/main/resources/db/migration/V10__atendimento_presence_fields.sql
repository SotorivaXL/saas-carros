alter table atendimento_conversations
    add column presence_status varchar(20),
    add column presence_last_seen timestamptz,
    add column presence_updated_at timestamptz;

create index idx_atendimento_conversations_company_presence
    on atendimento_conversations (company_id, presence_status);


alter table atendimento_conversations
    add column contact_lid varchar(80);

create index idx_atendimento_conversations_company_contact_lid
    on atendimento_conversations (company_id, contact_lid);


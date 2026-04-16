alter table atendimento_conversations
    add column status varchar(20) not null default 'NEW',
    add column assigned_user_id uuid references users(id),
    add column assigned_user_name varchar(180),
    add column started_at timestamptz;

create index idx_atendimento_conversations_company_status
    on atendimento_conversations (company_id, status);

create index idx_atendimento_conversations_company_assigned
    on atendimento_conversations (company_id, assigned_user_id);


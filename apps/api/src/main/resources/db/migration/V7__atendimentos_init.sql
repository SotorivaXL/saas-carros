create table atendimento_conversations (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    phone varchar(30) not null,
    display_name varchar(180),
    last_message_text text,
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (company_id, phone)
);

create index idx_atendimento_conversations_company_last_at
    on atendimento_conversations (company_id, last_message_at desc nulls last);

create table atendimento_messages (
    id uuid primary key,
    conversation_id uuid not null references atendimento_conversations(id) on delete cascade,
    company_id uuid not null references companies(id) on delete cascade,
    phone varchar(30) not null,
    message_text text,
    message_type varchar(40) not null default 'text',
    from_me boolean not null,
    zapi_message_id varchar(160),
    status varchar(20),
    moment bigint,
    payload_json text,
    created_at timestamptz not null default now()
);

create index idx_atendimento_messages_conversation_created_at
    on atendimento_messages (conversation_id, created_at);

create unique index uq_atendimento_messages_company_zapi_message_id
    on atendimento_messages (company_id, zapi_message_id)
    where zapi_message_id is not null;


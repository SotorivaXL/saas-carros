create table ai_agent_calendar_suggestion_state (
    id uuid primary key,
    company_id uuid not null,
    conversation_id uuid not null,
    timezone varchar(80) not null,
    slots_json text not null default '[]',
    context_json text not null default '{}',
    generated_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_ai_agent_calendar_suggestion_state_company_conversation unique (company_id, conversation_id)
);

create index idx_ai_agent_calendar_suggestion_state_conversation
    on ai_agent_calendar_suggestion_state (conversation_id, updated_at desc);


create table company_google_oauth (
    id uuid primary key,
    company_id uuid not null,
    google_user_email varchar(180),
    refresh_token_encrypted text not null,
    access_token_encrypted text,
    access_token_expires_at timestamptz,
    scopes text not null,
    status varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_company_google_oauth_company unique (company_id)
);

create index idx_company_google_oauth_status
    on company_google_oauth (status, updated_at desc);

create table ai_agent_calendar_suggestion_state (
    id uuid primary key,
    company_id uuid not null,
    conversation_id uuid not null,
    timezone varchar(80) not null,
    slots_json text not null default '[]',
    context_json text not null default '{}',
    generated_at timestamptz not null default now(),
    expires_at timestamptz not null,
    updated_at timestamptz not null default now(),
    constraint uq_ai_agent_calendar_suggestion_state_company_conversation unique (company_id, conversation_id)
);

create index idx_ai_agent_calendar_suggestion_state_conversation
    on ai_agent_calendar_suggestion_state (company_id, conversation_id, expires_at desc);

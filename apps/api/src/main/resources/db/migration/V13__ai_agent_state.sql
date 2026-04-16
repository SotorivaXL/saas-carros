create table ai_agent_company_state (
    company_id uuid primary key,
    providers_json text not null default '[]',
    agents_json text not null default '[]',
    knowledge_base_json text not null default '[]',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ai_agent_company_state_updated_at
    on ai_agent_company_state (updated_at desc);

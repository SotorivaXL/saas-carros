alter table crm_company_state
    add column if not exists version bigint not null default 0;

create table ai_agent_stage_rules (
    id uuid primary key,
    company_id uuid not null,
    agent_id varchar(120) not null,
    stage_id varchar(120) not null,
    enabled boolean not null default true,
    prompt text not null default '',
    priority integer,
    only_forward_override boolean,
    allowed_from_stages_json text not null default '[]',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_ai_agent_stage_rules_company_agent_stage unique (company_id, agent_id, stage_id)
);

create index idx_ai_agent_stage_rules_company_agent_enabled
    on ai_agent_stage_rules (company_id, agent_id, enabled);

create table ai_agent_kanban_move_attempts (
    id uuid primary key,
    company_id uuid not null,
    agent_id varchar(120) not null,
    conversation_id uuid not null,
    card_id varchar(120) not null,
    from_stage_id varchar(120),
    to_stage_id varchar(120),
    decision varchar(20) not null,
    confidence numeric(4,3),
    reason varchar(180),
    evaluation_key varchar(200) not null,
    last_message_id uuid,
    error_code varchar(80),
    error_message_short varchar(180),
    llm_request_id varchar(120),
    created_at timestamptz not null default now(),
    constraint uq_ai_agent_kanban_move_attempt_eval unique (company_id, agent_id, conversation_id, card_id, evaluation_key)
);

create index idx_ai_agent_kanban_move_attempts_company_conversation_created
    on ai_agent_kanban_move_attempts (company_id, conversation_id, created_at desc);

create index idx_ai_agent_kanban_move_attempts_card_created
    on ai_agent_kanban_move_attempts (company_id, card_id, created_at desc);

create table ai_agent_kanban_state (
    company_id uuid not null,
    agent_id varchar(120) not null,
    conversation_id uuid not null,
    card_id varchar(120) not null,
    last_evaluated_message_id uuid,
    last_evaluated_message_at timestamptz,
    last_decision_at timestamptz,
    last_moved_stage_id varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (company_id, agent_id, conversation_id, card_id)
);

create index idx_ai_agent_kanban_state_lookup
    on ai_agent_kanban_state (company_id, conversation_id, agent_id, card_id);

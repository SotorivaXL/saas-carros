alter table atendimento_conversations
    add column if not exists assigned_agent_id varchar(120),
    add column if not exists human_handoff_requested boolean not null default false,
    add column if not exists human_handoff_queue varchar(120),
    add column if not exists human_handoff_requested_at timestamptz,
    add column if not exists human_user_choice_required boolean not null default false,
    add column if not exists human_choice_options_json text not null default '[]';

create index if not exists idx_atendimento_conversations_company_assigned_agent
    on atendimento_conversations (company_id, assigned_agent_id);

create index if not exists idx_atendimento_conversations_company_handoff
    on atendimento_conversations (company_id, human_handoff_requested, human_handoff_requested_at desc);

create table ai_supervisors (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    name varchar(180) not null,
    communication_style text not null default '',
    profile text not null default '',
    objective text not null default '',
    reasoning_model_version varchar(80) not null default '',
    provider varchar(40) not null default 'openai',
    model varchar(120) not null default '',
    other_rules text not null default '',
    human_handoff_enabled boolean not null default false,
    human_user_choice_enabled boolean not null default false,
    human_choice_options_json text not null default '[]',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ai_supervisors_company_enabled
    on ai_supervisors (company_id, enabled, updated_at desc);

create table ai_supervisor_agent_rules (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    supervisor_id uuid not null references ai_supervisors(id) on delete cascade,
    agent_id varchar(120) not null,
    agent_name_snapshot varchar(180),
    triage_text text not null default '',
    enabled boolean not null default true,
    priority integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_ai_supervisor_agent_rules unique (company_id, supervisor_id, agent_id)
);

create index idx_ai_supervisor_agent_rules_lookup
    on ai_supervisor_agent_rules (company_id, supervisor_id, enabled, priority desc);

create table ai_supervisor_conversation_state (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    supervisor_id uuid not null references ai_supervisors(id) on delete cascade,
    conversation_id uuid not null references atendimento_conversations(id) on delete cascade,
    card_id varchar(120),
    assigned_agent_id varchar(120),
    triage_asked boolean not null default false,
    last_supervisor_question_message_id uuid,
    last_evaluated_message_id uuid,
    last_decision_at timestamptz,
    cooldown_until timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_ai_supervisor_conversation_state unique (company_id, supervisor_id, conversation_id)
);

create index idx_ai_supervisor_conversation_state_lookup
    on ai_supervisor_conversation_state (company_id, supervisor_id, conversation_id);

create table ai_supervisor_decision_logs (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    supervisor_id uuid not null references ai_supervisors(id) on delete cascade,
    conversation_id uuid not null references atendimento_conversations(id) on delete cascade,
    inbound_message_id uuid,
    action varchar(32) not null,
    target_agent_id varchar(120),
    confidence numeric(4,3),
    reason varchar(180),
    evaluation_key varchar(200) not null,
    error_code varchar(80),
    error_message_short varchar(220),
    context_snippet varchar(400),
    created_at timestamptz not null default now(),
    constraint uq_ai_supervisor_decision_logs_eval unique (company_id, supervisor_id, conversation_id, evaluation_key)
);

create index idx_ai_supervisor_decision_logs_company_conversation_created
    on ai_supervisor_decision_logs (company_id, conversation_id, created_at desc);

create index idx_ai_supervisor_decision_logs_eval
    on ai_supervisor_decision_logs (company_id, supervisor_id, evaluation_key);

create table ai_supervisor_company_config (
    company_id uuid primary key references companies(id) on delete cascade,
    default_supervisor_id uuid references ai_supervisors(id) on delete set null,
    supervisor_enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

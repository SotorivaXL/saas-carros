create table ai_agent_run_logs (
    id uuid primary key,
    company_id uuid not null,
    conversation_id uuid not null,
    agent_id varchar(120) not null,
    trace_id varchar(120) not null,
    customer_message text,
    final_text text,
    handoff boolean not null default false,
    actions_json text not null default '[]',
    tool_logs_json text not null default '[]',
    request_payload_json text not null default '{}',
    response_payload_json text not null default '{}',
    created_at timestamptz not null default now()
);

create index idx_ai_agent_run_logs_company_created
    on ai_agent_run_logs (company_id, created_at desc);

create index idx_ai_agent_run_logs_conversation_created
    on ai_agent_run_logs (conversation_id, created_at desc);

create unique index uq_ai_agent_run_logs_trace
    on ai_agent_run_logs (trace_id);

create table ai_agent_calendar_events (
    id uuid primary key,
    company_id uuid not null,
    conversation_id uuid not null,
    slot_key varchar(180) not null,
    google_event_id varchar(180) not null,
    html_link text,
    meet_link text,
    calendar_id varchar(180) not null,
    raw_json text not null default '{}',
    created_at timestamptz not null default now(),
    constraint uq_ai_agent_calendar_events_company_slot unique (company_id, slot_key)
);

create index idx_ai_agent_calendar_events_conversation_created
    on ai_agent_calendar_events (conversation_id, created_at desc);


create table atendimento_sessions (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    conversation_id uuid not null references atendimento_conversations(id) on delete cascade,
    channel_id varchar(180),
    channel_name varchar(180),
    responsible_user_id uuid references users(id),
    responsible_user_name varchar(180),
    arrived_at timestamptz not null,
    started_at timestamptz,
    first_response_at timestamptz,
    completed_at timestamptz,
    classification_result varchar(40),
    classification_label varchar(180),
    status varchar(20) not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_atendimento_sessions_status
        check (status in ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    constraint chk_atendimento_sessions_classification_result
        check (
            classification_result is null
            or classification_result in ('OBJECTIVE_ACHIEVED', 'OBJECTIVE_LOST', 'QUESTION', 'OTHER')
        )
);

create unique index uq_atendimento_sessions_open_conversation
    on atendimento_sessions (company_id, conversation_id)
    where completed_at is null;

create index idx_atendimento_sessions_company_arrived_at
    on atendimento_sessions (company_id, arrived_at);

create index idx_atendimento_sessions_company_started_at
    on atendimento_sessions (company_id, started_at);

create index idx_atendimento_sessions_company_completed_at
    on atendimento_sessions (company_id, completed_at);

create index idx_atendimento_sessions_company_responsible
    on atendimento_sessions (company_id, responsible_user_id);

create index idx_atendimento_sessions_company_channel
    on atendimento_sessions (company_id, channel_id);

create index idx_atendimento_sessions_company_classification
    on atendimento_sessions (company_id, classification_result);

create index idx_atendimento_sessions_company_status
    on atendimento_sessions (company_id, status);

create index idx_atendimento_sessions_company_conversation
    on atendimento_sessions (company_id, conversation_id, arrived_at desc);

create table atendimento_session_labels (
    id uuid primary key,
    session_id uuid not null references atendimento_sessions(id) on delete cascade,
    company_id uuid not null references companies(id) on delete cascade,
    label_id varchar(120) not null,
    label_title varchar(180) not null,
    label_color varchar(7),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_atendimento_session_labels_session_label
    on atendimento_session_labels (session_id, label_id);

create index idx_atendimento_session_labels_company_session
    on atendimento_session_labels (company_id, session_id);

create index idx_atendimento_session_labels_company_label
    on atendimento_session_labels (company_id, label_id);

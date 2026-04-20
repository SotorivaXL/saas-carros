create table ioauto_public_lead_events (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    vehicle_id uuid references ioauto_vehicles(id) on delete set null,
    event_type varchar(40) not null,
    source_type varchar(40),
    source_reference varchar(160),
    page_path varchar(255),
    source_url text,
    session_id varchar(120),
    created_at timestamptz not null default now()
);

create index idx_ioauto_public_lead_events_company_created
    on ioauto_public_lead_events (company_id, created_at desc);

create index idx_ioauto_public_lead_events_company_source
    on ioauto_public_lead_events (company_id, source_type, source_reference, created_at desc);

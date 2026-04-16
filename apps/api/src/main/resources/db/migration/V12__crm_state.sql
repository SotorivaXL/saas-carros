create table crm_company_state (
    company_id uuid primary key,
    stages_json text not null default '[]',
    lead_stage_map_json text not null default '{}',
    custom_fields_json text not null default '[]',
    lead_field_values_json text not null default '{}',
    lead_fields_order_json text not null default '[]',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_crm_company_state_updated_at
    on crm_company_state (updated_at desc);

create table ioauto_public_links (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    vehicle_id uuid references ioauto_vehicles(id) on delete set null,
    name varchar(160) not null,
    link_kind varchar(30) not null,
    scope_type varchar(30) not null,
    source_type varchar(40),
    source_reference varchar(160),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ioauto_public_links_company_created
    on ioauto_public_links (company_id, created_at desc);

create index idx_ioauto_public_links_company_source
    on ioauto_public_links (company_id, source_type, source_reference);

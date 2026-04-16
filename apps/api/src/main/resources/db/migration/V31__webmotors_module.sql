create table webmotors_credentials (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    store_name varchar(160) not null default 'Loja principal',
    soap_ads_enabled boolean not null default false,
    rest_leads_enabled boolean not null default false,
    catalog_sync_enabled boolean not null default false,
    lead_pull_enabled boolean not null default false,
    callback_enabled boolean not null default false,
    soap_base_url text,
    soap_auth_path text,
    soap_inventory_path text,
    soap_catalog_path text,
    soap_cnpj_encrypted text,
    soap_email_encrypted text,
    soap_password_encrypted text,
    rest_token_url text,
    rest_api_base_url text,
    rest_username_encrypted text,
    rest_password_encrypted text,
    rest_client_id_encrypted text,
    rest_client_secret_encrypted text,
    callback_secret_encrypted text,
    last_soap_sync_at timestamptz,
    last_lead_pull_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_webmotors_credentials_company_store
    on webmotors_credentials (company_id, store_key);

create table webmotors_ads (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    vehicle_id uuid references ioauto_vehicles(id) on delete set null,
    publication_id uuid references ioauto_vehicle_publications(id) on delete set null,
    remote_ad_code varchar(180),
    remote_status varchar(60) not null,
    title varchar(200),
    brand varchar(120),
    model varchar(120),
    version varchar(160),
    price_cents bigint,
    mileage integer,
    catalog_snapshot_json text not null default '{}',
    remote_payload_json text not null default '{}',
    last_soap_return_code varchar(60),
    last_soap_request_id varchar(120),
    last_error text,
    last_sync_at timestamptz,
    published_at timestamptz,
    remote_updated_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_webmotors_ads_company_remote_code
    on webmotors_ads (company_id, remote_ad_code)
    where remote_ad_code is not null;

create unique index uq_webmotors_ads_company_vehicle
    on webmotors_ads (company_id, vehicle_id)
    where vehicle_id is not null;

create index idx_webmotors_ads_company_sync
    on webmotors_ads (company_id, updated_at desc);

create table webmotors_catalog_mappings (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    mapping_type varchar(60) not null,
    internal_value varchar(180) not null,
    webmotors_code varchar(80) not null,
    webmotors_label varchar(180),
    raw_payload_json text not null default '{}',
    last_refreshed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_webmotors_catalog_mapping_lookup
    on webmotors_catalog_mappings (company_id, store_key, mapping_type, lower(internal_value));

create table webmotors_sync_jobs (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    job_type varchar(40) not null,
    aggregate_id uuid,
    idempotency_key varchar(180) not null,
    payload_json text not null default '{}',
    status varchar(40) not null,
    attempts integer not null default 0,
    next_retry_at timestamptz,
    last_error text,
    locked_at timestamptz,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_webmotors_sync_jobs_idempotency
    on webmotors_sync_jobs (company_id, idempotency_key);

create index idx_webmotors_sync_jobs_status
    on webmotors_sync_jobs (company_id, status, next_retry_at nulls first, created_at asc);

create table webmotors_sync_logs (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    job_id uuid references webmotors_sync_jobs(id) on delete set null,
    channel varchar(20) not null,
    direction varchar(20) not null,
    operation varchar(120) not null,
    status_code integer,
    return_code varchar(60),
    request_id varchar(120),
    sanitized_payload text,
    created_at timestamptz not null default now()
);

create index idx_webmotors_sync_logs_company_created
    on webmotors_sync_logs (company_id, created_at desc);

create table webmotors_leads (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    external_lead_id varchar(180),
    vehicle_id uuid references ioauto_vehicles(id) on delete set null,
    webmotors_ad_id uuid references webmotors_ads(id) on delete set null,
    customer_name varchar(160),
    customer_email varchar(180),
    customer_phone varchar(60),
    message text,
    source varchar(40) not null,
    received_via varchar(40) not null,
    payload_json text not null,
    dedupe_key varchar(120) not null,
    received_at timestamptz not null,
    processed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_webmotors_leads_company_dedupe
    on webmotors_leads (company_id, dedupe_key);

create index idx_webmotors_leads_company_received
    on webmotors_leads (company_id, received_at desc);

create table webmotors_callback_events (
    id uuid primary key,
    company_id uuid references companies(id) on delete cascade,
    store_key varchar(80) not null default 'default',
    external_event_id varchar(180),
    payload_hash varchar(120) not null,
    headers_json text not null default '{}',
    payload_json text not null,
    status varchar(40) not null,
    processed_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index uq_webmotors_callback_events_payload_hash
    on webmotors_callback_events (payload_hash);

insert into webmotors_credentials (
    id,
    company_id,
    store_key,
    store_name,
    created_at,
    updated_at
)
select gen_random_uuid(), c.id, 'default', 'Loja principal', now(), now()
from companies c
where not exists (
    select 1
    from webmotors_credentials wc
    where wc.company_id = c.id
      and wc.store_key = 'default'
);

update ioauto_integrations
set display_name = 'Z-API / Central de Conversas WhatsApp',
    updated_at = now()
where provider_key = 'zapi';

update ioauto_integrations
set display_name = 'Webmotors / Estoque e Leads',
    updated_at = now()
where provider_key = 'webmotors';

create table ioauto_billing_subscriptions (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    provider varchar(40) not null,
    provider_customer_id varchar(180),
    provider_subscription_id varchar(180),
    provider_price_id varchar(180),
    plan_key varchar(80) not null,
    plan_name varchar(160) not null,
    status varchar(40) not null,
    amount_cents bigint,
    currency varchar(10),
    billing_interval varchar(20),
    current_period_end timestamptz,
    cancel_at_period_end boolean not null default false,
    checkout_session_id varchar(180),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ioauto_billing_subscriptions_company
    on ioauto_billing_subscriptions (company_id, updated_at desc);

create unique index uq_ioauto_billing_subscriptions_provider_subscription
    on ioauto_billing_subscriptions (provider, provider_subscription_id)
    where provider_subscription_id is not null;

create table ioauto_signup_intents (
    id uuid primary key,
    company_name varchar(160) not null,
    owner_full_name varchar(160) not null,
    email varchar(180) not null,
    whatsapp_number varchar(30),
    password_hash varchar(255) not null,
    plan_key varchar(80) not null,
    provider varchar(40) not null,
    status varchar(40) not null,
    checkout_session_id varchar(180),
    provider_customer_id varchar(180),
    provider_subscription_id varchar(180),
    provider_price_id varchar(180),
    company_id uuid references companies(id) on delete set null,
    user_id uuid references users(id) on delete set null,
    activated_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ioauto_signup_intents_email
    on ioauto_signup_intents (lower(email), created_at desc);

create unique index uq_ioauto_signup_intents_checkout_session
    on ioauto_signup_intents (checkout_session_id)
    where checkout_session_id is not null;

create table ioauto_integrations (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    provider_key varchar(60) not null,
    display_name varchar(120) not null,
    status varchar(40) not null,
    endpoint_url text,
    account_name varchar(160),
    username varchar(160),
    api_token text,
    webhook_secret text,
    settings_json text not null default '{}',
    last_sync_at timestamptz,
    last_error text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_ioauto_integrations_company_provider
    on ioauto_integrations (company_id, provider_key);

create table ioauto_vehicles (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    stock_number varchar(80),
    title varchar(200) not null,
    brand varchar(120) not null,
    model varchar(120) not null,
    version varchar(160),
    model_year integer,
    manufacture_year integer,
    price_cents bigint,
    mileage integer,
    transmission varchar(40),
    fuel_type varchar(40),
    body_type varchar(60),
    color varchar(60),
    plate_final varchar(10),
    city varchar(120),
    state varchar(20),
    featured boolean not null default false,
    status varchar(40) not null default 'DRAFT',
    description text,
    cover_image_url text,
    gallery_json text not null default '[]',
    optionals_json text not null default '[]',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ioauto_vehicles_company_updated_at
    on ioauto_vehicles (company_id, updated_at desc);

create table ioauto_vehicle_publications (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    vehicle_id uuid not null references ioauto_vehicles(id) on delete cascade,
    provider_key varchar(60) not null,
    provider_listing_id varchar(180),
    external_url text,
    status varchar(40) not null,
    last_error text,
    published_at timestamptz,
    synced_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_ioauto_vehicle_publications_vehicle_provider
    on ioauto_vehicle_publications (vehicle_id, provider_key);

create index idx_ioauto_vehicle_publications_company
    on ioauto_vehicle_publications (company_id, updated_at desc);

insert into ioauto_integrations (
    id,
    company_id,
    provider_key,
    display_name,
    status,
    settings_json,
    created_at,
    updated_at
)
select gen_random_uuid(), c.id, 'zapi', 'Z-API / WhatsApp', 'CONFIGURATION_REQUIRED', '{}', now(), now()
from companies c
where not exists (
    select 1
    from ioauto_integrations i
    where i.company_id = c.id
      and i.provider_key = 'zapi'
);

insert into ioauto_integrations (
    id,
    company_id,
    provider_key,
    display_name,
    status,
    settings_json,
    created_at,
    updated_at
)
select gen_random_uuid(), c.id, 'webmotors', 'WebMotors WebServices', 'CONFIGURATION_REQUIRED', '{}', now(), now()
from companies c
where not exists (
    select 1
    from ioauto_integrations i
    where i.company_id = c.id
      and i.provider_key = 'webmotors'
);

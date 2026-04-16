alter table companies
    add column google_calendar_email varchar(180),
    add column google_calendar_refresh_token text,
    add column google_calendar_access_token text,
    add column google_calendar_access_expires_at timestamptz,
    add column google_calendar_id varchar(180),
    add column google_calendar_connected_at timestamptz;

create index idx_companies_google_calendar_connected_at
    on companies (google_calendar_connected_at desc);


drop table if exists ai_agent_calendar_suggestion_state;

drop table if exists ai_agent_calendar_events;

drop index if exists idx_companies_google_calendar_connected_at;

alter table companies
    drop column if exists google_calendar_email,
    drop column if exists google_calendar_refresh_token,
    drop column if exists google_calendar_access_token,
    drop column if exists google_calendar_access_expires_at,
    drop column if exists google_calendar_id,
    drop column if exists google_calendar_connected_at;

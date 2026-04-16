alter table ai_supervisors
    add column if not exists notify_contact_on_agent_transfer boolean not null default false,
    add column if not exists human_handoff_team varchar(120) not null default '',
    add column if not exists human_handoff_send_message boolean not null default false,
    add column if not exists human_handoff_message text not null default '',
    add column if not exists agent_issue_handoff_team varchar(120) not null default '',
    add column if not exists agent_issue_send_message boolean not null default false;

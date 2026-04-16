create table teams (
    id uuid primary key,
    company_id uuid not null references companies(id) on delete cascade,
    name varchar(120) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_teams_company_name_lower
    on teams (company_id, lower(name));

alter table users
    add column if not exists team_id uuid references teams(id);

alter table atendimento_conversations
    add column if not exists assigned_team_id uuid references teams(id);

create index if not exists idx_users_company_team
    on users (company_id, team_id);

create index if not exists idx_atendimento_conversations_company_team
    on atendimento_conversations (company_id, assigned_team_id);

insert into teams (id, company_id, name, created_at, updated_at)
select gen_random_uuid(), c.id, 'Equipe Geral', now(), now()
from companies c;

update users u
set team_id = t.id
from teams t
where t.company_id = u.company_id
  and lower(t.name) = lower('Equipe Geral')
  and u.team_id is null;

update atendimento_conversations c
set assigned_team_id = u.team_id
from users u
where c.company_id = u.company_id
  and c.assigned_user_id = u.id
  and c.assigned_user_id is not null
  and c.assigned_team_id is null;

alter table users
    alter column team_id set not null;

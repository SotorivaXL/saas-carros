alter table atendimento_sessions
    add column if not exists responsible_team_id uuid references teams(id);

alter table atendimento_sessions
    add column if not exists responsible_team_name varchar(180);

create index if not exists idx_atendimento_sessions_company_team
    on atendimento_sessions (company_id, responsible_team_id);

update atendimento_sessions s
set responsible_team_id = u.team_id,
    responsible_team_name = t.name
from users u
join teams t
  on t.id = u.team_id
where s.company_id = u.company_id
  and s.responsible_user_id = u.id;

update atendimento_sessions s
set responsible_team_id = c.assigned_team_id,
    responsible_team_name = t.name
from atendimento_conversations c
left join teams t
  on t.id = c.assigned_team_id
where s.company_id = c.company_id
  and s.conversation_id = c.id
  and s.responsible_team_id is null
  and c.assigned_team_id is not null;

alter table atendimento_sessions
    add column if not exists sale_completed boolean not null default false;

alter table atendimento_sessions
    add column if not exists sold_vehicle_id uuid references ioauto_vehicles(id) on delete set null;

alter table atendimento_sessions
    add column if not exists sold_vehicle_title varchar(200);

alter table atendimento_sessions
    add column if not exists sale_completed_at timestamptz;

create index if not exists idx_atendimento_sessions_company_sale_completed
    on atendimento_sessions (company_id, sale_completed, sale_completed_at desc);

create index if not exists idx_atendimento_sessions_company_sold_vehicle
    on atendimento_sessions (company_id, sold_vehicle_id);

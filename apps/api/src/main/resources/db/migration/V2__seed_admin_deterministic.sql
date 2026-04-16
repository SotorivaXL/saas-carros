-- garante extensão (caso você não tenha feito V0)
create extension if not exists pgcrypto;

-- IDs fixos (fácil de referenciar e debugar)
-- company: 000...001
-- user:    000...100
-- ADMIN:   000...010

insert into companies (id, name)
values ('00000000-0000-0000-0000-000000000001', 'IO Demo Company')
    on conflict (id) do update set name = excluded.name;

insert into roles (id, name) values
                                 ('00000000-0000-0000-0000-000000000010', 'ADMIN'),
                                 ('00000000-0000-0000-0000-000000000011', 'MANAGER'),
                                 ('00000000-0000-0000-0000-000000000012', 'AGENT')
    on conflict (name) do nothing;

-- ✅ senha: Admin@123
-- hash REAL (use este abaixo)
insert into users (id, company_id, email, password_hash, full_name, is_active)
values (
           '00000000-0000-0000-0000-000000000100',
           '00000000-0000-0000-0000-000000000001',
           'admin@io.com',
           '$2a$10$WJuStJ2axeWO8ukE305FXezO7Yd88MuAVr4ahmmkG3EM7KkOsIJby',
           'Admin IO',
           true
       )
    on conflict (company_id, email) do update set
    password_hash = excluded.password_hash,
                                           full_name = excluded.full_name,
                                           is_active = excluded.is_active;

insert into roles (id, name) values
                                 ('00000000-0000-0000-0000-000000000010', 'ADMIN'),
                                 ('00000000-0000-0000-0000-000000000011', 'MANAGER'),
                                 ('00000000-0000-0000-0000-000000000012', 'AGENT')
    on conflict (name) do update set id = excluded.id;

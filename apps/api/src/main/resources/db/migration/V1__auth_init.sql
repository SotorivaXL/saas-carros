create table companies (
                           id uuid primary key,
                           name varchar(120) not null,
                           created_at timestamptz not null default now()
);

create table users (
                       id uuid primary key,
                       company_id uuid not null references companies(id),
                       email varchar(180) not null,
                       password_hash varchar(255) not null,
                       full_name varchar(180) not null,
                       is_active boolean not null default true,
                       created_at timestamptz not null default now(),
                       unique (company_id, email)
);

create table roles (
                       id uuid primary key,
                       name varchar(40) not null unique
);

create table user_roles (
                            user_id uuid not null references users(id) on delete cascade,
                            role_id uuid not null references roles(id) on delete cascade,
                            primary key (user_id, role_id)
);
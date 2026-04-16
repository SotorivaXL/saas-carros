insert into roles (id, name)
values ('00000000-0000-0000-0000-000000000013', 'SUPERADMIN')
on conflict (name) do nothing;

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
join roles r on r.name = 'SUPERADMIN'
where lower(u.email) = 'dev@iomarketing.com.br'
on conflict (user_id, role_id) do nothing;

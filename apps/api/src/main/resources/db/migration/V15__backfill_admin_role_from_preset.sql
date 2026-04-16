insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
join roles r on r.name = 'ADMIN'
where lower(coalesce(u.permission_preset, '')) = 'admin'
on conflict (user_id, role_id) do nothing;

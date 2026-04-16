create unique index if not exists ux_users_email_lower
    on users (lower(email));

create unique index if not exists ux_companies_email_lower
    on companies (lower(email))
    where email is not null;

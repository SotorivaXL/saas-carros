alter table companies
    add column business_hours_start varchar(5) not null default '09:00',
    add column business_hours_end varchar(5) not null default '18:00';

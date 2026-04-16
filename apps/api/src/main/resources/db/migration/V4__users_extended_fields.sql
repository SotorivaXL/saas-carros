alter table users
    add column profile_image_url text,
    add column job_title varchar(120),
    add column birth_date date,
    add column permission_preset varchar(30),
    add column module_permissions text;

alter table companies
    add column profile_image_url text,
    add column email varchar(180),
    add column contract_end_date date,
    add column cnpj varchar(18),
    add column opened_at date,
    add column whatsapp_number varchar(30),
    add column zapi_instance_id varchar(180),
    add column zapi_instance_token varchar(255),
    add column zapi_client_token varchar(255);

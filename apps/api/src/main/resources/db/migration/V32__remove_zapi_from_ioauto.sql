delete from ioauto_vehicle_publications
where provider_key = 'zapi';

delete from ioauto_integrations
where provider_key = 'zapi';

update companies
set whatsapp_number = '',
    zapi_instance_id = '',
    zapi_instance_token = '',
    zapi_client_token = '';

update ioauto_signup_intents
set whatsapp_number = ''
where whatsapp_number is distinct from '';

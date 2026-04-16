alter table companies
    add column business_hours_weekly_json text;

update companies
set business_hours_weekly_json = jsonb_build_object(
        'sunday', jsonb_build_object('active', false, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'monday', jsonb_build_object('active', true, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'tuesday', jsonb_build_object('active', true, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'wednesday', jsonb_build_object('active', true, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'thursday', jsonb_build_object('active', true, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'friday', jsonb_build_object('active', true, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00')),
        'saturday', jsonb_build_object('active', false, 'start', coalesce(business_hours_start, '09:00'), 'lunchStart', '12:00', 'lunchEnd', '13:00', 'end', coalesce(business_hours_end, '18:00'))
    )::text
where business_hours_weekly_json is null;

alter table ioauto_vehicles
    add column if not exists engine varchar(120);

alter table ioauto_vehicles
    add column if not exists financing_json text not null default '{}';

update ioauto_vehicles
set engine = version
where engine is null
  and version is not null;

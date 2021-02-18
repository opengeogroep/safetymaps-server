SET search_path = safetymaps, pg_catalog;

create table safetymaps.drawing(incident varchar primary key, features text, last_modified timestamp, modified_by varchar);


insert into safetymaps.role(role, protected, description) values
('incident_googlemapsnavigation', true, 'Benodigd voor Google Maps navigatie icoon bij Incident Adres');

insert into safetymaps.role(role, protected, description) values
('incidentmonitor_training', true, 'Benodigd om trainings incidenten vanuit Safety Connect ook te tonen');

insert into safetymaps.role(role, protected, description) values
('kro', true, 'Benodigd voor het laden van kro');

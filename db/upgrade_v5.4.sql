SET search_path = safetymaps, pg_catalog;

create table safetymaps.drawing(incident varchar primary key, features text, last_modified timestamp, modified_by varchar);

insert into role(role, protected, description) values
('incident_googlemapsnavigation', true, 'Benodigd voor Google Maps navigatie icoon bij Incident Adres')

insert into role(role, protected, description) values
('incidentmonitor_training', true, 'Benodigd om trainings incidenten vanuit Safety Connect ook te tonen')

insert into role(role, protected, description) values
('kladblokchat_editor', true, 'Benodigd voor het kunnen toevoegen van kladblokregels in de viewer')

insert into role(role, protected, description) values
('kladblokchat_viewer', true, 'Benodigd voor het kunnen zien van toegevoegde kladblokregels vanuit de viewer')

CREATE TABLE safetymaps.kladblok (
    id serial PRIMARY KEY,
	incident varchar NOT NULL,
	dtg timestamp NOT NULL,
	inhoud varchar NULL,
	username varchar NULL
);

create index on safetymaps.kladblok (incident);
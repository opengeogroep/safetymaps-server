
SET standard_conforming_strings = on;

CREATE SCHEMA safetymaps;

SET search_path = safetymaps, pg_catalog;

SET default_with_oids = false;

CREATE TABLE settings (
    name character varying NOT NULL,
    value text,
    primary key(name)
);

insert into settings (name, value) values ('title', 'SafetyMaps beheer');
insert into settings (name, value) values ('hide_onboard', 'true');
insert into settings (name, value) values ('linkify_enabled', 'false');
/*
INSERT INTO settings (name, value) VALUES ('static_mapserver_searchdirs', null);
INSERT INTO settings (name, value) VALUES ('static_outputdir', 'www');
INSERT INTO settings (name, value) VALUES ('static_url', '/voertuigviewer/');
INSERT INTO settings (name, value) VALUES ('static_update_command', '/usr/bin/sudo -u webdev /home/webdev/update_viewer.sh');
*/

create table user_(username varchar, password varchar, session_expiry_number int, session_expiry_timeunit varchar, details json, primary key(username));
create table role(role varchar, description text, protected boolean default false, modules text, primary key(role));
create table user_roles(username varchar, role varchar, primary key(username, role), foreign key(role) references safetymaps.role(role));

insert into safetymaps.role(role, protected, description) values
('admin', true, 'Heeft overal toegang toe'),
('viewer', true, 'Benodigd voor toegang voertuigviewer'),
('editor', true, 'Benodigd voor opslaan tekeningen'),
('safetyconnect_webservice', true, 'Benodigd voor toegang SC&T SafetyConnect webservice'),
('incidentmonitor', true, 'Benodigd voor toegang incidentmonitor'),
('incidentmonitor_kladblok', true, 'Benodigd voor tonen kladblokregels in incidentmonitor'),
('eigen_voertuignummer', true, 'Benodigd voor zelf kunnen instellen voertuignummer');

-- echo -n [password] | sha1sum
insert into user_(username, password) values ('admin', 'insert hash here');
insert into user_roles(username, role) values( 'admin', 'admin');

create table persistent_session (
    id varchar,
    username varchar not null,
    created_at timestamp not null,
    expires_at timestamp not null,
    remote_ip_login varchar not null,
    remote_ip_last varchar not null,
    last_api_call timestamp,
    login_source varchar,
    primary key(id)
);


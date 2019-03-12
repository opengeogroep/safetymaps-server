
SET standard_conforming_strings = on;

CREATE SCHEMA safetymaps;

SET search_path = safetymaps, pg_catalog;

SET default_with_oids = false;

CREATE TABLE settings (
    name character varying NOT NULL,
    value text,
    primary key(name)
);

INSERT INTO settings (name, value) VALUES ('title', 'SafetyMaps beheer');
INSERT INTO settings (name, value) VALUES ('static_mapserver_searchdirs', null);
INSERT INTO settings (name, value) VALUES ('static_outputdir', 'www');
INSERT INTO settings (name, value) VALUES ('static_url', '/voertuigviewer/');
INSERT INTO settings (name, value) VALUES ('static_update_command', '/usr/bin/sudo -u webdev /home/webdev/update_viewer.sh');

-- upgrade v1.5

SET search_path = safetymaps, pg_catalog;

create table user_(username varchar, password varchar, primary key(username));
create table user_roles(username varchar, role varchar, primary key(username, role));

-- echo -n [password] | sha1sum
insert into user_(username, password) values ('admin', 'insert hash here');
insert into user_roles(username, role) values( 'admin', 'admin');


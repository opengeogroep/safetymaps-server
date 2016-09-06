
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



SET search_path = safetymaps, pg_catalog;

create table safetymaps.drawing(incident varchar primary key, features text, last_modified timestamp, modified_by varchar);



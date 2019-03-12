SET search_path = safetymaps, pg_catalog;

create table user_(username varchar, password varchar, primary key(username));
create table user_roles(username varchar, role varchar, primary key(username, role));

-- echo -n [password] | sha1sum
insert into user_(username, password) values ('admin', 'insert hash here');
insert into user_roles(username, role) values( 'admin', 'admin');


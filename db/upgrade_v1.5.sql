SET search_path = safetymaps, pg_catalog;

create table user_(username varchar, password varchar, primary key(username));
create table user_roles(username varchar, role varchar, primary key(username, role));

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


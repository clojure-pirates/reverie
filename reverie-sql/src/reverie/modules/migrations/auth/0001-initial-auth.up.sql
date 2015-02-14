CREATE TABLE auth_role (
    id serial primary key,
    created timestamp with time zone DEFAULT now() NOT NULL,
    name text NOT NULL
);
ALTER TABLE auth_role ADD CONSTRAINT auth_role_unique_name UNIQUE(name);

CREATE TABLE auth_user (
    id serial primary key,
    created timestamp with time zone DEFAULT now() NOT NULL,
    username text NOT NULL,
    password text NOT NULL,
    email text NOT NULL,
    spoken_name text NOT NULL,
    full_name text NOT NULL,
    last_login timestamp with time zone NULL
);
ALTER TABLE auth_user ADD CONSTRAINT auth_user_unique_username UNIQUE(username);


CREATE TABLE auth_group (
    id serial primary key,
    name text NOT NULL
);
ALTER TABLE auth_group ADD CONSTRAINT auth_group_unique_name UNIQUE(name);


CREATE TABLE auth_user_group (
    user_id integer references auth_user(id),
    group_id integer references auth_group(id)
);
ALTER TABLE auth_user_group ADD CONSTRAINT auth_user_group_unique UNIQUE(user_id, group_id);


CREATE TABLE auth_user_role (
    user_id integer references auth_user(id),
    role_id integer references auth_role(id)
);
ALTER TABLE auth_user_role ADD CONSTRAINT auth_user_role_unique UNIQUE(user_id, role_id);


CREATE TABLE auth_group_role (
    group_id integer references auth_group(id),
    role_id integer references auth_role(id)
);
ALTER TABLE auth_group_role ADD CONSTRAINT auth_group_role_unique UNIQUE(group_id, role_id);

CREATE SCHEMA zimbra;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE zimbra.scripts (
  filename character varying(255) NOT NULL,
  passed timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT scripts_pkey PRIMARY KEY (filename)
);

CREATE TABLE zimbra.users (
  mailzimbra character varying(255) NOT NULL,
  uuidneo character varying(255) NOT NULL,
  CONSTRAINT users_pkey PRIMARY KEY(mailzimbra, uuidneo)
);

CREATE INDEX users_zimbra_idx ON zimbra.users (mailzimbra);
CREATE INDEX users_neo_idx ON zimbra.users (uuidneo);

CREATE TABLE zimbra.groups (
  mailzimbra character varying(255) NOT NULL,
  uuidneo character varying(255) NOT NULL,
  CONSTRAINT groups_pkey PRIMARY KEY(mailzimbra, uuidneo)
);

CREATE INDEX groups_zimbra_idx ON zimbra.groups (mailzimbra);
CREATE INDEX groups_neo_idx ON zimbra.groups (uuidneo);
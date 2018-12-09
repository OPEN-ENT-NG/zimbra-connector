CREATE TABLE zimbra.deployed_structures (
  uai character varying(16) NOT NULL,
  date_modified timestamp without time zone NOT NULL DEFAULT now(),
  is_deployed boolean DEFAULT true,
  id_logs bigint,
  CONSTRAINT deployed_structures_pkey PRIMARY KEY (uai)
);

CREATE SEQUENCE zimbra.synchro_id_seq;

CREATE TABLE zimbra.synchro (
  id bigint NOT NULL DEFAULT nextval('zimbra.synchro_id_seq'::regclass),
  date_synchro timestamp without time zone NOT NULL DEFAULT now(),
  maillinglist varchar,
  infos varchar,
  status character varying(16) NOT NULL,
  id_logs bigint,
  CONSTRAINT synchro_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE zimbra.user_synchro_id_seq;

CREATE TABLE zimbra.user_synchro (
  id bigint NOT NULL DEFAULT nextval('zimbra.user_synchro_id_seq'::regclass),
  id_user character varying(36) NOT NULL,
  id_synchro bigint,
  synchro_type character varying(16) NOT NULL,
  modif_type character varying(16) NOT NULL,
  synchro_date timestamp without time zone NOT NULL DEFAULT now(),
  status character varying(16) NOT NULL,
  id_logs bigint,
  CONSTRAINT user_synchro_pkey PRIMARY KEY (id)
);
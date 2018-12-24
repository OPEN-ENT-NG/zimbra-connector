CREATE TABLE zimbra.deployed_structures (
  uai character varying(16) NOT NULL,
  date_modified timestamp without time zone NOT NULL DEFAULT now(),
  is_deployed boolean DEFAULT true,
  CONSTRAINT deployed_structures_pkey PRIMARY KEY (uai)
);

CREATE SEQUENCE zimbra.synchro_logs_id_seq;

CREATE TABLE zimbra.synchro_logs (
  id bigint NOT NULL DEFAULT nextval('zimbra.synchro_logs_id_seq'::regclass),
  content varchar NOT NULL,
  CONSTRAINT synchro_logs_pkey PRIMARY KEY (id)
);

CREATE SEQUENCE zimbra.synchro_id_seq;

CREATE TABLE zimbra.synchro (
  id bigint NOT NULL DEFAULT nextval('zimbra.synchro_id_seq'::regclass),
  date_synchro timestamp without time zone NOT NULL DEFAULT now(),
  maillinglist varchar,
  infos varchar,
  status character varying(16) NOT NULL,
  id_logs bigint,
  CONSTRAINT synchro_pkey PRIMARY KEY (id),
  CONSTRAINT synchro_id_logs_fkey FOREIGN KEY (id_logs) REFERENCES zimbra.synchro_logs (id)
);

CREATE SEQUENCE zimbra.synchro_user_id_seq;

CREATE TABLE zimbra.synchro_user (
  id bigint NOT NULL DEFAULT nextval('zimbra.synchro_user_id_seq'::regclass),
  id_user character varying(36) NOT NULL,
  id_synchro bigint,
  synchro_type character varying(16) NOT NULL,
  synchro_action character varying(16) NOT NULL,
  synchro_date timestamp without time zone NOT NULL DEFAULT now(),
  status character varying(16) NOT NULL,
  id_logs bigint,
  CONSTRAINT synchro_user_pkey PRIMARY KEY (id),
  CONSTRAINT synchro_user_id_logs_fkey FOREIGN KEY (id_logs) REFERENCES zimbra.synchro_logs (id),
  CONSTRAINT synchro_user_id_synchro_fkey FOREIGN KEY (id_synchro) REFERENCES zimbra.synchro (id)
);

CREATE INDEX synchro_user_status ON zimbra.synchro_user (status);
CREATE INDEX synchro_user_id_user ON zimbra.synchro_user (id_user);


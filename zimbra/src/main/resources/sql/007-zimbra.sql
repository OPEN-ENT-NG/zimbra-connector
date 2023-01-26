CREATE TABLE zimbra.action(
    id bigserial NOT NULL,
    user_id bigint,
    date timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    type varchar,
    CONSTRAINT action_pkey PRIMARY KEY (id)
);
CREATE TABLE zimbra.task(
    id bigserial NOT NULL,
    action_id integer,
    status varchar,
    CONSTRAINT task_pkey PRIMARY KEY (id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.action (id)
);
CREATE TABLE zimbra.task_logs(
    id bigserial NOT NULL,
    task_id integer,
    logs varchar,
    CONSTRAINT task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT task_id_fkey FOREIGN KEY (task_id) REFERENCES zimbra.task (id)
);
CREATE TABLE zimbra.recall_recipient
(
    mail_id integer,
    receiver_id integer,
    retry int,
    CONSTRAINT mail_id_fkey FOREIGN KEY (mail_id) REFERENCES zimbra.recalled_mail (id)
) INHERITS (zimbra.task);
CREATE TABLE zimbra.recalled_mail
(
    id serial NOT NULL,
    user_id character varying(36),
    action_id integer,
    user_name character varying,
    user_mail character varying,
    structures json,
    mail_id character varying,
    object character varying,
    number_message bigint,
    comment text,
    recipient jsonb,
    mail_date character varying,
    PRIMARY KEY (mail_id, user_id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.action (id)
);


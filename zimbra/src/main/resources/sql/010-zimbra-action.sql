CREATE TABLE zimbra.actions
(
    id       bigserial NOT NULL,
    user_id  uuid,
    date     timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    type     varchar,
    approved boolean,
    CONSTRAINT action_pkey PRIMARY KEY (id)
);
CREATE TABLE zimbra.tasks
(
    id        bigserial NOT NULL,
    action_id bigint,
    status    varchar,
    CONSTRAINT task_pkey PRIMARY KEY (id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id)
);
CREATE TABLE zimbra.recall_mails
(
    id         bigserial NOT NULL,
    action_id  bigint,
    user_name  character varying,
    user_mail  character varying,
    structures json,
    object     character varying,
    comment    text,
    mail_date  character varying,
    CONSTRAINT recalled_mail_pkey PRIMARY KEY (id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id)
);
CREATE TABLE zimbra.task_logs
(
    id      bigserial NOT NULL,
    task_id bigint,
    logs    varchar,
    CONSTRAINT task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT task_id_fkey FOREIGN KEY (task_id) REFERENCES zimbra.tasks (id)
);
CREATE TABLE zimbra.recall_recipient
(
    mail_id     bigint,
    receiver_id integer,
    retry       int,
    CONSTRAINT mail_id_fkey FOREIGN KEY (mail_id) REFERENCES zimbra.recall_mails (id)
) INHERITS (zimbra.tasks);


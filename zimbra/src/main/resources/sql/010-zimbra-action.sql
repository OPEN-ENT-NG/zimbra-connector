CREATE TABLE zimbra.actions
(
    id         bigserial NOT NULL,
    user_id    uuid,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    type       varchar(32),
    approved   boolean,
    CONSTRAINT action_pkey PRIMARY KEY (id)
);
CREATE TABLE zimbra.tasks
(
    id        bigserial NOT NULL,
    action_id bigint,
    status    varchar,
    CONSTRAINT task_pkey PRIMARY KEY (id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id) ON DELETE CASCADE
);
CREATE TABLE zimbra.recall_mails
(
    id         bigserial NOT NULL,
    action_id  bigint,
    user_name  varchar(64),
    user_mail  varchar,
    message_id varchar,
    structures json,
    object     varchar,
    comment    text,
    mail_date  timestamp with time zone,
    completed boolean,
    local_mail_id varchar,
    CONSTRAINT recalled_mail_pkey PRIMARY KEY (id),
    CONSTRAINT unique_local_id UNIQUE(local_mail_id),
    CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id) ON DELETE CASCADE
);
CREATE TABLE zimbra.recall_recipient_tasks
(
    recall_mail_id bigint,
    receiver_id    uuid,
    retry          smallint,
    recipient_address varchar,
    CONSTRAINT mail_id_fkey FOREIGN KEY (recall_mail_id) REFERENCES zimbra.recall_mails (id)
) INHERITS (zimbra.tasks);
CREATE TABLE zimbra.ical_request_tasks
(
    name varchar(64),
    body varchar
) INHERITS (zimbra.tasks);
CREATE TABLE zimbra.recall_task_logs
(
    id      bigserial NOT NULL,
    recall_task_id bigint,
    logs    varchar,
    CONSTRAINT recall_task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT recall_task_id_fkey FOREIGN KEY (recall_task_id) REFERENCES zimbra.tasks(id)
);
CREATE TABLE zimbra.ical_task_logs
(
    id      bigserial NOT NULL,
    ical_task_id bigint,
    logs    varchar,
    CONSTRAINT ical_task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT ical_task_id_fkey FOREIGN KEY (ical_task_id) REFERENCES zimbra.tasks(id)
);

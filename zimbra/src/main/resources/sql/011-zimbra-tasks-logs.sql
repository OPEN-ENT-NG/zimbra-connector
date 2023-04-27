DROP TABLE zimbra.task_logs;
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

ALTER TABLE zimbra.recall_mails ADD completed boolean, ADD local_mail_id varchar;
ALTER TABLE zimbra.recall_mails ADD CONSTRAINT unique_local_id UNIQUE(local_mail_id);
ALTER TABLE zimbra.recall_recipient_tasks add recipient_address varchar;
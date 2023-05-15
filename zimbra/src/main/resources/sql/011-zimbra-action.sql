DROP TABLE zimbra.task_logs;
ALTER TABLE zimbra.tasks DROP CONSTRAINT action_id_fkey;
ALTER TABLE zimbra.tasks ADD CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id) ON DELETE CASCADE;
ALTER TABLE zimbra.recall_mails ADD completed boolean, ADD local_mail_id varchar, ADD CONSTRAINT unique_local_id UNIQUE(local_mail_id);
ALTER TABLE zimbra.recall_mails DROP CONSTRAINT action_id_fkey;
ALTER TABLE zimbra.recall_mails ADD CONSTRAINT action_id_fkey FOREIGN KEY (action_id) REFERENCES zimbra.actions (id) ON DELETE CASCADE;
ALTER TABLE zimbra.recall_recipient_tasks ADD recipient_address varchar, ADD CONSTRAINT unique_recall_task_id UNIQUE(id);
ALTER TABLE zimbra.recall_recipient_tasks DROP CONSTRAINT mail_id_fkey;
ALTER TABLE zimbra.recall_recipient_tasks ADD CONSTRAINT mail_id_fkey FOREIGN KEY (recall_mail_id) REFERENCES zimbra.recall_mails (id) ON DELETE CASCADE;
ALTER TABLE zimbra.ical_request_tasks ADD CONSTRAINT unique_ical_task_id UNIQUE(id);
CREATE TABLE zimbra.recall_task_logs
(
    id      bigserial NOT NULL,
    recall_task_id bigint,
    logs    varchar,
    CONSTRAINT recall_task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT recall_task_id_fkey FOREIGN KEY (recall_task_id) REFERENCES zimbra.recall_recipient_tasks(id) ON DELETE CASCADE
);
CREATE TABLE zimbra.ical_task_logs
(
    id      bigserial NOT NULL,
    ical_task_id bigint,
    logs    varchar,
    CONSTRAINT ical_task_logs_pkey PRIMARY KEY (id),
    CONSTRAINT ical_task_id_fkey FOREIGN KEY (ical_task_id) REFERENCES zimbra.ical_request_tasks(id) ON DELETE CASCADE
);


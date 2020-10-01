CREATE TABLE zimbra.purge_emailed_contacts (
    userid varchar NOT NULL,
    date_purge timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT purgeEmailedContacts_pkey PRIMARY KEY (userid)
);

CREATE INDEX purge_emailed_contacts_id_user ON zimbra.purge_emailed_contacts (userid);
CREATE TABLE zimbra.mail_returned
(
    id serial NOT NULL,
    user_id character varying(36),
    user_name character varying,
    structure_id character varying,
    mail_id character varying,
    object character varying,
    number_message bigint,
    statut character varying,
    comment text,
    recipient jsonb,
    mail_date character varying,
    date timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mail_id, user_id)
)


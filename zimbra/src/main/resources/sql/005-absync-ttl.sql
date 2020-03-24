CREATE TABLE zimbra.address_book_sync (
    userid varchar NOT NULL,
    date_synchro timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT abooksync_pkey PRIMARY KEY (userid)
);
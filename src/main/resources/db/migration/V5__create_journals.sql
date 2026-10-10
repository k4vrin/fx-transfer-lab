CREATE TABLE journal (
    journal_id  VARCHAR2(36 CHAR)           NOT NULL,
    transfer_id VARCHAR2(36 CHAR)           NOT NULL,
    created_at  TIMESTAMP(9) WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_journal PRIMARY KEY (journal_id),

    CONSTRAINT fk_journal_transfer
        FOREIGN KEY (transfer_id)
            REFERENCES transfer (transfer_id),

    CONSTRAINT uq_journal_transfer UNIQUE (transfer_id)
);
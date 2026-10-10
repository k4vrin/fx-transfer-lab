ALTER TABLE account
    ADD CONSTRAINT uq_account_id_currency
        UNIQUE (account_id, currency);

CREATE TABLE journal_entry
(
    entry_id   VARCHAR2(36 CHAR) NOT NULL,
    journal_id VARCHAR2(36 CHAR) NOT NULL,
    account_id VARCHAR2(36 CHAR) NOT NULL,
    direction  VARCHAR2(6 CHAR)  NOT NULL,
    amount     NUMBER(19, 2)     NOT NULL,
    currency   VARCHAR2(3 CHAR)  NOT NULL,

    CONSTRAINT pk_journal_entry
        PRIMARY KEY (entry_id),

    CONSTRAINT fk_journal_entry_journal
        FOREIGN KEY (journal_id)
            REFERENCES journal (journal_id),

    CONSTRAINT fk_journal_entry_account_currency
        FOREIGN KEY (account_id, currency)
            REFERENCES account (account_id, currency),

    CONSTRAINT ck_journal_entry_direction
        CHECK (direction IN ('DEBIT', 'CREDIT')),

    CONSTRAINT ck_journal_entry_amount
        CHECK (amount > 0)
);

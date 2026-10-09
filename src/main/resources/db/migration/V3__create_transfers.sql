CREATE TABLE transfer (
    transfer_id            VARCHAR2(36 CHAR) NOT NULL,
    customer_id            VARCHAR2(36 CHAR) NOT NULL,
    quote_id               VARCHAR2(36 CHAR) NOT NULL,
    source_account_id      VARCHAR2(36 CHAR) NOT NULL,
    destination_account_id VARCHAR2(36 CHAR) NOT NULL,
    source_currency        VARCHAR2(3 CHAR) NOT NULL,
    destination_currency   VARCHAR2(3 CHAR) NOT NULL,
    source_amount          NUMBER(19, 2) NOT NULL,
    destination_amount     NUMBER(19, 2) NOT NULL,
    fee_amount             NUMBER(19, 2) NOT NULL,
    rate                   NUMBER(19, 8) NOT NULL,
    rate_provider          VARCHAR2(100 CHAR) NOT NULL,
    rate_version           VARCHAR2(100 CHAR) NOT NULL,
    rate_as_of             TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    status                 VARCHAR2(20 CHAR) NOT NULL,
    booked_at              TIMESTAMP(9) WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_transfer
        PRIMARY KEY (transfer_id),

    CONSTRAINT fk_transfer_customer
        FOREIGN KEY (customer_id) REFERENCES customer (customer_id),

    CONSTRAINT fk_transfer_quote
        FOREIGN KEY (quote_id) REFERENCES quote (quote_id),

    CONSTRAINT uq_transfer_quote
        UNIQUE (quote_id),

    CONSTRAINT fk_transfer_source_account
        FOREIGN KEY (source_account_id) REFERENCES account (account_id),

    CONSTRAINT fk_transfer_destination_account
        FOREIGN KEY (destination_account_id) REFERENCES account (account_id),

    CONSTRAINT ck_transfer_distinct_accounts
        CHECK (source_account_id <> destination_account_id),

    CONSTRAINT ck_transfer_source_currency
        CHECK (source_currency IN ('EUR', 'USD')),

    CONSTRAINT ck_transfer_destination_currency
        CHECK (destination_currency IN ('EUR', 'USD')),

    CONSTRAINT ck_transfer_currency_pair
        CHECK (source_currency <> destination_currency),

    CONSTRAINT ck_transfer_source_amount
        CHECK (source_amount > 0),

    CONSTRAINT ck_transfer_destination_amount
        CHECK (destination_amount >= 0),

    CONSTRAINT ck_transfer_fee_amount
        CHECK (fee_amount >= 0),

    CONSTRAINT ck_transfer_rate
        CHECK (rate > 0),

    CONSTRAINT ck_transfer_rate_provider
        CHECK (REGEXP_LIKE(rate_provider, '[^[:space:]]')),

    CONSTRAINT ck_transfer_rate_version
        CHECK (REGEXP_LIKE(rate_version, '[^[:space:]]')),

    CONSTRAINT ck_transfer_status
        CHECK (status = 'BOOKED')
);

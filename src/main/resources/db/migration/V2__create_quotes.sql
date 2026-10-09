CREATE TABLE quote (
    quote_id             VARCHAR2(36 CHAR) NOT NULL,
    customer_id          VARCHAR2(36 CHAR) NOT NULL,
    source_currency      VARCHAR2(3 CHAR) NOT NULL,
    destination_currency VARCHAR2(3 CHAR) NOT NULL,
    source_amount        NUMBER(19, 2) NOT NULL,
    destination_amount   NUMBER(19, 2) NOT NULL,
    fee_amount           NUMBER(19, 2) NOT NULL,
    rate                 NUMBER(19, 8) NOT NULL,
    rate_provider        VARCHAR2(100 CHAR) NOT NULL,
    rate_version         VARCHAR2(100 CHAR) NOT NULL,
    rate_as_of           TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    created_at           TIMESTAMP(9) WITH TIME ZONE NOT NULL,
    expires_at           TIMESTAMP(9) WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_quote
        PRIMARY KEY (quote_id),

    CONSTRAINT fk_quote_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id),

    CONSTRAINT ck_quote_source_currency
        CHECK (source_currency IN ('EUR', 'USD')),

    CONSTRAINT ck_quote_destination_currency
        CHECK (destination_currency IN ('EUR', 'USD')),

    CONSTRAINT ck_quote_currency_pair
        CHECK (source_currency <> destination_currency),

    CONSTRAINT ck_quote_source_amount
        CHECK (source_amount > 0),

    CONSTRAINT ck_quote_destination_amount
        CHECK (destination_amount >= 0),

    CONSTRAINT ck_quote_fee_amount
        CHECK (fee_amount >= 0),

    CONSTRAINT ck_quote_rate
        CHECK (rate > 0),

    CONSTRAINT ck_quote_rate_provider
        CHECK (REGEXP_LIKE(rate_provider, '[^[:space:]]')),

    CONSTRAINT ck_quote_rate_version
        CHECK (REGEXP_LIKE(rate_version, '[^[:space:]]')),

    CONSTRAINT ck_quote_expiry
        CHECK (expires_at > created_at)
);

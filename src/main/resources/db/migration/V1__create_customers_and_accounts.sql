CREATE TABLE customer (
    customer_id VARCHAR2(36 CHAR) CONSTRAINT pk_customer PRIMARY KEY
);

CREATE TABLE account (
    account_id      VARCHAR2(36 CHAR) NOT NULL,
    customer_id     VARCHAR2(36 CHAR),
    account_type    VARCHAR2(30 CHAR) NOT NULL,
    currency        VARCHAR2(3 CHAR) NOT NULL,
    opening_balance NUMBER(19, 2) NOT NULL,
    booked_balance  NUMBER(19, 2) NOT NULL,

    CONSTRAINT pk_account
        PRIMARY KEY (account_id),

    CONSTRAINT fk_account_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer(customer_id),

    CONSTRAINT ck_account_type
        CHECK (account_type IN (
            'CUSTOMER_LIABILITY', 'FX_CLEARING_ASSET', 'FEE_REVENUE'
        )),

    CONSTRAINT ck_account_currency
        CHECK (currency IN ('USD', 'EUR')),

    CONSTRAINT ck_account_owner
        CHECK (
            (account_type = 'CUSTOMER_LIABILITY' AND customer_id IS NOT NULL)
            OR
            (account_type IN ('FX_CLEARING_ASSET', 'FEE_REVENUE')
                AND customer_id IS NULL)
        ),

    CONSTRAINT ck_customer_balance
        CHECK (
            account_type <> 'CUSTOMER_LIABILITY'
            OR (opening_balance >= 0 AND booked_balance >= 0)
        )
);

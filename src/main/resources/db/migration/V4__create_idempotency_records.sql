CREATE TABLE idempotency_record (
    idempotency_id         VARCHAR2(36 CHAR) NOT NULL,
    customer_id            VARCHAR2(36 CHAR) NOT NULL,
    operation              VARCHAR2(50 CHAR) NOT NULL,
    idempotency_key        VARCHAR2(100 CHAR) NOT NULL,
    request_fingerprint    VARCHAR2(64 CHAR) NOT NULL,
    quote_id               VARCHAR2(36 CHAR) NOT NULL,
    source_account_id      VARCHAR2(36 CHAR) NOT NULL,
    destination_account_id VARCHAR2(36 CHAR) NOT NULL,
    transfer_id            VARCHAR2(36 CHAR),
    status                 VARCHAR2(20 CHAR) NOT NULL,
    response_http_status   NUMBER(3),
    response_body          CLOB,
    created_at             TIMESTAMP(9) WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_idempotency_record
        PRIMARY KEY (idempotency_id),

    CONSTRAINT fk_idempotency_customer
        FOREIGN KEY (customer_id)
        REFERENCES customer (customer_id),

    CONSTRAINT uq_idempotency_scope
        UNIQUE (customer_id, operation, idempotency_key),

    CONSTRAINT ck_idempotency_fingerprint
        CHECK (
            LENGTH(request_fingerprint) = 64
            AND NOT REGEXP_LIKE(request_fingerprint, '[^0-9a-f]', 'c')
        ),

    CONSTRAINT fk_idempotency_transfer
        FOREIGN KEY (transfer_id)
        REFERENCES transfer(transfer_id),

    -- IN_PROGRESS exists only inside booking; the application must complete
    -- the record before committing it with the transfer.
    CONSTRAINT ck_idempotency_result
        CHECK (
            (
                status = 'IN_PROGRESS'
                AND transfer_id IS NULL
                AND response_http_status IS NULL
                AND response_body IS NULL
            )
            OR
            (
                status = 'SUCCEEDED'
                AND transfer_id IS NOT NULL
                AND response_http_status IS NOT NULL
                AND response_http_status = 201
                AND response_body IS NOT NULL
            )
        ),

    CONSTRAINT ck_idempotency_operation
        CHECK (operation = 'CREATE_FX_TRANSFER'),

    CONSTRAINT ck_idempotency_key
        CHECK (REGEXP_LIKE(idempotency_key, '[^[:space:]]')),

    CONSTRAINT ck_idempotency_response_json
        CHECK (response_body IS JSON STRICT)
);

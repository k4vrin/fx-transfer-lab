-- Synthetic local starting state. Never overwrite an existing account's balances.
MERGE INTO customer target
USING (
    SELECT '10000000-0000-0000-0000-000000000001' AS customer_id FROM dual
) seed
ON (target.customer_id = seed.customer_id)
WHEN NOT MATCHED THEN
    INSERT (customer_id) VALUES (seed.customer_id);

MERGE INTO account target
USING (
    SELECT '20000000-0000-0000-0000-000000000001' AS account_id,
           '10000000-0000-0000-0000-000000000001' AS customer_id,
           'CUSTOMER_LIABILITY' AS account_type, 'EUR' AS currency,
           1000.00 AS initial_balance FROM dual
    UNION ALL
    SELECT '20000000-0000-0000-0000-000000000002',
           '10000000-0000-0000-0000-000000000001',
           'CUSTOMER_LIABILITY', 'USD', 0.00 FROM dual
    UNION ALL
    SELECT '20000000-0000-0000-0000-000000000003',
           NULL, 'FX_CLEARING_ASSET', 'EUR', 0.00 FROM dual
    UNION ALL
    SELECT '20000000-0000-0000-0000-000000000004',
           NULL, 'FX_CLEARING_ASSET', 'USD', 10000.00 FROM dual
    UNION ALL
    SELECT '20000000-0000-0000-0000-000000000005',
           NULL, 'FEE_REVENUE', 'EUR', 0.00 FROM dual
) seed
ON (target.account_id = seed.account_id)
WHEN NOT MATCHED THEN
    INSERT (account_id, customer_id, account_type, currency, opening_balance, booked_balance)
    VALUES (seed.account_id, seed.customer_id, seed.account_type, seed.currency,
            seed.initial_balance, seed.initial_balance);

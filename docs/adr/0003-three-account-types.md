# ADR-0003: Use three account types and balance each currency separately

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Types constrained in the account migration; journal posting pending

## Context

A customer's deposit is an amount owed to that customer. The conversion also
needs an internal principal counterpart in each currency, and the fee must be
distinguishable from principal. A generic balance field cannot explain whether
a debit increases or decreases any of these positions.

## Decision and rationale

Give each account one currency and one explicit accounting type.

| Type | Normal balance | Debit | Credit | Purpose |
| --- | --- | --- | --- | --- |
| `CUSTOMER_LIABILITY` | Credit | Decreases | Increases | Track the amount owed to the customer |
| `FX_CLEARING_ASSET` | Debit | Increases | Decreases | Provide the synthetic principal counterpart in each currency |
| `FEE_REVENUE` | Credit | Decreases | Increases | Separate earned fees from principal |

Three types describe roles, not the number of account rows. A EUR 100.00 to
USD 110.00 conversion with a EUR 2.00 fee uses five account rows:

| Currency | Account | Debit | Credit |
| --- | --- | ---: | ---: |
| EUR | Customer source liability | 102.00 | |
| EUR | Clearing asset | | 100.00 |
| EUR | Fee revenue | | 2.00 |
| USD | Clearing asset | 110.00 | |
| USD | Customer destination liability | | 110.00 |

EUR debits and credits both total 102.00. USD debits and credits both total
110.00. Never add EUR to USD to test balance. Clearing connects the fictional
conversion's accounting groups without treating the currencies as the same unit.

Customer accounts reference a real customer row; internal accounts have no
customer owner. Do not create a fictional customer just to satisfy a foreign key.
The account ID is the primary key. The current schema does not impose one
account per customer and currency.

## Alternatives and consequences

- Customer-only entries would leave each currency's journal unbalanced.
- Combining fee and clearing would hide fee revenue inside principal movement.
- A full chart of accounts would add roles for products that do not yet exist.

These types are sufficient for the selected synthetic flow, not a universal bank
chart of accounts. Customer opening and booked balances cannot be negative.
Internal balances use the chosen sign rules and are not subject to the customer
nonnegative constraint. Clearing rows may become shared contention points.

## Evidence and revisit conditions

See [account migration](../../src/main/resources/db/migration/V1__create_customers_and_accounts.sql)
and [account sign contract](../DESIGN_DECISIONS.md#1-account-types-and-debitcredit-effects).
The migration contains type, ownership, currency, and customer-balance checks;
the [account schema tests](../../src/test/java/dev/kavrin/fxtransfer/AccountSchemaIT.java)
cover accepted rows and constraint rejection on Oracle. The posting engine
remains pending.

Revisit for external settlement, suspense, reservations, a different account
classification, or a configured posting template. Each added type needs explicit
sign rules and an example journal.

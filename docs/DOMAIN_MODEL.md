# Business Model and Invariants

This document explains the fictional domain before implementation. Names may
evolve, but an invariant should change only through a recorded design decision.

## Domain glossary

### Money

An amount paired with exactly one currency. `100 EUR` and `100 USD` are not
interchangeable and cannot be added. Money uses decimal arithmetic, a supported
scale, and an explicit rounding rule.

### Account

A place in the local ledger that holds one currency. Customer accounts belong
to one customer. Internal accounts represent the fictional FX clearing and fee
revenue sides needed to balance journal entries.

### Available balance

The amount the customer may currently spend. In this lab there are no holds or
pending settlements, so available and booked balance may initially be the same.
Do not silently add overdraft behavior.

### Quote

An immutable promise to use a particular rate and calculated amounts until an
expiry instant. A transfer stores a snapshot so later rate changes cannot alter
history.

### Exchange rate

A decimal multiplier with an explicit direction. A rate of `1.1000 USD/EUR`
means one EUR produces 1.1000 USD. Never accept a naked decimal whose direction
must be guessed.

### Fee policy

A named rule that calculates the source-currency fee. Multiple policies are a
Strategy-pattern exercise; the registry must reject missing or duplicate keys.

### Transfer

The durable record of the customer's instruction and the exact quote, fee,
accounts, and amounts used. Transfer lifecycle must not be confused with
downstream message-delivery status.

### Journal and journal entry

A journal groups immutable debit and credit entries created by one business
operation. Entries provide an audit trail. Updating an old entry to correct a
mistake destroys that trail, so corrections use new compensating entries.

### Idempotency record

A durable association between a caller-scoped key, a normalized request
fingerprint, and the original outcome. It answers an ambiguous retry without
performing the business effect twice.

### Outbox message

A message stored in the same transaction as the transfer. A later relay may
publish it more than once; downstream processing therefore needs a duplicate
strategy.

## Core invariants

### Monetary invariants

1. Every money value has a currency.
2. Amounts of different currencies cannot be added or compared as though they
   were the same unit.
3. A requested source amount must be greater than zero.
4. Rounding occurs at a named boundary and never through `double` or `float`.
5. The fee currency equals the source currency.
6. Required debit equals source amount plus fee.
7. Destination amount, rate, and rounding result are frozen in the quote.

### Quote invariants

1. Source and destination currencies differ.
2. The rate is positive and its direction is explicit.
3. Expiry is after creation.
4. A quote cannot be used after its expiry instant.
5. Transfer accounts and amount must match the quote's currencies and amount.
6. A booked transfer keeps its quote snapshot even if the live rate changes.

### Account and authorization invariants

1. Each account has exactly one currency.
2. Customer accounts have exactly one owner in the initial model.
3. The authenticated caller owns both source and destination accounts.
4. The source account currency matches the quote source currency.
5. The destination account currency matches the quote destination currency.
6. A successful debit cannot leave available balance negative.

### Transfer invariants

1. A transfer has one immutable customer, source account, destination account,
   quote snapshot, source amount, destination amount, and fee.
2. A transfer is booked at most once.
3. Transfer status changes only through named domain operations.
4. A booked transfer is not edited or deleted.
5. Reversal, when implemented, is a new linked operation and is itself
   idempotent.

### Idempotency invariants

1. Key scope is `(customer ID, operation, idempotency key)`.
2. The same key and same normalized request return the original result.
3. The same key and a different fingerprint produce a conflict.
4. A database unique constraint closes concurrent key reuse.
5. The idempotency outcome commits atomically with the transfer effect.

### Ledger invariants

1. Journal entries are immutable.
2. Every journal contains at least two entries.
3. Within each currency, total debit equals total credit.
4. Values in different currencies are never summed to test balance.
5. A transfer and all its C2 journal entries commit or roll back together.
6. Each journal can be traced to one business operation.

### Concurrency invariants

1. Concurrent transfers cannot overspend the same source account.
2. Correctness comes from the database transaction and locking/constraint
   strategy, not from an in-memory Java check alone.
3. Lock ordering is deterministic whenever an operation locks multiple rows.
4. A concurrency test must coordinate the race; merely running two tests does
   not prove that the critical overlap occurred.

### Outbox invariants

1. The transfer and its outbox row commit atomically.
2. Publishing does not happen inside the booking transaction.
3. A relay retry may publish the same message more than once.
4. A message has a stable ID and business correlation ID.
5. Delivery status does not rewrite whether the transfer was booked.

## Suggested lifecycle

Keep the initial lifecycle small:

```text
request accepted -> BOOKED
BOOKED -> REVERSED       (later milestone only)
```

Invalid requests do not become successful transfer records. In C3, model
message delivery separately:

```text
PENDING -> SENT
PENDING -> RETRYABLE_FAILURE -> PENDING
```

Do not add states merely because a real bank might have them. Add a state only
when the lab has behavior, an allowed transition, and a test for it.

## Questions you must answer during C0

- What are the supported currency scales and rounding mode?
- Does quote expiry use `isAfter`, `isBefore`, or an inclusive boundary at the
  exact expiry instant?
- What normalized fields form the idempotency fingerprint?
- What account types exist, and how does debit/credit affect each balance?
- Which row or rows are locked during booking, and in what order?
- What HTTP result represents an expired quote, insufficient funds, ownership
  failure, same-key/different-payload conflict, and an unknown transfer?

# FX Transfer Lab — Minimal Product Requirements

[نسخه فارسی](PRD.fa.md)

## 1. Purpose

FX Transfer Lab is a fictional backend in which one authenticated customer
moves value from an internal source account to an internal destination account
held in a different currency.

The product exists to practice precise domain modeling, REST contracts, local
transactions, idempotency, concurrency, double-entry bookkeeping, and reliable
message handoff. It does not reproduce any employer's system.

## 2. The business problem in plain language

Suppose a customer owns a EUR account and a USD account. They want to spend EUR
and receive USD. Before accepting the transfer, the system gives them a quote:

- the source amount;
- the EUR-to-USD exchange rate;
- the destination amount produced by that rate;
- the fee charged in EUR; and
- the time at which the quote expires.

When the customer submits the transfer, the system must use the quoted values,
not a newer rate. It must take the source amount and fee only once, even if the
client retries after a timeout. It must also leave an auditable journal showing
where every amount went.

This lab models an **internal book transfer**. Both customer accounts and the
fictional clearing accounts are maintained in the same database. Moving money
to another bank, settling with a real counterparty, sanctions screening,
liquidity management, market execution, and regulatory reporting are outside
the model.

## 3. Actors

- **Customer:** owns the source and destination accounts and requests a quote
  and transfer.
- **FX Transfer API:** validates the request and exposes its result.
- **Transfer application service:** coordinates authorization, idempotency,
  account locking, booking, and the database transaction.
- **Quote policy:** creates an immutable, expiring rate snapshot.
- **Fee policy:** calculates a fee in the source currency.
- **Ledger:** records balanced debit and credit entries for auditability.
- **Outbox relay:** introduced later; forwards a committed message to a fake
  gateway without making the database transaction depend on the network.

## 4. Initial assumptions

These are lab choices, not universal banking rules:

1. The authenticated customer owns both accounts.
2. Source and destination currencies must differ.
3. The first supported pair is EUR to USD; support for more pairs is an
   extension, not a C1 requirement.
4. The fee is charged **on top of** the source amount and is denominated in the
   source currency.
5. A quote is immutable and has an explicit expiry instant.
6. The destination amount is calculated once when the quote is issued and is
   stored with the quote and transfer.
7. Money calculations use decimal arithmetic and an explicit currency scale and
   rounding mode. Binary floating-point is forbidden.
8. A booked transfer is never edited or deleted. A later correction is a new,
   linked reversal with compensating journal entries.
9. All C2 booking data is committed in one Oracle transaction.
10. External settlement and message delivery are separate later concerns.

Any changed assumption must be captured in a short architecture decision record
before code and tests are changed.

## 5. Functional requirements

### FR-1 — Obtain a quote

The customer supplies source currency, destination currency, and a positive
source amount. The result contains a quote ID, rate, source amount, destination
amount, fee, creation time, and expiry time.

### FR-2 — Create a transfer

The customer submits a quote ID, source account ID, destination account ID, and
idempotency key. A successful request returns the transfer ID and the exact
monetary snapshot used for booking.

### FR-3 — Enforce ownership

The caller must own both customer accounts. A controller-supplied account ID is
not proof of ownership; the application must compare it with the authenticated
customer identity.

### FR-4 — Reject invalid or stale work

Reject non-positive amounts, same-currency transfers, unsupported pairs,
missing accounts, ownership violations, expired quotes, quote/request
mismatches, and insufficient funds.

### FR-5 — Make retries safe

An idempotency key is scoped to customer and operation. Repeating the same key
with the same normalized request returns the original outcome. Reusing the key
with a different request is a conflict. A database constraint, not only a
read-before-write check, must close the race.

### FR-6 — Book atomically

Idempotency state, transfer state, account effects, and journal entries either
commit together or do not commit. Two concurrent debits must not make the source
account negative.

### FR-7 — Preserve a balanced journal

For every currency in a booking, total debits must equal total credits. The
cross-currency rate connects two balanced currency groups; currencies are never
summed together to fake one balance.

### FR-8 — Retrieve safely

A customer can retrieve their own transfer by ID and, later, list their
transfers using a bounded page size and stable ordering.

### FR-9 — Hand off a message reliably

In C3, an outbox row is committed with the transfer. Network publication occurs
afterward and may be retried or duplicated. The booking transaction must not
depend on direct network success.

## 6. Example booking

Given a quote for EUR 100.00 at 1.1000 USD/EUR with a EUR 2.00 fee:

- the customer source account is reduced by EUR 102.00;
- the customer destination account is increased by USD 110.00;
- the EUR journal balances EUR 102.00 of debits and credits; and
- the USD journal balances USD 110.00 of debits and credits.

One possible fictional journal is:

| Currency | Debit | Credit |
| --- | ---: | ---: |
| EUR | Customer source 102.00 | FX clearing 100.00 + fee revenue 2.00 |
| USD | FX clearing 110.00 | Customer destination 110.00 |

The exact account-type sign rules must be written before implementation. The
important invariant is balanced entries per currency and a customer balance
effect consistent with those rules.

## 7. Non-functional requirements

- Use Java 21 and the Maven Wrapper.
- Use one Spring Boot process and one Oracle database through C3.
- Keep controllers thin and do not expose persistence entities as API models.
- Store timestamps as instants and evaluate expiry using an injectable clock.
- Do not log credentials, tokens, full sensitive payloads, or secrets.
- Database schema changes must be versioned Flyway migrations.
- Integration evidence must name the real database used; H2 evidence cannot
  establish Oracle locking or query-plan behavior.

## 8. Out of scope

- Real SWIFT messages or connectivity
- Core-banking integration
- Real market rates or trade execution
- Beneficiary banks and cross-border settlement
- AML, KYC, sanctions, fraud, tax, or regulatory reporting
- Documentary credits, guarantees, chargebacks, and disputes
- Multi-region availability and production deployment
- A frontend or mobile application

## 9. Minimum useful result

C2 is the minimum useful stopping point: a database-backed, idempotent transfer
with ownership checks, concurrency protection, and balanced per-currency
journal entries. Later milestones add failure delivery, diagnostics, and oral
defense; they do not repair missing C2 correctness.

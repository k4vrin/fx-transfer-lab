# Foundation Design Decisions

[نسخه فارسی](DESIGN_DECISIONS.fa.md)

These decisions define the first useful version of FX Transfer Lab. They are
project assumptions, not claims about Exir, SWIFT, central-bank rules, or a
particular core-banking product.

## Decision summary

| # | Question | Decision |
| ---: | --- | --- |
| 1 | How do debits and credits affect balances? | Use explicit account types and sign rules; never infer the effect from a name. |
| 2 | Is balance derived or stored? | The immutable ledger is authoritative; a stored booked balance is a synchronous, reconcilable projection. |
| 3 | What are scale and rounding rules? | The foundation supports EUR and USD at scale 2; rates use scale 8; monetary rounding is `HALF_EVEN`. |
| 4 | Can a quote fund multiple transfers? | No. A quote is single-use after one successful booking. |
| 5 | Is the exact expiry instant valid? | No. A quote is valid only while `now < expiresAt`. |
| 6 | What forms the idempotency fingerprint? | A versioned canonical representation of quote and account IDs, hashed with SHA-256. |
| 7 | Are deterministic failures cached? | No. The foundation commits only successful outcomes; a rolled-back failure may be retried. |
| 8 | What is locked, and in what order? | Reserve the idempotency key, lock the quote, then lock every affected account in ascending account-ID order. |
| 9 | What are the HTTP statuses and error codes? | Use Problem Details plus stable project codes; the matrix below is normative. |
| 10 | How do reversals work? | Not in the foundation release. Later: full amount only, at most once, with linked compensating entries and its own idempotency key. |
| 11 | What is the initial authentication mechanism? | OAuth2 Resource Server with JWT access tokens; object ownership remains an application rule. |
| 12 | Where do exchange rates come from? | A trusted internal `RateProvider` port backed initially by deterministic fixtures, never by client input. |

## 1. Account types and debit/credit effects

The initial ledger has three account types:

| Account type | Normal balance | Debit effect | Credit effect |
| --- | --- | --- | --- |
| `CUSTOMER_LIABILITY` | Credit | Decreases the amount owed to the customer | Increases the amount owed to the customer |
| `FX_CLEARING_ASSET` | Debit | Increases the internal clearing asset | Decreases the internal clearing asset |
| `FEE_REVENUE` | Credit | Decreases or reverses fee revenue | Increases fee revenue |

For a EUR 100.00 to USD 110.00 transfer with a EUR 2.00 fee, the journal is:

- debit EUR customer liability 102.00;
- credit EUR FX clearing asset 100.00;
- credit EUR fee revenue 2.00;
- debit USD FX clearing asset 110.00; and
- credit USD customer liability 110.00.

Each currency balances independently. The exchange rate relates the two currency
groups but does not make EUR and USD arithmetically interchangeable. Internal
clearing balances are seeded synthetic inventory; the foundation does not model
market execution, liquidity, or external settlement.

## 2. Ledger truth and stored balances

Immutable journal entries are the financial source of truth. Each account also
stores `booked_balance` so booking can lock one row and check funds efficiently.
That column is a synchronous projection, not a second independent truth.

The same Oracle transaction must insert the journal and update all affected
booked balances. A reconciliation query must be able to recompute each balance
from its opening balance and journal entries. Asynchronous balance projection is
out of scope for the foundation release.

## 3. Currency precision and rounding

- The foundation supports only EUR and USD; each amount has scale 2.
- An exchange rate has scale 8 and explicit source/destination direction.
- Use `BigDecimal` and `RoundingMode.HALF_EVEN`; never use `double` or `float`.
- Normalize money at construction and reject a value that cannot be represented
  under the currency contract unless the operation explicitly performs rounding.
- Calculate destination amount as `sourceAmount * rate`, then round once to the
  destination scale.
- Calculate a percentage fee from the unrounded multiplication, then round once
  to the source scale. Do not round intermediate operands.

This is a deliberately small foundation currency catalog. Supporting currencies
with different minor units requires a catalog change and changed-case tests.

## 4. Quote consumption

A quote funds at most one successfully booked transfer. Enforce that with a
unique constraint on the transfer's `quote_id`, not only a prior lookup. A
failed transaction does not consume the quote because all effects roll back.
An idempotent replay returns the original transfer and is not a second use.

## 5. Expiry boundary

A quote is valid only when `clock.instant().isBefore(expiresAt)`. At exactly
`expiresAt`, it is expired. Capture `now` once inside the use case and use an
injectable `Clock` so the boundary has a deterministic test.

## 6. Idempotency scope and fingerprint

The uniqueness scope is:

```text
(authenticated customer ID, CREATE_FX_TRANSFER, Idempotency-Key)
```

The version-1 fingerprint includes these normalized fields in this exact order:

```text
v1 | quoteId | sourceAccountId | destinationAccountId
```

Use canonical UUID text, UTF-8, explicit field names or length-prefixing, and
SHA-256. Do not hash arbitrary JSON whose property order or formatting may
change. Persist the normalized fields as well as the fingerprint so conflicts
remain diagnosable without logging the request body. The authenticated customer
and operation are in the database key scope, not duplicated in the fingerprint.

## 7. Failure caching

The foundation does not cache business failures. Insufficient funds can change,
and a validation failure creates no financial effect. The idempotency reservation is
inserted inside the booking transaction, so it rolls back with a failed booking
and the client may retry.

Only a `SUCCEEDED` idempotency record is visible after commit; it stores the
original successful HTTP status and response snapshot. A concurrent duplicate
contends on the unique index. After the first transaction commits, the loser
reloads and either replays the successful result or reports a changed-payload
conflict. If the first transaction rolls back, the waiting request may proceed.
This avoids a stranded, committed `IN_PROGRESS` record. Bounded lock
timeouts remain infrastructure failures, not cached business outcomes.

## 8. Transaction and lock order

Within one `@Transactional` application use case:

1. insert or resolve the caller-scoped idempotency reservation;
2. lock the quote row for update and verify owner, match, unused state, and
   expiry;
3. determine every affected customer, clearing, and fee account;
4. lock all affected account rows in ascending stable account-ID order;
5. recheck sufficient funds against the locked source balance;
6. create the transfer and balanced journal, update booked balances, and store
   the successful idempotency response; and
7. commit everything together.

The deterministic account order reduces deadlock risk. Database unique
constraints on idempotency scope and quote use close races that application
checks alone cannot close. The shared clearing rows may become hot under load;
that is a known foundation trade-off, not by itself a reason to split services.

## 9. HTTP and stable error contract

Error responses use `application/problem+json` and include `status`, `title`,
stable `code`, and `traceId`. They must not leak another customer's resource.

| Situation | HTTP | Stable code |
| --- | ---: | --- |
| Quote created | `201` | — |
| Transfer created or same-request replayed | `201` | — |
| Transfer retrieved | `200` | — |
| Malformed JSON or field validation | `400` | `INVALID_REQUEST` |
| Missing, invalid, expired, wrong-issuer, or wrong-audience token | `401` | `AUTHENTICATION_REQUIRED` |
| Valid token without required scope | `403` | `INSUFFICIENT_SCOPE` |
| Account is not owned by caller | `403` | `ACCOUNT_ACCESS_DENIED` |
| Caller-visible account, quote, or transfer not found | `404` | `RESOURCE_NOT_FOUND` |
| Same idempotency key with changed request | `409` | `IDEMPOTENCY_KEY_REUSED` |
| Quote already funded another transfer | `409` | `QUOTE_ALREADY_USED` |
| Expired quote | `422` | `QUOTE_EXPIRED` |
| Quote/account/currency mismatch | `422` | `QUOTE_MISMATCH` |
| Insufficient available funds | `422` | `INSUFFICIENT_FUNDS` |
| Unsupported currency pair | `422` | `UNSUPPORTED_CURRENCY_PAIR` |
| Unexpected server failure | `500` | `INTERNAL_ERROR` |

A replay returns the original `201` response and adds
`Idempotency-Replayed: true`. Do not invent a new transfer or recalculate its
money snapshot.

## 10. Reversal contract

Reversal is intentionally absent from the foundation release. When added, it has
these rules:

- only a `BOOKED` transfer may be reversed;
- reversal is full, never partial, and uses the original amounts, currencies,
  rate snapshot, and fee;
- exactly one successful reversal may link to an original transfer, enforced by
  a unique constraint;
- reversal creates a new transfer operation and exact opposite journal entries;
- original entries and transfer data are never edited or deleted; and
- reversal has its own caller-scoped idempotency key.

Outbound message state remains separate. Reversing a booked instruction does
not pretend that an already sent message never existed.

## 11. Authentication and authorization

Use Spring Security as an OAuth2 Resource Server that accepts JWT **access
tokens**. The project does not implement an authorization server. Local tests use
test-signed or mocked JWTs with no real secrets committed.

Validate signature/key trust, issuer, audience, expiry/not-before, and the
required `fx-transfer:read` or `fx-transfer:write` scope. In the foundation, JWT
`sub` is the stable customer UUID. A valid token proves neither account ownership nor
permission to operate on an arbitrary resource: the application service must
load the account and compare its owner to the authenticated customer.

## 12. Rate source and trust

Application code depends on a `RateProvider` port. The initial adapter reads a
deterministic, operator-controlled fixture or database table. A quote stores the
provider rate ID/version, direction, rate, `asOf`, and expiry. The client never
submits or overrides the rate used for booking.

An optional later SOAP adapter may call a simulated rate service through the
same port as an institutional integration example. It must add authentication,
timeout, freshness, and failure mapping before it could be
trusted, and it still proves no real market connectivity.

## Planned core-banking boundary

These decisions apply while the foundation owns synthetic customer accounts and
can book all financial effects in one Oracle transaction. The product roadmap
later moves authoritative accounts, balances, reservations, and final postings
to a separate core-banking system.

That evolution does not remove account or ledger concepts. Account becomes an
access and integration boundary; ledger becomes the FX subledger and the source
of posting instructions and reconciliation evidence. The change requires a new
ADR because it replaces one local atomic transaction with an idempotent
reservation, posting, status-inquiry, and reconciliation workflow.

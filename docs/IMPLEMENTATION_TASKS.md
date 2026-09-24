# Implementation Tasks

This is the coding route for the learner. Complete tasks in order unless a task
explicitly says it is optional. Check a box only when its acceptance evidence
exists; creating a class or making the project compile is not enough.

The detailed business answers are in [C0 Design Decisions](DESIGN_DECISIONS.md).
Do not silently choose different semantics while coding. Propose a short ADR
first if implementation evidence shows that a decision should change.

## How to work with this list

For each task:

1. make a small independent design or first attempt before asking for a worked
   solution;
2. implement only the named behavior;
3. run the acceptance tests and record the command and result; and
4. ask for review with the task ID, diff, and failing/passing evidence.

Use this evidence record:

```text
Task:
First attempt or design:
Expected result:
Command/test:
Observed result:
First failure and correction:
Changed-case retest:
Evidence: Design | Unit | Oracle integration | Runtime | Oral defense
```

## Phase 0 — Understand and fix the contract

### T00 — Restate the business flow

- [ ] Read the PRD, domain model, and C0 design decisions in either language.
- [ ] Write the happy path in your own words from JWT-authenticated request to
      Oracle commit.
- [ ] Draw the quote, transfer, five account effects, and two balanced currency
      groups.
- [ ] Explain why the project is an internal book transfer and not SWIFT.

**Acceptance:** a five-minute explanation correctly distinguishes quote,
booking, ledger truth, stored balance, and later message delivery.

### T01 — Record the modular-monolith decision

- [ ] Create `docs/adr/0001-start-as-a-modular-monolith.md` in your own words.
- [ ] State the decision, forces, rejected early microservice split, and triggers
      that could justify a future split.
- [ ] Include the shared Oracle transaction as the main C2 correctness benefit.

**Acceptance:** the ADR names at least one cost of the chosen design and one
failure mode introduced by a distributed alternative.

## Phase 1 — Pure Java domain

### T10 — Implement currency-safe `Money`

**Package:** `dev.kavrin.fxtransfer.shared`

- [ ] Implement an immutable `Money` value for EUR and USD scale 2.
- [ ] Reject null currency, negative scale violations, and currency-mismatched
      arithmetic.
- [ ] Provide only behavior needed by later tasks: add, subtract, compare, and
      zero/positive checks.

**Tests:** equality; add/subtract same currency; mismatched currency rejected;
invalid scale rejected; no `double` constructor.

**Acceptance:** unit tests pass without Spring. Explain why `BigDecimal.equals`
and `compareTo` have different scale behavior and how your contract handles it.

### T11 — Implement directed exchange rates

**Package:** `dev.kavrin.fxtransfer.quote`

- [ ] Model rate direction explicitly as source and destination currency.
- [ ] Store a positive rate at scale 8.
- [ ] Convert source money and round once to destination scale with
      `HALF_EVEN`.

**Tests:** correct EUR-to-USD conversion; reversed direction rejected; zero or
negative rate rejected; awkward decimal demonstrates one rounding boundary.

### T12 — Implement fee policies and registry

**Package:** `dev.kavrin.fxtransfer.fee`

- [ ] Define a named `FeePolicy` domain contract.
- [ ] Implement fixed-fee and percentage-fee policies.
- [ ] Implement one registry/factory that rejects missing and duplicate keys.

**Tests:** fee stays in source currency; percentage rounding; missing key;
duplicate registration; a third test-only policy works without editing a
consumer.

**Job-posting evidence:** advanced OOP, composition, Strategy, factory/registry,
and a concrete reason not to use inheritance.

### T13 — Implement immutable quotes

**Package:** `dev.kavrin.fxtransfer.quote`

- [ ] Create a quote from source amount, directed rate, fee policy, `Clock`, and
      validity duration.
- [ ] Freeze source amount, destination amount, fee, rate source/version,
      `asOf`, creation, and expiry.
- [ ] Implement `isExpired(now)` with the exact-instant rule.

**Tests:** exact expiry instant; one nanosecond before expiry; immutable snapshot
after provider rate changes; source/destination mismatch; non-positive amount.

## Phase 2 — Oracle schema and persistence boundaries

### T20 — Design and migrate the C2 schema

**Packages:** capability infrastructure packages; **tool:** Flyway

- [ ] Draw tables for customer account, rate/quote, transfer, idempotency,
      journal, and journal entry before writing SQL.
- [ ] Add opening balance and `booked_balance` to accounts.
- [ ] Add primary keys, foreign keys, amount checks, enum checks, and timestamps.
- [ ] Add unique constraints for idempotency scope, quote use, and business
      operation to journal linkage.
- [ ] Seed only synthetic customers, accounts, clearing inventory, fee account,
      and deterministic rates in the local profile.

**Acceptance:** migration applies to Oracle Testcontainers from an empty schema;
an invalid row is rejected by each important constraint. Record which rules
remain Java-only and why.

### T21 — Map persistence without leaking JPA into the API

- [ ] Add JPA entities and repository adapters inside their owning capability.
- [ ] Keep API DTOs and domain values separate from JPA entities.
- [ ] Add optimistic version fields where useful, but do not substitute them for
      the required pessimistic booking locks.
- [ ] Implement a reconciliation query for opening balance plus journal effects
      versus stored booked balance.

**Tests:** round-trip mappings preserve amount scale, currency, instant, and
account type; reconciliation succeeds for seeded accounts.

## Phase 3 — Security and API boundary

### T30 — Configure JWT resource-server authentication

**Package:** a security adapter under `dev.kavrin.fxtransfer.shared` or a small
top-level `security` capability if it grows.

- [ ] Add Spring Security OAuth2 Resource Server JWT support.
- [ ] Validate issuer, audience, time claims, and signature trust.
- [ ] Map JWT `sub` to a typed customer ID.
- [ ] Require `fx-transfer:read` and `fx-transfer:write` scopes by endpoint.
- [ ] Use mocked or test-signed JWTs; do not build an authorization server.

**Tests:** missing token `401`; wrong issuer/audience `401`; missing scope `403`;
valid token reaches the application boundary.

### T31 — Implement the quote REST endpoint

**Packages:** `quote.api`, `quote.application`, and existing quote domain

- [ ] Implement `POST /api/v1/fx-quotes` with validation and thin mapping.
- [ ] Obtain the rate from `RateProvider`; never accept it from the request.
- [ ] Return `201` and the immutable quote snapshot.
- [ ] Map unsupported pair and invalid amount to the documented Problem Details.

**Tests:** happy path, malformed request, unsupported pair, and caller-supplied
rate cannot influence the result.

## Phase 4 — Minimum useful transfer and ledger

### T40 — Implement account ownership and access policy

**Package:** `dev.kavrin.fxtransfer.account`

- [ ] Load both accounts by ID and compare their owners with JWT `sub`.
- [ ] Verify source/destination currencies against the quote.
- [ ] Return the safe ownership/not-found errors from the documented contract.

**Tests:** own accounts allowed; either foreign account denied; a body or header
customer ID cannot override the authenticated identity.

### T41 — Implement canonical idempotency fingerprinting

**Package:** `transfer.application` and `transfer.infrastructure`

- [ ] Build the exact `v1` canonical representation from the three decided IDs.
- [ ] Hash it with SHA-256 and persist normalized fields for diagnosis.
- [ ] Enforce the caller/operation/key unique constraint.
- [ ] Store and replay the original successful status and response snapshot.
- [ ] Resolve a concurrent unique-key race after the winning transaction commits
      or rolls back; do not leave a committed `IN_PROGRESS` record.

**Tests:** same semantic fields produce same fingerprint; one field changed
produces a different fingerprint; same key/same request replays; same key/new
request returns `409 IDEMPOTENCY_KEY_REUSED`.

### T42 — Implement journals and account effects

**Package:** `dev.kavrin.fxtransfer.ledger`

- [ ] Model immutable debit/credit entries and the three account-type effects.
- [ ] Reject a journal with fewer than two entries or imbalance in any currency.
- [ ] Produce the five-entry example for transfer plus fee.
- [ ] Apply the same entries to the synchronous booked-balance projection.

**Tests:** example effects; per-currency balance; cross-currency totals never
summed; tampered imbalance rejected; reconciliation matches after booking.

### T43 — Implement the transactional booking use case

**Package:** `transfer.application`

- [ ] Follow the exact reservation and lock order from the C0 decisions.
- [ ] Recheck quote expiry, use, ownership, match, and funds after locks.
- [ ] Create transfer, account effects, journal, and successful idempotency
      outcome in one transaction.
- [ ] Keep controllers, direct network calls, and message publication outside
      this transaction.

**Tests on Oracle:** successful booking; expired quote; insufficient funds;
quote mismatch; quote used twice; forced exception rolls back every artifact.

### T44 — Expose create and retrieve endpoints

**Package:** `transfer.api`

- [ ] Implement `POST /api/v1/fx-transfers` using the `Idempotency-Key` header.
- [ ] Implement `GET /api/v1/fx-transfers/{id}` with ownership protection.
- [ ] Return the frozen monetary snapshot and the stable error matrix.
- [ ] Add `Idempotency-Replayed: true` to a replayed original `201` response.

**Tests:** every row in the C0 HTTP matrix that applies to C2, including no
cross-customer data leak.

### T45 — Prove concurrency, do not merely invoke threads

- [ ] Write an Oracle-backed test that uses barriers/latches to overlap two
      debit transactions after they start but before either commits.
- [ ] Prove competing debits cannot make the source balance negative.
- [ ] Prove duplicate keys create one transfer and one journal.
- [ ] Prove competing requests for one quote create one transfer.
- [ ] Capture and explain any Oracle lock timeout or unique-constraint exception
      translated by the application.

**Acceptance:** `./mvnw verify` passes repeatedly against Oracle Testcontainers,
and the test records that the critical overlap actually occurred.

**C2 stop point:** after T45, the project is minimally useful. Complete and
defend this phase before optional integrations.

## Phase 5 — Distributed failure and retrieval performance

### T50 — Add a transactional outbox

**Package:** `dev.kavrin.fxtransfer.outbox`

- [ ] Insert an outbox event in the transfer transaction.
- [ ] Relay it later to a fake gateway with stable event/correlation IDs.
- [ ] Model bounded retry and observable terminal failure separately from
      transfer status.

**Tests:** rollback produces no event; commit plus publish failure retains the
event; duplicate relay is safe; explain why this is at-least-once, not exactly
once.

### T51 — Add bounded transfer listing and inspect Hibernate SQL

- [ ] Implement a customer-owned list with bounded page size and stable
      `(createdAt, id)` ordering.
- [ ] Record generated SQL and query count.
- [ ] Introduce or identify one N+1 case, then fix it with a justified projection
      or fetch strategy.

**Acceptance:** result ordering is stable and the measured query count supports
the explanation.

### T52 — Measure one Oracle index and the connection pool

- [ ] Capture representative row counts and an executed Oracle plan before an
      index change.
- [ ] Add one justified index, verify result parity, and compare actual rows and
      work evidence.
- [ ] Run controlled load and observe connection hold time, wait time, active,
      idle, and timeout metrics.
- [ ] Explain why increasing pool size is not the default repair.

**Acceptance:** attach observed commands/output; record `not run` if privileges
are unavailable rather than claiming verification.

## Phase 6 — Optional job-description extensions

### T60 — Add a thin SOAP adapter

- [ ] Define one contract-first quote or transfer-status operation and Fault.
- [ ] Map it to the existing application use case; do not duplicate domain
      behavior.
- [ ] Test one existing client contract against a backward-compatible optional
      field.

**Boundary:** this is protocol practice, not SWIFT messaging.

### T61 — Add a fake SOAP `RateProvider` adapter

- [ ] Keep the existing `RateProvider` port unchanged.
- [ ] Add authentication, timeout, stale-rate rejection, and fault mapping.
- [ ] Test timeout and malformed/fault responses without using a live provider.

### T62 — Implement full reversal

- [ ] Re-read decision 10 before coding.
- [ ] Add a new idempotent operation with exact compensating entries.
- [ ] Enforce at most one successful reversal with a database constraint.
- [ ] Preserve the original transfer, journal, and message history.

## Phase 7 — Final defense

### T70 — Build the evidence table and oral walkthrough

- [ ] Give a ten-minute architecture walkthrough without notes.
- [ ] Trace a successful request, a duplicate retry, and concurrent overspend.
- [ ] Explain JWT validation versus account ownership.
- [ ] Explain stored balance versus ledger truth and demonstrate reconciliation.
- [ ] Explain the outbox crash windows and duplicate delivery.
- [ ] Critique the hot clearing-account row and state when partitioning or a new
      consistency boundary would be justified.
- [ ] Separate design, compiled code, unit tests, Oracle tests, runtime
      observation, and unimplemented ideas in the final evidence table.

**Completion boundary:** this project demonstrates a synthetic internal transfer
and ledger. It does not demonstrate regulatory compliance, external settlement,
live FX pricing, real SOAP interoperability, or SWIFT connectivity.

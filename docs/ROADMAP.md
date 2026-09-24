# Implementation Roadmap

This is a learning checklist, not a race. Check an item only after its stated
evidence exists. Preserve your first independent attempt before asking for a
worked solution.

The twelve C0 choices are now resolved in
[C0 Design Decisions](DESIGN_DECISIONS.md). Use the smaller, reviewable task IDs
in [Implementation Tasks](IMPLEMENTATION_TASKS.md) for day-to-day coding. This
roadmap remains the milestone-level view.

## Evidence legend

- **Design:** a written decision or diagram; it is not executed behavior.
- **Unit:** a fast test of domain behavior without Spring or Oracle.
- **Integration:** an executed test against Oracle through Testcontainers.
- **Runtime:** an observed running request, query, plan, or failure.
- **Defense:** an unaided spoken explanation and changed-case answer.

## C0 — Scope and design

Target: understand the fictional business and make its assumptions explicit.

- [ ] Read `README.md`, `docs/PRD.md`, and `docs/DOMAIN_MODEL.md`.
- [ ] Rewrite the transfer flow in your own words without looking.
- [ ] Draw the happy-path sequence from HTTP request through database commit.
- [x] Choose supported currencies, scale, and rounding mode.
- [x] Define debit/credit balance effects for customer, clearing, and fee
      accounts.
- [x] Decide the exact quote-expiry boundary.
- [x] Define the normalized idempotency fingerprint.
- [x] Write the HTTP status/error-code matrix.
- [x] Define initial transfer and message-delivery states separately.
- [ ] Add `docs/adr/0001-start-as-a-modular-monolith.md` in your own words.
- [ ] Explain why one application and one database are enough for C1-C3.

Exit evidence:

- [ ] One-page design reviewed without unresolved invariant contradictions.
- [ ] Five-minute oral explanation with no claim of real banking behavior.

## C1 — Money, quotes, and fee policies

Target: model immutable values and strategy selection before persistence.

Preparation:

- [ ] Complete the relevant R2/G functional-interface and policy first attempt.
- [ ] Handwrite the public behavior of `Money`, `Quote`, and `FeePolicy`.
- [ ] List invalid construction cases before writing implementation code.

Implementation:

- [ ] Implement `Money` with currency-safe arithmetic.
- [ ] Implement an explicitly directed exchange rate.
- [ ] Implement immutable quote creation and expiry using an injectable clock.
- [ ] Implement one fixed fee policy.
- [ ] Implement one percentage fee policy with explicit rounding.
- [ ] Implement a policy registry that rejects missing and duplicate keys.

Tests:

- [ ] Reject currency mismatch and non-positive amounts.
- [ ] Prove the chosen rounding boundary with awkward decimal inputs.
- [ ] Test the exact quote-expiry instant.
- [ ] Test missing and duplicate fee-policy keys.
- [ ] Add one changed requirement without editing existing policy consumers.

Exit evidence:

- [ ] `./mvnw test` passes.
- [ ] You can explain why `BigDecimal` alone does not make money safe.
- [ ] You can defend Strategy here without calling every function a pattern.

## C2 — Idempotent transfer and balanced journal

Target: produce the minimum useful database-backed project.

Design and schema:

- [ ] Draw the local transaction boundary.
- [ ] Design account, quote, transfer, idempotency, journal, and entry tables.
- [ ] Write Flyway migrations with primary, foreign, check, and unique
      constraints.
- [ ] Identify which invariant is enforced by Java, Oracle, or both.
- [ ] Choose and document the account-locking strategy.

Application behavior:

- [ ] Implement caller identity without trusting a customer ID from the body.
- [ ] Implement `POST /fx-transfers` request and response DTOs.
- [ ] Verify ownership of both customer accounts.
- [ ] Verify quote match and expiry inside the use case.
- [ ] Implement caller-scoped idempotency and request fingerprinting.
- [ ] Book account effects and immutable per-currency journal entries.
- [ ] Commit transfer, idempotency outcome, balance effects, and journal in one
      transaction.
- [ ] Implement `GET /fx-transfers/{id}` with ownership protection.
- [ ] Map expected failures to stable API error codes.

Oracle-backed tests:

- [ ] Same key and same request returns the original result.
- [ ] Same key and changed request returns a conflict.
- [ ] Insufficient funds leaves no partial booking.
- [ ] Ownership failure exposes no other customer's transfer.
- [ ] Concurrent debits cannot produce a negative balance.
- [ ] Concurrent duplicate keys produce one business effect.
- [ ] Every committed journal balances independently per currency.
- [ ] A forced failure rolls back every C2 artifact.

Exit evidence:

- [ ] `./mvnw verify` passes against Oracle Testcontainers.
- [ ] Record the test command, database image, observed result, and one failure
      you corrected.
- [ ] Explain six C2 questions listed in the R2 capstone note without reading.

Stop here if interview preparation time is limited.

## C3 — Transactional outbox and delivery failure

Target: separate database commitment from unreliable network delivery.

- [ ] Add the outbox table through Flyway.
- [ ] Insert the outbox row in the booking transaction.
- [ ] Implement a relay and fake gateway outside that transaction.
- [ ] Give every message a stable event and correlation ID.
- [ ] Simulate failure before commit, after commit/before publish, and after
      publish/before acknowledgement.
- [ ] Test duplicate relay attempts.
- [ ] Implement bounded retry state and an observable terminal failure.
- [ ] Explain why the outbox does not create end-to-end exactly-once delivery.

## C4 — Interfaces and measured diagnosis

Target: add only the role-specific depth that produces measured evidence.

- [ ] Implement bounded, stably sorted customer transfer listing.
- [ ] Measure query count and diagnose one deliberate N+1 case.
- [ ] Capture an Oracle execution plan before and after one justified index.
- [ ] Measure connection-pool hold and wait time under controlled load.
- [ ] Replace lab authentication with an OAuth2 resource-server boundary if it
      remains useful to the R2 security exercise.
- [ ] Optionally map one SOAP operation to the same application use case.
- [ ] Keep protocol-specific errors outside the domain model.

Record `not run` instead of inventing unavailable runtime evidence.

## C5 — Oral defense

- [ ] Give a ten-minute architecture walkthrough.
- [ ] Trace one successful request and one failed concurrent request.
- [ ] Explain the commit/publish crash window.
- [ ] Handle a changed fee rule without redesigning the entire application.
- [ ] Handle a changed requirement where the destination account belongs to an
      external beneficiary.
- [ ] Identify what would force a new service or consistency boundary.
- [ ] Present an evidence table separating design, code, unit tests, Oracle
      integration tests, runtime observation, and unimplemented ideas.

## Per-task evidence log

Copy this block below the task you complete or into a dated note:

```text
Attempt:
Expected result:
Command/test:
Observed result:
Failure or correction:
Changed-case retest:
Evidence level: Design | Unit | Integration | Runtime | Defense
```

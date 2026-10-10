# Architecture decision records

These numbered records explain the foundation choices in FX Transfer Lab.
They were recorded retrospectively on 2026-10-09 after reviewing the current
Java implementation, migration, tests, and product documentation. The rationale
describes why a choice fits this project; it does not claim that a benchmark or
production trial established it as the best choice for every financial system.

All records are accepted foundation decisions. **Accepted does not mean
implemented.** Each record states its implementation boundary and links to the
available evidence. The core-banking direction in ADR-0012 is a future boundary,
not an implemented distributed transaction design.

## Index

| Record | Decision | Current implementation |
| --- | --- | --- |
| [0001](0001-java-spring-modular-monolith.md) | Java 21, Spring Boot, and a modular monolith | Scaffold and independent monetary domain |
| [0002](0002-internal-book-transfer-foundation.md) | Begin with an internal book transfer | Product scope; booking pending |
| [0003](0003-three-account-types.md) | Three account types and balancing per currency | Account migration; posting engine pending |
| [0004](0004-immutable-expiring-quote.md) | Immutable quote and trusted rate snapshot | Domain and quote schema tested; provider and persistence adapter pending |
| [0005](0005-decimal-money-and-rounding.md) | Decimal money, directed rates, and explicit rounding | Implemented in domain values and fee calculation |
| [0006](0006-fee-strategies-and-policy-keys.md) | Fee strategies selected through normalized keys | Domain registry implemented; configuration wiring pending |
| [0007](0007-ledger-authority-and-balance-projection.md) | Immutable ledger with a synchronous balance projection | Balance columns and journal/entry schema tested; posting and reconciliation pending |
| [0008](0008-oracle-flyway-and-test-lanes.md) | Oracle, Flyway, and separate test lanes | Configuration, initial migration, and context integration test |
| [0009](0009-atomic-booking-and-lock-order.md) | Atomic booking and deterministic locking | Planned |
| [0010](0010-successful-command-idempotency.md) | Caller-scoped idempotency for committed successes | Schema tested; booking coordination and replay pending |
| [0011](0011-jwt-and-resource-ownership.md) | JWT access tokens with application ownership checks | Planned; Spring Security dependency exists |
| [0012](0012-outbox-and-external-system-boundary.md) | Transactional outbox and a separate external-system boundary | Planned; core banking deferred |

## Relationship to the other documents

[Product requirements](../PRD.md) define scope and acceptance criteria.
[Foundation design decisions](../DESIGN_DECISIONS.md) remain the consolidated
normative contract. Its numbered sections are not ADR IDs.
[Quote and ledger architecture](../QUOTE_AND_LEDGER_ARCHITECTURE.md) supplies
longer examples, diagrams, and comparisons. These ADRs record the reasons and
trade-offs behind those rules without changing them.

If a decision changes, add the next numbered ADR, name the record it supersedes,
and update the affected contracts, code, and tests together. Keep earlier records
so a reviewer can understand the previous design. New implementation evidence
may be added to an existing record without pretending the original decision
was made on a later date.

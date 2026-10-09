# ADR-0008: Version the Oracle schema with Flyway and test Oracle behavior on Oracle

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Configured; initial migration and context integration test exist

## Context

Booking depends on database constraints, locks, transaction isolation, and
migration behavior. A portable domain test can prove a fee calculation but
cannot establish how Oracle resolves a unique-index race or a locked debit.
Requiring Docker for every calculation test would slow ordinary feedback.

## Decision and rationale

Use Oracle as the foundation database and Flyway versioned SQL as the schema
history. Oracle is the selected target for this project; this decision does not
claim it is superior to PostgreSQL or another transactional database.

Use database keys and checks for invariants that must hold outside a Java call:
account identity, valid customer references, supported types and currencies,
customer/internal ownership shape, and nonnegative customer balances.
Applied versioned migrations become immutable. Subsequent changes require a
new migration rather than editing an already applied schema history.

Keep two explicit verification lanes. `./mvnw test` runs portable tests through
Surefire. `./mvnw verify -Poracle-it` also runs `*IT` tests through Failsafe against
a fresh Oracle Testcontainer. Plain `verify` does not select the Oracle lane.
Unavailable Docker fails that lane rather than making it appear verified.
The local Compose database is persistent and separate from those fresh containers.

## Alternatives and consequences

- H2 would start faster but would not establish Oracle SQL, locking, or index behavior.
- Manual schema changes would make test and development databases harder to reproduce.
- Database tests for every domain calculation would add infrastructure cost
  without improving evidence about the arithmetic.

Pinned container configuration makes the chosen target explicit, but images and
Docker add resource and startup costs. A successful context test proves startup
and migration application; it does not prove every rejection constraint,
overspending race, deadlock path, or rollback scenario.

## Evidence and revisit conditions

See [Maven test profile](../../pom.xml), [Compose configuration](../../compose.yaml),
[initial migration](../../src/main/resources/db/migration/V1__create_customers_and_accounts.sql),
[Oracle test configuration](../../src/test/java/dev/kavrin/fxtransfer/TestcontainersConfiguration.java),
and [context integration test](../../src/test/java/dev/kavrin/fxtransfer/FxTransferLabApplicationIT.java).
The [account schema tests](../../src/test/java/dev/kavrin/fxtransfer/AccountSchemaIT.java)
now cover customer/account constraints, accepted ownership shapes, and balance
storage. Coordinated concurrency tests and constraints for the remaining
financial tables are still pending. The
[quote schema tests](../../src/test/java/dev/kavrin/fxtransfer/QuoteSchemaIT.java)
also cover quote ownership, monetary and rate bounds, provenance, expiry, and
nanosecond timestamp storage after V2 migration.
The [transfer schema tests](../../src/test/java/dev/kavrin/fxtransfer/TransferSchemaIT.java)
cover V3 foreign keys, required values, synchronous status, snapshot storage,
unique quote use, and quote reuse after rollback of an insert.
The [idempotency schema tests](../../src/test/java/dev/kavrin/fxtransfer/IdempotencySchemaIT.java)
cover V4 scope uniqueness, fingerprint format, result states, strict JSON and
large response snapshots, timestamp precision, and reservation/transfer rollback.

Revisit the database target only with a migration and behavior-verification plan.
Update image versions through an explicit dependency review and rerun the Oracle lane.

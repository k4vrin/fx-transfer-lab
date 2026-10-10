# ADR-0009: Commit booking effects together and lock accounts in a stable order

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Planned; transaction service, lock queries, and race tests absent

## Context

A transfer that changes a balance but loses its journal or retry result cannot
be explained or safely retried. Two requests can both pass an unlocked funds
check and then overspend. Requests acquiring shared account locks in opposite
orders can also deadlock.

## Decision and rationale

Use one Oracle transaction at the application booking boundary. Within it:

1. Reserve or resolve the caller-scoped idempotency key.
2. Lock the quote and verify ownership, request match, unused state, and expiry.
3. Resolve every affected customer, clearing, and fee account.
4. Lock all affected account rows in ascending stable account-ID order.
5. Check funds again against the locked source balance.
6. Write the transfer, balanced journal, booked projections, successful retry
   response, and outbox row.
7. Commit together, or roll back every effect.

Quote consumption is part of the same outcome. A failure must not leave a quote
used while the transfer rolled back. A unique `transfer.quote_id` closes duplicate
use; any separate consumed flag must follow the same transaction. Unique
idempotency scope closes the other retry race.

Lock every affected row, including shared internal rows. Ordering only customer
accounts would leave another route to inconsistent lock acquisition.

## Alternatives and consequences

- Separate transactions permit partial financial effects.
- A read-before-write check or JVM lock cannot protect competing database writers
  across instances.
- Optimistic versioning or conditional balance updates can work, but would need
  a different coordinated retry and journal strategy.

The selected pessimistic approach makes the spending check explicit but creates
waits and possible clearing-account contention. Stable order reduces deadlock
risk; it does not remove every database deadlock or timeout. Network calls must
not extend this lock-holding transaction.

## Evidence and revisit conditions

See [transaction contract](../DESIGN_DECISIONS.md#8-transaction-and-lock-order),
[atomicity requirements](../PRD.md), and [outbox boundary](0012-outbox-and-external-system-boundary.md).
[V3](../../src/main/resources/db/migration/V3__create_transfers.sql) now includes
the transfer table and unique quote-use constraint.
[Transfer schema tests](../../src/test/java/dev/kavrin/fxtransfer/TransferSchemaIT.java)
verify uniqueness and reuse after a savepoint rollback. That is database-level
evidence, not an application booking or coordinated-race test.
[Idempotency schema tests](../../src/test/java/dev/kavrin/fxtransfer/IdempotencySchemaIT.java)
also roll back a reservation, transfer, and successful result together and then
reuse the same key. The [journal header tests](../../src/test/java/dev/kavrin/fxtransfer/JournalSchemaIT.java)
cover V5's unique transfer linkage, deletion protection, and rollback of a
transfer and its journal header together.
[Journal-entry tests](../../src/test/java/dev/kavrin/fxtransfer/JournalEntrySchemaIT.java)
cover V6's row constraints and rollback of a header and entries together.
Outbox and the booking service remain pending; these tests do not establish atomicity of all
financial writes or balanced accounting.

Verify with coordinated Oracle races and failure injection after each financial
write. Revisit locking after measuring contention, or when final balances move
to an external authority and a local transaction can no longer cover the effect.

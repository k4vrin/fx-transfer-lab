# ADR-0010: Persist caller-scoped idempotency with committed successful transfers

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Idempotency schema tested; booking coordination and retry handling pending

## Context

A client timeout does not reveal whether the database committed. Retrying the
same instruction must return the original transfer without repeating its debit.
Reusing a key for another instruction must not accidentally return an unrelated
success. Reserving a key in a separate committed transaction can strand it after
a booking failure.

## Decision and rationale

Scope uniqueness to authenticated customer ID, `CREATE_FX_TRANSFER`, and the
idempotency key. Hash a versioned canonical representation of quote ID, source
account ID, and destination account ID using SHA-256. Use canonical UUID text,
UTF-8, and unambiguous field names or length-prefixing. Arbitrary JSON formatting
must not affect identity. Persist the normalized fields for diagnosis too.

Reserve the key inside the booking transaction. Only a successful outcome is
visible after commit, with the original HTTP status and response snapshot.
Same key and same normalized request replay that snapshot; changed payload
produces a conflict. Replay returns the original `201` and
`Idempotency-Replayed: true` without recalculating pricing or rechecking quote
expiry as though it were a new booking.

Do not cache failed bookings in the foundation. Rollback removes the reservation
along with the financial effects. Insufficient funds may change before a retry.
There is no separately committed `IN_PROGRESS` record needing recovery.

Concurrent duplicates contend on database uniqueness. After a winning commit,
resolve the committed result for replay or conflict. After rollback, a waiting
request may proceed. Implement this using transaction handling that does not
assume a failed ORM flush leaves the same transaction usable.

## Alternatives and consequences

- An in-memory cache is lost on restart and cannot coordinate instances.
- Globally scoped keys cause unrelated customers to collide.
- Committed pending reservations require a recovery lifecycle.
- Caching failures is a valid different API contract, but prevents a same-key
  retry from succeeding when the underlying condition changes.

This provides retry safety for the local financial command, not exactly-once
network delivery. Retention and response compatibility need explicit rules before
records are pruned or the public API evolves.

## Evidence and revisit conditions

See [idempotency contract](../DESIGN_DECISIONS.md#6-idempotency-scope-and-fingerprint)
and [failure caching contract](../DESIGN_DECISIONS.md#7-failure-caching).
No current domain unit test establishes concurrent command deduplication.

[V4](../../src/main/resources/db/migration/V4__create_idempotency_records.sql)
and the [idempotency schema tests](../../src/test/java/dev/kavrin/fxtransfer/IdempotencySchemaIT.java)
establish scope uniqueness, lowercase 64-character hexadecimal fingerprint
format, reservation and successful-result shapes, strict JSON response storage,
and rollback of the reservation and transfer together, followed by same-key reuse.
The format check does not establish canonical request hashing. The schema permits
an `IN_PROGRESS` row; the booking service must complete it before commit.
Application replay and coordinated concurrent duplicates remain untested.

Revisit when adding request fields, changing retention, caching failures, or
introducing a long-running external workflow. Version the fingerprint contract
and test replay after restart and concurrent key reuse.

# ADR-0012: Commit an outbox intent and deliver external effects separately

- Status: Accepted for the foundation outbox; separate core banking remains deferred
- Recorded: 2026-10-09, retrospective
- Implementation: Planned; outbox package contains no delivery implementation

## Context

Publishing before database commit can announce a transfer that later rolls back.
Publishing only after commit without durable intent can lose the notification
if the process stops between commit and publication. Calling a remote system
inside the transaction adds lock wait time without making that system share the
Oracle commit.

## Decision and rationale

Insert the outbox row with the transfer, journal, balance projection, and
successful idempotency result in one transaction. A separate relay publishes
committed messages and maintains delivery state. Booking success and delivery
success are different outcomes.

Give messages stable IDs and business correlation. Delivery can be retried and
duplicated, including when publication succeeds but acknowledgement or the
relay's state update is lost. Consumers need deduplication or idempotent handling.
Do not claim exactly-once delivery.

## Alternatives and consequences

- Direct publication inside booking can survive a later rollback at the remote
  side while holding database locks during the call.
- A best-effort callback after commit can lose work on process failure.
- Distributed transactions would require participating systems and a different
  operational model; they are outside the foundation contract.

The outbox closes the local commit-to-intent gap. It adds relay retries, retention,
terminal failure handling, and monitoring. A failed notification does not undo a
booked transfer or justify silently repeating it.

## Future core-banking boundary

The roadmap later delegates authoritative customer balances, reservations, and
final postings to separate core banking. The FX platform keeps product history,
its subledger, posting instructions, and reconciliation evidence.

An outbox cannot make a remote posting atomic with local records. That phase
needs a new ADR defining idempotent reservation and posting commands, status
inquiry, release or compensation, and reconciliation. A timeout is an unknown
outcome until investigated, not evidence of rollback. No distributed workflow
or remote posting guarantee is accepted as implemented by this record.

## Evidence and revisit conditions

See [outbox invariants](../DOMAIN_MODEL.md#outbox-invariants),
[package ownership](../PACKAGE_GUIDE.md#outbox), and
[core-banking roadmap](../ROADMAP.md#phase-2--separate-core-banking-integration).

Before implementing delivery, test crashes around commit and acknowledgement,
duplicate messages, and restart recovery. Before external posting, record the
authority and recovery contract for each remote operation.

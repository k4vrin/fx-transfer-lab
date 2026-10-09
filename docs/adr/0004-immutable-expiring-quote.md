# ADR-0004: Freeze an expiring quote with a trusted rate snapshot

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Domain and quote schema tested; rate provider and persistence adapter pending

## Context

The customer needs to know the destination amount and fee before accepting a
transfer. If booking recalculates at a newer rate, the customer could accept
USD 110.00 and receive USD 108.00. Retaining only a rate value would also lose
which source and version supplied it.

## Decision and rationale

Create an immutable quote containing source principal, calculated destination
amount, calculated source-currency fee, `RateSnapshot`, creation instant, and
expiry instant. Calculate conversion and fee once at quote creation. Booking
uses that stored monetary snapshot rather than invoking today's pricing rules.

`RateSnapshot` holds a directed rate, provider, version, and `asOf`. Provider and
version are nonblank provenance values, preserved without policy-key normalization.
Application code will obtain rates through a trusted internal `RateProvider`
port, initially using controlled fixtures. Clients must not set the booking rate.
Provenance fields explain a rate; their existence alone does not authenticate it.

Use an injectable `Clock`. Validity ends at the exact expiry instant:
`now < expiresAt`. The planned booking flow captures its eligibility time after
locking the quote. A transaction that passed this check may commit later; the
contract is not a guarantee that commit finishes before the deadline.

A quote funds at most one successful transfer. Enforce that later through unique
`transfer.quote_id` and the booking transaction. Rollback leaves it unused.
Successful idempotent replay returns the original result, even after expiry.

## Alternatives and consequences

- An indicative quote could reprice at acceptance, but would need explicit
  customer consent to the changed amounts.
- Immediate conversion would avoid a separate quote lifecycle, but would not
  support the selected preview-and-accept contract.
- Keeping only a rate-table reference could change historical meaning if the
  referenced data or pricing logic changed.

This firm synthetic quote makes acceptance deterministic. It does not reserve
funds, hold a live market price, or hedge exposure. Real firm pricing would need
those policies as separate requirements. An expired offer requires a new quote.

## Evidence and revisit conditions

See [Quote](../../src/main/java/dev/kavrin/fxtransfer/quote/Quote.java),
[RateSnapshot](../../src/main/java/dev/kavrin/fxtransfer/quote/RateSnapshot.java),
[Quote tests](../../src/test/java/dev/kavrin/fxtransfer/quote/QuoteTest.java), and
[architecture explanation](../QUOTE_AND_LEDGER_ARCHITECTURE.md).
The current `Quote` has no persistent ID, owner, consumption state, or funds reservation.

The [V2 quote table](../../src/main/resources/db/migration/V2__create_quotes.sql)
adds quote identity, required customer ownership, frozen amounts and provenance,
and timestamp columns. [Quote schema tests](../../src/test/java/dev/kavrin/fxtransfer/QuoteSchemaIT.java)
verify its constraints, decimal storage, and nanosecond instant precision on
Oracle. [V3](../../src/main/resources/db/migration/V3__create_transfers.sql)
adds unique transfer quote use. [Transfer schema tests](../../src/test/java/dev/kavrin/fxtransfer/TransferSchemaIT.java)
verify rejection of a second transfer for the same quote and reuse after a
transfer insert is rolled back to a savepoint. Java persistence mapping and
coordinated concurrent-booking tests are still pending.
Storage limits for rate magnitude and provenance length must be
handled at the persistence boundary.

Revisit for indicative pricing, partial quote use, target-fixed amounts, remote
price holds, or a stricter completion-time expiry requirement.

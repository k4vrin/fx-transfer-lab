# ADR-0002: Begin with an internal book transfer

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Accepted product scope; booking not yet implemented

## Context

An FX transfer can involve another customer, beneficiary banks, market execution,
settlement dates, liquidity, and external posting systems. Each introduces its
own lifecycle and failure modes. The foundation first needs evidence that a
retry cannot debit twice, two concurrent requests cannot overspend, and a failed
booking cannot leave a partial journal.

## Decision and rationale

Start with one authenticated customer converting a fixed EUR principal into USD
between two accounts owned by that customer. Both customer accounts and the
synthetic internal accounts are maintained in the same Oracle database. Charge
the fee on top of the source principal, in the source currency.

This scope still requires pricing, ownership, expiry, idempotency, locking,
atomicity, and balanced accounting. It makes those behaviors testable within
one commit boundary before external settlement adds ambiguous outcomes.
The first release is useful only when the complete booking path satisfies those
invariants; a collection of domain classes is not that release.

## Alternatives and consequences

- Cross-bank remittance would require beneficiary and settlement models, external
  status inquiry, and reconciliation before its success state could be trusted.
- A transfer between different customers would add recipient authorization and
  customer-to-customer product rules to the initial scope.
- A rate calculator alone would be smaller, but would not establish booking,
  concurrency, or journal correctness.

Synthetic clearing inventory is sufficient for the chosen example. It provides
no evidence of real liquidity, hedging, market execution, or network settlement.
Available balance can initially equal booked balance because holds and pending
settlements are outside this scope.

## Evidence and revisit conditions

See [PRD scope and example](../PRD.md), [foundation roadmap](../ROADMAP.md), and
[quote and ledger architecture](../QUOTE_AND_LEDGER_ARCHITECTURE.md).
No transfer endpoint or booking service currently establishes these runtime guarantees.

Revisit when adding another recipient, a target-fixed amount, external settlement,
funds reservation, or separate authoritative core banking. Record those product
and consistency changes before extending the booking flow.

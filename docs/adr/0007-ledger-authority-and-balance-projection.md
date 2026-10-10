# ADR-0007: Keep immutable ledger truth and a synchronous booked balance

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Balance columns and journal/entry schema tested; posting, reconciliation, and reversal pending

## Context

A stored balance alone cannot explain how an account reached its current value.
Summing the entire journal for every debit would retain history but complicate
an efficient, concurrency-safe funds check. Separate asynchronous projections
would let the spending decision observe stale data.

## Decision and rationale

Treat immutable journal entries as authoritative financial movements. Keep
`booked_balance` as a synchronous projection for locking and funds checks.
Commit journal entries and all affected balance updates in the same transaction.

`opening_balance` is the synthetic starting baseline, not the current spendable
amount. Reconciliation uses that baseline and the account type's sign rules:

```text
credit-normal: expected = opening + credits - debits
debit-normal:  expected = opening + debits - credits
```

For a customer opening at EUR 100.00, a EUR 30.00 debit means booked balance must
be EUR 70.00. If the stored value is EUR 68.00, reconciliation has found a
EUR 2.00 discrepancy. Do not change the opening baseline to hide it.
Without holds, the booked projection is also the foundation spending balance.

Later corrections create a linked compensating operation using the original
amounts and opposite entries. The agreed later reversal is full and permitted
at most once per original transfer. Original financial history remains intact;
reversal is not part of the foundation release.

## Alternatives and consequences

- Balance-only storage loses the journal needed to explain movements.
- Deriving every balance on demand avoids a projection but increases the work
  required for each locked funds check.
- Asynchronous projection can lag and needs another authoritative reservation
  mechanism to prevent overspending.
- Editing old entries would erase evidence of the original operation.

The projection adds consistency obligations. Reconciliation must report drift
and support investigation; it must not silently rewrite financial evidence.
The opening baseline is part of the initial model and must remain accountable.

## Evidence and revisit conditions

See [balance columns](../../src/main/resources/db/migration/V1__create_customers_and_accounts.sql),
[ledger contract](../DESIGN_DECISIONS.md#2-ledger-truth-and-stored-balances), and
[reversal contract](../DESIGN_DECISIONS.md#10-reversal-contract).
Current columns alone do not prove journal or reconciliation correctness.
The [journal-entry migration](../../src/main/resources/db/migration/V6__create_journal_entries.sql)
enforces positive amounts, debit/credit direction, and account/currency matching.
[Oracle tests](../../src/test/java/dev/kavrin/fxtransfer/JournalEntrySchemaIT.java)
verify these constraints, deletion protection, decimal storage, and rollback.
They also demonstrate that the schema accepts a single entry or an unbalanced
pair. Minimum entry count and balance per currency require booking validation.
No current test establishes entry immutability or synchronized balance projection.

Revisit when introducing holds, pending settlement, historical balance queries,
or external ownership of customer balances. Preserve the FX subledger even when
core banking becomes authoritative for final postings.

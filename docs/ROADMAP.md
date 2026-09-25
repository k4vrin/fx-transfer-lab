# Enterprise FX and Trade-Finance Roadmap

This roadmap describes how FX Transfer Lab can evolve from one reliable internal
FX transfer into an enterprise-shaped foreign-exchange and trade-finance
platform. The direction is inspired by publicly described capabilities common
to products such as Exir, but this project is independent and is not affiliated
with, endorsed by, or a reproduction of any vendor product.

The roadmap has no delivery-date promise. A phase advances only when its
correctness, security, operational, and migration gates are satisfied.

## Architectural direction

The foundation starts as a modular monolith with one Oracle database because a
single local transaction is the clearest way to establish monetary, ledger,
idempotency, and concurrency correctness.

The intended long-term boundary is different:

```text
Channels and bank operators
            |
   FX and trade-finance platform
            |
   +--------+---------+----------------+
   |                  |                |
Rate and pricing   Workflow       Product modules
   |                  |                |
   +----------- FX subledger ----------+
                       |
              Posting instructions
                       |
              Core-banking adapter
                       |
              Separate core banking
```

In that target architecture, core banking owns authoritative customer accounts,
balances, reservations, and final postings. This platform owns FX products,
quotes, fees, workflow, immutable business history, its FX subledger, outbound
posting instructions, and reconciliation state.

Distribution is introduced only when a named ownership, deployment, security,
availability, or scaling requirement justifies its consistency cost.

## Phase 0 — Reliable internal FX foundation

Deliver one complete EUR-to-USD internal book transfer:

- immutable money, directed exchange rates, quotes, and fee policies;
- authenticated ownership checks;
- caller-scoped idempotency and single-use quotes;
- atomic account effects and balanced entries per currency;
- deterministic account locking and Oracle-backed concurrency tests;
- transfer retrieval, stable errors, reconciliation, and auditability; and
- transactional outbox preparation for external delivery.

**Exit gate:** repeated and concurrent requests cannot duplicate a financial
effect, overspend an account, or leave a partial journal. Stored balance and
ledger reconciliation agree.

## Phase 1 — Operationally complete FX transfer service

Harden the foundation before expanding product scope:

- full linked reversal with compensating entries;
- transfer search and stable pagination;
- rate limiting and abuse controls;
- structured audit events and security-safe logging;
- metrics for latency, errors, lock waits, pool saturation, outbox age, and
  reconciliation differences;
- bounded outbox retry, terminal failure handling, and operator recovery;
- backup, restore, migration, and disaster-recovery exercises; and
- measured Oracle indexes, execution plans, and connection-pool behavior.

**Exit gate:** the service has recovery procedures, observable failure states,
and evidence that schema migrations and rollback paths preserve financial data.

## Phase 2 — Separate core-banking integration

Move authoritative balances out of the FX platform without discarding account
or ledger concepts:

- define an anti-corruption layer for account lookup, funds reservation,
  posting, release, reversal, and status inquiry;
- add stable external customer, account, reservation, posting, and correlation
  references;
- replace local balance ownership with a simulated core-banking adapter;
- introduce `RESERVATION_PENDING`, `FUNDS_RESERVED`, `POSTING_PENDING`,
  `POSTED`, `REJECTED`, and `RECONCILIATION_REQUIRED` behavior where justified;
- treat timeout as an unknown outcome and inquire before retrying;
- make every external command idempotent; and
- reconcile FX instructions and subledger records with authoritative core
  postings.

Start with an in-process simulated adapter, then a separately deployed synthetic core
banking service. REST, SOAP, or messaging is an adapter decision; remote DTOs do
not enter the FX domain.

**Exit gate:** injected timeouts, duplicate replies, late replies, and service
restarts cannot create a duplicate posting or falsely mark an unknown posting as
successful.

## Phase 3 — Multi-currency rate, pricing, and accounting configuration

Generalize rules through controlled, effective-dated configuration:

- ISO currency catalog with currency-specific minor units;
- multiple rate books, markets, providers, and intraday versions;
- stale-rate policy, spread, preferential rates, and negotiated deals;
- configurable fixed, percentage, tiered, minimum, maximum, and waived fees;
- versioned accounting-event types and posting templates;
- typed debit/credit account-resolution and amount rules;
- simulation and validation before activation; and
- maker-checker approval, effective dates, immutable versions, and rollback.

Existing transfers retain the exact rate, fee, and accounting-template versions
that produced them. “Dynamic” configuration never means executing arbitrary,
unreviewed code in a financial path.

**Exit gate:** a configuration version can be simulated, approved, activated,
used deterministically, audited, and retired without changing historical
transactions.

## Phase 4 — Remittance and FX dealing

Add complete vertical products rather than empty feature packages:

- outgoing and incoming currency remittances;
- commercial remittance purpose and supporting-document workflow;
- currency purchase and sale deals;
- beneficiary, counterparty, correspondent, and nostro/vostro references;
- settlement instructions, value dates, cut-off times, and business calendars;
- amendments, cancellation, rejection, return, and investigation cases; and
- exposure, position, settlement, and reconciliation views.

Each product supplies its own state machine, permissions, fees, accounting
events, messages, documents, failure recovery, and audit history while reusing
shared monetary and workflow foundations.

**Exit gate:** at least one outgoing and one incoming flow survive duplicate,
out-of-order, timeout, rejection, and reconciliation scenarios end to end.

## Phase 5 — Trade-finance product modules

Introduce one independently useful product at a time:

1. foreign-currency guarantees;
2. documentary credits;
3. documentary collections and bills;
4. correspondent credit lines; and
5. amendments, document presentation, discrepancies, expiry, and closure.

The platform needs party roles, document storage and versioning, work queues,
maker-checker approvals, limits, collateral references, event-driven fees,
accounting events, and message generation. Product workflows must be versioned
so a running case does not silently change when a new process definition is
published.

**Exit gate:** each module has a reviewed domain glossary, allowed transitions,
authorization matrix, accounting map, message map, reconciliation path, and
complete audit history.

## Phase 6 — Financial messaging and institutional integration

Build protocol-neutral business messages before protocol adapters:

- canonical financial-message model with stable business correlation;
- schema validation, duplicate detection, acknowledgements, rejections, and
  repair queues;
- contract-first SOAP adapters where required;
- simulated inbound and outbound SWIFT and SEPAM adapters;
- core banking, identity, document, notification, and reporting adapters; and
- central-bank reporting exports with versioned schemas and submission status.

Real network compatibility requires licensed specifications, counterparties,
security infrastructure, certification, and end-to-end testing. A simulated
adapter must never be presented as proof of SWIFT, SEPAM, or regulatory
interoperability.

**Exit gate:** contract tests and failure injection prove validation,
correlation, retry, duplicate handling, operator repair, and reconciliation for
each simulated adapter.

## Phase 7 — Localization, governance, and production readiness

Complete the cross-cutting platform capabilities:

- multilingual labels, documents, messages, and operator interfaces;
- Gregorian, Solar Hijri, and business-calendar presentation without replacing
  authoritative instants;
- fine-grained roles, separation of duties, privileged-operation approval, and
  periodic access review;
- retention, privacy, key management, secrets, tamper-evident audit, and
  security operations;
- verification of stated contracts and validation by qualified banking, legal,
  accounting, security, and regulatory reviewers;
- capacity tests, back pressure, high availability, disaster recovery, and
  regional failure exercises; and
- release controls, migration rehearsals, runbooks, service-level objectives,
  and incident response.

**Exit gate:** production or compliance claims require evidence from the target
environment and the responsible qualified reviewers. Passing local tests is not
regulatory validation.

## Candidate bounded contexts

The roadmap may eventually produce these boundaries. They begin as modules and
become separately deployable only when justified:

| Context | Owns |
| --- | --- |
| Party and access | Parties, operator roles, customer references, entitlements |
| Reference data | Currencies, countries, branches, calendars, code lists |
| Rate book | Providers, markets, rate versions, freshness and spreads |
| Pricing | Fee definitions, waivers, tiers and calculated fee snapshots |
| Remittance | Incoming/outgoing instructions and beneficiary workflow |
| FX dealing | Quotes, negotiated deals, positions and value dates |
| Trade finance | Credits, guarantees, collections, presentations and amendments |
| Workflow | Cases, tasks, approvals, maker-checker and process versions |
| Accounting | FX subledger, posting templates, instructions and reconciliation |
| Messaging | Canonical messages, protocol adapters, acknowledgements and repair |
| Reporting | Operational, accounting and simulated regulatory reports |
| Audit | Immutable business and security audit history |

## Permanent scope and claim boundaries

- This repository begins as a reference implementation, not a bank product.
- The first release owns synthetic accounts; the target platform delegates
  authoritative balances to core banking.
- The FX subledger remains even after balance ownership moves.
- UCP support is a versioned product and legal-review concern, not a boolean
  validation flag.
- Verification proves conformance to a stated contract; it does not by itself
  validate suitability for regulated banking use.
- No roadmap item constitutes a claim of vendor equivalence, regulatory
  compliance, live market access, or certified network connectivity.

# Package Responsibilities

The root package is `dev.kavrin.fxtransfer`. Organize primarily by business
capability, then introduce `api`, `application`, `domain`, and `infrastructure`
inside a capability only when those distinctions are useful.

[ADR-0001](adr/0001-java-spring-modular-monolith.md) explains this module and
framework boundary. The [ADR index](adr/README.md) distinguishes accepted design
from implemented behavior; a package listed here may still be only a scaffold.

```text
dev.kavrin.fxtransfer
├── FxTransferLabApplication
├── fee
│   └── domain                 # policies, keys, registry
├── quote
│   ├── domain                 # Quote, RateSnapshot, DirectedExchangeRate
│   └── application
│       └── port               # RateProvider
├── shared
│   └── domain                 # Money
├── account
│   ├── domain                 # Account, AccountType
│   └── infrastructure
│       └── persistence        # AccountEntity, AccountEntityMapper
├── transfer                   # capability scaffold
├── ledger                     # capability scaffold
└── outbox                     # capability scaffold
```

This is the current production layout, not a claim that all capabilities are
implemented. Tests mirror the domain packages; schema tests live in each owning
capability's `infrastructure` package. Local seed integration tests live in
`integration.database`, and shared Oracle setup lives in test-only `support`.

Create `api`, `application`, and `infrastructure` packages as their code arrives.
The next configured-rate implementation belongs in `quote.infrastructure.rate`;
Spring wiring belongs in `quote.infrastructure.configuration`. JPA mappings
will belong in each capability's `infrastructure.persistence` package.

## Dependency direction

```text
API adapter -> application orchestration -> domain model
                         |
                         v
               repository/gateway ports
                         ^
                         |
             infrastructure adapters
```

Domain code should not depend on controllers, JSON, JPA repositories, or a
message client. Application code coordinates a use case and transaction; it
should not absorb all domain rules.

## `account`

Owns customer and internal ledger-account identity, currency, ownership, and
spendable-balance rules. Persistence locking needed for a debit also belongs to
this capability's infrastructure.

`Account` is immutable and owns balance currency, ownership, and customer
nonnegative-balance validation. Its manual builder delegates to the same validated
constructor. `AccountEntity` maps Oracle rows; `AccountEntityMapper` converts row
values to domain values through `Money.of`. Portable mapper tests cover field
preservation, scale normalization, and rejection of invalid state. Oracle account
tests also exercise SQL-to-JPA-to-domain mapping for customer and internal rows.
Repository adapters, booking locks, and reconciliation remain pending.

It must not decide exchange rates, fees, or HTTP responses.

## `fee`

`fee.domain` owns the fee-policy contract, concrete fee rules, and policy selection. A
registry may select a policy by a stable key and must fail fast when keys are
missing or duplicated.

It must return a source-currency `Money`; it does not debit an account.

## `quote`

Owns the exchange-rate direction, quote creation, expiry, source/destination
amount relationship, and immutable quote snapshot.

`quote.domain` owns the pricing values and invariants. `quote.application.port`
contains the `RateProvider` contract used by quote orchestration. An
infrastructure adapter implements that contract without making the domain
depend on configuration files, Spring, or a remote client.

It must not book balances or publish messages.

## `transfer`

Owns the transfer use case, lifecycle, REST contract, idempotency coordination,
authorization decision, and transaction boundary. This is the orchestration
center, not a dumping ground for every rule.

Likely internal packages later:

- `transfer.api`: request/response DTOs, controller, and HTTP error mapping;
- `transfer.application`: command, result, use-case service, and ports;
- `transfer.domain`: transfer state and invariant-bearing behavior; and
- `transfer.infrastructure`: JPA mappings and repository adapters.

## `ledger`

Owns journals, immutable debit/credit entries, per-currency balancing, and the
mapping between a business operation and its accounting evidence.

It does not authenticate callers or choose a quote.

## `outbox`

Introduced after foundation booking. Owns durable outbound messages, relay
selection, retry state, and simulated gateway adapters. It must preserve the
distinction between a booked transfer and a delivered notification.

## `shared`

Contains only concepts genuinely shared by multiple capabilities, such as a
carefully designed `Money` value, an injectable clock abstraction if needed, or
strongly typed identifiers.

Shared business values belong in `shared.domain`. Technical helpers do not
belong there merely because several adapters use them.

Do not create a generic `util` package. If a class has a clear business owner,
keep it with that capability.

## Where Spring and JPA belong

- Controllers and JSON DTOs are API adapters.
- `@Transactional` belongs on an application use-case boundary.
- Spring Data repository interfaces or adapters belong in infrastructure.
- JPA entities must not be returned as API responses.
- Domain objects should remain testable without starting Spring.
- Constructor injection is required for mandatory collaborators.

Keep capabilities first, with layers inside each capability. Do not introduce
global `controller`, `service`, or `repository` folders that scatter one feature
across the project. Package placement expresses intended dependency direction;
Java package names alone do not enforce all architectural boundaries.

Use [ADR-0006](adr/0006-fee-strategies-and-policy-keys.md) for fee selection,
[ADR-0009](adr/0009-atomic-booking-and-lock-order.md) for the application booking
boundary, and [ADR-0012](adr/0012-outbox-and-external-system-boundary.md) for
delivery and later external-system ownership. Vendor API models belong behind
adapters; comparison references do not change these dependency rules.

# Package Responsibilities

The root package is `dev.kavrin.fxtransfer`. Organize primarily by business
capability, then introduce `api`, `application`, `domain`, and `infrastructure`
inside a capability only when those distinctions are useful.

```text
dev.kavrin.fxtransfer
├── account
├── fee
├── quote
├── transfer
├── ledger
├── outbox
└── shared
```

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

It must not decide exchange rates, fees, or HTTP responses.

## `fee`

Owns the fee-policy contract, concrete fee rules, and policy selection. A
registry may select a policy by a stable key and must fail fast when keys are
missing or duplicated.

It must return a source-currency `Money`; it does not debit an account.

## `quote`

Owns the exchange-rate direction, quote creation, expiry, source/destination
amount relationship, and immutable quote snapshot.

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

Introduced in C3. Owns durable outbound messages, relay selection, retry state,
and the fake gateway adapter. It must preserve the distinction between a booked
transfer and a delivered notification.

## `shared`

Contains only concepts genuinely shared by multiple capabilities, such as a
carefully designed `Money` value, an injectable clock abstraction if needed, or
strongly typed identifiers.

Do not create a generic `util` package. If a class has a clear business owner,
keep it with that capability.

## Where Spring and JPA belong

- Controllers and JSON DTOs are API adapters.
- `@Transactional` belongs on an application use-case boundary.
- Spring Data repository interfaces or adapters belong in infrastructure.
- JPA entities must not be returned as API responses.
- Domain objects should remain testable without starting Spring.
- Constructor injection is required for mandatory collaborators.

The first implementation may keep a feature flat. Add subpackages when there
are enough classes to clarify rather than obscure responsibility.

# FX Transfer Lab

FX Transfer Lab is a reference backend for an internal foreign-exchange transfer
and its accounting evidence. It focuses on financial correctness: immutable
monetary snapshots, idempotent commands, concurrency-safe booking, balanced
per-currency journals, authorization, reconciliation, and reliable integration.

The foundation is intentionally small. One authenticated customer converts
value between two internally maintained accounts using an expiring quote. The
long-term roadmap evolves that foundation toward an enterprise-shaped FX and
trade-finance platform connected to a separate synthetic core-banking system.

Public references include [Wise's quote-to-transfer contract](https://docs.wise.com/guides/product/send-money/quotes/authenticated-quote)
and [TOSAN Exir's FX and trade-finance product scope](https://tosan.com/products/index/67?title=%D8%B3%D8%A7%D9%85%D8%A7%D9%86%D9%87+%D8%A7%D8%B1%D8%B2%DB%8C+%D8%A7%DA%A9%D8%B3%DB%8C%D8%B1).
See the [architecture comparison](docs/QUOTE_AND_LEDGER_ARCHITECTURE.md) for how
each reference relates to this project's scope and decisions.

> [!IMPORTANT]
> This project is independent and is not affiliated with or endorsed by any bank,
> network, regulator, or software vendor. It does not claim SWIFT or SEPAM
> interoperability, UCP or regulatory compliance, live market access, or
> production readiness.

## Planned foundation flow

1. Authenticate the caller using an OAuth2 JWT access token.
2. Issue an immutable quote containing the directed rate, calculated amounts,
   fee, source metadata, and expiry.
3. Verify account ownership, currencies, quote validity, and available funds.
4. Reserve a caller-scoped idempotency key and lock affected rows in a stable
   order.
5. Commit the transfer, account projection, and balanced journal atomically.
6. Return the original result for a safe retry.
7. Relay a committed outbox message separately from the booking transaction.

## Architecture

The first release is a modular monolith with one Oracle database:

```text
dev.kavrin.fxtransfer
├── account     # account identity, ownership and initial balance projection
├── fee         # fee policies and selection
├── quote       # rates, quote creation, snapshots and expiry
├── transfer    # use cases, lifecycle, authorization and idempotency
├── ledger      # FX subledger, journals and reconciliation
├── outbox      # durable outbound messages and retry state
└── shared      # carefully selected shared value types
```

The domain remains independent from HTTP, JSON, JPA, and remote client models.
Application services coordinate transactions and ports; infrastructure adapters
own persistence and external protocols.

The target architecture keeps the FX subledger but moves authoritative customer
accounts, balances, reservations, and final postings behind a core-banking
anti-corruption layer. See the [product roadmap](docs/ROADMAP.md).

## Current status

- [x] Spring Boot and Java 21 project scaffold
- [x] Oracle local and Testcontainers configuration
- [x] English and Persian product and domain documentation
- [x] Foundation design decisions and package boundaries
- [x] Enterprise FX and trade-finance roadmap
- [x] Immutable money, directed rates, fee policies, and quote snapshots
- [ ] Oracle schema and persistence adapters
- [ ] JWT resource-server security
- [ ] Idempotent transfer booking and FX subledger
- [ ] Transactional outbox and operational diagnostics

The monetary domain has portable unit-test coverage. The first customer/account
migration includes schema constraints and has been applied in the Oracle
integration lane. Oracle tests cover customer/account, quote, transfer, and idempotency
constraints, valid rows, decimal storage, timestamp precision, and quote-use
uniqueness including reuse after rolling back a transfer insert. Idempotency tests
cover caller-scoped uniqueness, fingerprint format, reservation/result shapes,
JSON response storage, and rollback of a reservation and transfer together.
Transfer endpoints,
the remaining schema, database booking, and coordinated
concurrency tests remain pending.

## Technology

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC, Validation, Security, Data JPA, Flyway, and Actuator
- Oracle Database Free 23.26.2 for local and integration testing
- JUnit 5, Spring Boot Test, AssertJ, Mockito, and Testcontainers
- Maven Wrapper

## Documentation

- [Numbered architecture decision records](docs/adr/README.md): rationale, alternatives, consequences, and implementation evidence
- Product requirements: [English](docs/PRD.md) · [فارسی](docs/PRD.fa.md)
- Domain model and invariants: [English](docs/DOMAIN_MODEL.md) · [فارسی](docs/DOMAIN_MODEL.fa.md)
- Foundation design decisions: [English](docs/DESIGN_DECISIONS.md) · [فارسی](docs/DESIGN_DECISIONS.fa.md)
- Quote and ledger architecture, alternatives, and company comparisons: [English](docs/QUOTE_AND_LEDGER_ARCHITECTURE.md) · [فارسی](docs/QUOTE_AND_LEDGER_ARCHITECTURE.fa.md)
- [Package responsibilities](docs/PACKAGE_GUIDE.md)
- Enterprise FX and trade-finance roadmap: [English](docs/ROADMAP.md) · [فارسی](docs/ROADMAP.fa.md)

## Run the checks

Activate the repository's Java version:

```shell
sdk env
```

Run the portable unit tests without Docker:

```shell
./mvnw test
```

Run the portable tests and Oracle integration tests with Docker running:

```shell
./mvnw verify -Poracle-it
```

Surefire runs the portable tests; the `oracle-it` profile binds Failsafe's
`integration-test` and `verify` goals for classes named `*IT`. Without that
profile, `./mvnw verify` does not run the Oracle tests. An unavailable Docker
engine or Oracle container fails the integration lane rather than skipping it.
The first Oracle run downloads the pinned image and can take several minutes.

## Run locally

Set development-only database credentials without committing them:

```shell
export ORACLE_SYS_PASSWORD='choose-a-local-password'
export FXTRANSFER_DB_PASSWORD='choose-another-local-password'
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Stop the database with:

```shell
docker compose down
```

Add `--volumes` only when intentionally discarding the local database.

## Scope boundaries

The foundation does not implement:

- real market-rate execution or liquidity management;
- external beneficiary settlement;
- AML, KYC, sanctions, fraud, tax, or regulatory reporting;
- documentary credits, guarantees, collections, or credit lines;
- real SWIFT, SEPAM, central-bank, or core-banking connectivity; or
- multi-region availability or a production deployment.

These are explicit roadmap domains, not implied capabilities of the current
codebase.

## License

This repository does not currently include an open-source license. Until one is
added, all rights are reserved.

# FX Transfer Lab

A small, synthetic foreign-exchange transfer service for practicing Java, Spring,
database correctness, concurrency, and failure reasoning.

> [!IMPORTANT]
> This is an educational simulation. It is not connected to a bank, payment
> network, SWIFT, or a real foreign-exchange market. It does not claim regulatory,
> accounting, settlement, or production readiness.

## What the system will do

A customer asks to move value from one internal account to another account in a
different currency. The application will:

1. use an unexpired quote that fixes the exchange rate;
2. calculate a fee in the source currency;
3. verify ownership and sufficient available balance;
4. make retries safe with an idempotency key;
5. book balanced journal entries in each currency; and
6. later record an outbox message for simulated downstream delivery.

The project starts as one Spring Boot application and one Oracle database. A
microservice split would add failure modes without helping the first learning
milestones.

## Current status

- [x] Spring Boot project scaffolded.
- [x] Java 21 toolchain recorded.
- [x] Oracle development and test environments described.
- [x] Product, domain, package, and learning documents created.
- [x] C0 assumptions and design decisions recorded.
- [ ] Learner C0 restatement, diagram, and ADR completed.
- [ ] Business behavior implemented.

No transfer endpoint or domain solution has been implemented. Follow
[the roadmap](docs/ROADMAP.md) and preserve your first independent attempt.

## Technology

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC, Validation, Security, Data JPA, Flyway, and Actuator
- Oracle Database Free 23.26.2 for local and integration testing
- JUnit 5, Spring Boot Test, AssertJ, Mockito, and Testcontainers
- Maven Wrapper

## Documentation

- Product requirements: [English](docs/PRD.md) · [فارسی](docs/PRD.fa.md)
- Business model and invariants: [English](docs/DOMAIN_MODEL.md) · [فارسی](docs/DOMAIN_MODEL.fa.md)
- C0 design decisions: [English](docs/DESIGN_DECISIONS.md) · [فارسی](docs/DESIGN_DECISIONS.fa.md)
- [Package responsibilities](docs/PACKAGE_GUIDE.md)
- [Implementation roadmap](docs/ROADMAP.md)
- [Detailed implementation tasks](docs/IMPLEMENTATION_TASKS.md)

Read the PRD and domain model before creating domain classes. The documentation
defines a deliberately small fictional model so that no banking background is
required.

## Run the checks

Activate the repository's Java version:

```shell
sdk env
./mvnw verify
```

The integration test starts Oracle through Testcontainers, so Docker must be
running. The first run downloads a large database image and can take several
minutes.

## Run the application locally

Set database passwords without committing them:

```shell
export ORACLE_SYS_PASSWORD='choose-a-local-password'
export FXTRANSFER_DB_PASSWORD='choose-another-local-password'
docker compose up -d
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Stop the database with `docker compose down`. Add `--volumes` only when you
intentionally want to discard the local database.

Spring Security is present because caller identity and account ownership are
part of the model. Choosing the development authentication mechanism is a
roadmap task; no default password is documented or committed here.

## Learning contract

- Write the first business implementation yourself.
- Begin a milestone with a handwritten or unaided design where requested.
- Test observable behavior, including failure and concurrency cases.
- Record the exact command and database used as evidence.
- Treat review, compilation, and passing unit tests as different evidence.
- Do not claim real banking, SWIFT, regulatory, or production behavior.

## License

No license is granted. This private learning repository is not prepared for
public distribution.

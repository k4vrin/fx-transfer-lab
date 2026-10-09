# ADR-0001: Use Java 21 and Spring Boot in a modular monolith

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Scaffold and monetary domain implemented; application booking pending

## Context

The foundation needs one transaction around transfer state, account effects,
journal entries, and retry results. It also needs HTTP, validation, security,
persistence, migrations, and operational endpoints. Distributing these concerns
before a working booking flow would introduce network failure and separate
commit boundaries into the first correctness problem.

## Decision and rationale

Use Java 21, Maven Wrapper, and one Spring Boot process with one Oracle database.
Organize code by business capability: `account`, `fee`, `quote`, `transfer`,
`ledger`, and `outbox`, with narrowly shared value types in `shared`.

Java provides explicit value types and decimal arithmetic. Spring supplies the
application and adapter infrastructure already selected in the project. Keep
money, pricing, and quote rules independent of Spring, HTTP, JSON, and JPA so
their behavior can be checked without starting a server or database.

Place transaction orchestration at the application use-case boundary. Add
internal layers when they clarify a capability rather than creating empty
layers for every class. Framework and dependency versions may change without
changing this architectural boundary.

## Alternatives and consequences

- Separate services would allow independent deployment, but financial effects
  would need a distributed workflow and recovery rules. There is no current
  requirement that pays for that cost.
- A framework-free server could implement the same domain contract. It would
  require more infrastructure assembly for the selected HTTP and persistence scope.
- Kotlin could express this model too. This decision establishes one Java
  implementation; it makes no claim that Java is universally better.

One process is simpler to operate, but deployments and database capacity remain
shared. Package names alone do not enforce module boundaries; reviews and tests
must keep domain dependencies separate from adapters.

## Evidence and revisit conditions

See [Maven configuration](../../pom.xml), [package responsibilities](../PACKAGE_GUIDE.md),
and [Money](../../src/main/java/dev/kavrin/fxtransfer/shared/Money.java).
The current domain classes have no Spring or persistence annotations.

Revisit service boundaries when a specific ownership, deployment, security,
availability, or measured scaling requirement justifies a separate commit boundary.

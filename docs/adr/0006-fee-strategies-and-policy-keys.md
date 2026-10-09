# ADR-0006: Select fee strategies through normalized policy keys

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Domain strategies and registry implemented; configuration wiring pending

## Context

Putting fee rules in a consumer's switch would require changing that consumer
whenever a new policy is added. Selection also needs consistent handling of
keys such as `VIP`, `vip`, and ` vip `, without silently replacing a policy.

## Decision and rationale

Use `FeePolicy.calculate(Money)` as the shared strategy contract. The caller
selects a registered policy and invokes it without knowing its formula.
Represent its selection key with `FeePolicyKey`, a value object around a string.
Both registration and lookup therefore use `trim()` and
`toLowerCase(Locale.ROOT)` through the same constructor.

Reject null or blank keys, null policies, duplicate normalized registrations,
and missing policies. Invalid key format and a valid but unregistered key remain
different failures. The key space is extensible; it is not an enum of known
implementations. An enum would be suitable if the product intentionally chose
a closed set that changes only with code releases.

Both built-in policies require a nonnull, strictly positive source amount.
Fixed fees must be nonnegative and match the source currency; zero is allowed.
Percentage rates must satisfy `0 < rate < 1`. Store 2% as `0.02`, not `2`.
Rejecting 100% and higher is this project's business choice, not a mathematical
limitation. A positive percentage can still produce a rounded zero fee.

## Alternatives and consequences

- A switch couples the consumer to each strategy's implementation.
- An enum constrains the catalog to compile-time names.
- Raw strings in the registry would duplicate normalization and permit
  registration and lookup to disagree.

New registered implementations can reuse the same consumer. A string key alone
does not provide dynamic class loading, database configuration, or hot reload.
The current registry uses a mutable `HashMap` and does not guarantee concurrent
registration safety. Runtime changes would require an explicit publication and
versioning design. Quotes retain the calculated fee, so later policy changes do
not change an issued offer.

## Evidence and revisit conditions

See [FeePolicy](../../src/main/java/dev/kavrin/fxtransfer/fee/FeePolicy.java),
[FeePolicyKey](../../src/main/java/dev/kavrin/fxtransfer/fee/FeePolicyKey.java),
[registry](../../src/main/java/dev/kavrin/fxtransfer/fee/FeePolicyRegistry.java),
[fixed fee](../../src/main/java/dev/kavrin/fxtransfer/fee/FixedFeePolicy.java),
[percentage fee](../../src/main/java/dev/kavrin/fxtransfer/fee/PercentageFeePolicy.java),
and [registry tests](../../src/test/java/dev/kavrin/fxtransfer/fee/FeePolicyRegistryTest.java).

Revisit for effective-dated pricing, activation approval, concurrent reload,
policy provenance, or a product requirement that changes the percentage bounds.

# ADR-0005: Use decimal money, directed rates, and explicit rounding boundaries

- Status: Accepted
- Recorded: 2026-10-09, retrospective
- Implementation: Implemented in money, rates, and percentage fees

## Context

An amount without a currency can be combined with the wrong unit. A bare rate
can be applied backward. Implicit rounding can alter customer amounts before
the calculation reaches its intended monetary boundary.

## Decision and rationale

Use `BigDecimal` paired with a supported `Currency`. The foundation supports
EUR and USD at scale 2. `Money.of` uses `UNNECESSARY` to reject meaningful excess
precision rather than silently round an input. Arithmetic and comparison require
matching currencies. Money itself can represent zero or a negative value;
the operation requiring a positive principal enforces that rule.

Represent rates as positive destination-currency units per one source-currency
unit. Source and destination currencies must differ. Normalize rates to scale 8
with `UNNECESSARY`, so accepting a rate does not silently change it.

Multiply exactly, then round the final destination amount or percentage fee
once to scale 2 with `HALF_EVEN`. At a tie, this rule selects the even last digit
instead of always rounding upward. Do not round the percentage rate or an
intermediate multiplication result first.

## Alternatives and consequences

- Binary floating-point can introduce representation error for decimal amounts.
- Integer minor units work for balances but still need explicit precision rules
  for rates and percentage multiplication.
- Rounding every input would accept values by changing them, hiding a contract violation.
- Currency-specific scale is appropriate for a larger catalog but is outside
  the initial EUR/USD scope.

The rules make calculation and equality reproducible. They do not establish an
appropriate rounding policy for every product. A small conversion can round to
zero; any minimum payable destination amount needs a separate business rule.
`NUMBER(19,2)` limits stored precision and range; it is not a substitute for the
domain's rejection of excess fractional precision. SQL insertion can round.

## Evidence and revisit conditions

See [Money](../../src/main/java/dev/kavrin/fxtransfer/shared/Money.java),
[DirectedExchangeRate](../../src/main/java/dev/kavrin/fxtransfer/quote/DirectedExchangeRate.java),
[percentage calculation](../../src/main/java/dev/kavrin/fxtransfer/fee/PercentageFeePolicy.java),
and their [money tests](../../src/test/java/dev/kavrin/fxtransfer/shared/MoneyTest.java)
and [rate tests](../../src/test/java/dev/kavrin/fxtransfer/quote/DirectedExchangeRateTest.java).

Revisit scale, maximum representable amount, rounding, and equality together when
adding currencies, different settlement units, or product-specific rounding.

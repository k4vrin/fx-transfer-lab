package dev.kavrin.fxtransfer.quote;

import dev.kavrin.fxtransfer.fee.FeePolicy;
import dev.kavrin.fxtransfer.fee.FixedFeePolicy;
import dev.kavrin.fxtransfer.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class QuoteTest {

    @Test
    void createQuoteWithCalculatedAmountsAndExpiry() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");

        Money sourceAmount = Money.of(new BigDecimal("100.00"), eur);
        DirectedExchangeRate rate = DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd);
        FeePolicy feePolicy = new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur));

        Instant now = Instant.parse("2026-10-08T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        RateSnapshot snapshot = new RateSnapshot(
                rate,
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        Quote quote = Quote.create(
                sourceAmount, snapshot, feePolicy, clock, Duration.ofMinutes(5));
        assertThat(quote.rateSnapshot()).isEqualTo(snapshot);
        assertThat(quote.sourceAmount()).isEqualTo(sourceAmount);
        assertThat(quote.destinationAmount()).isEqualTo(
                Money.of(new BigDecimal("110.00"), usd));
        assertThat(quote.fee()).isEqualTo(
                Money.of(new BigDecimal("2.00"), eur));
        assertThat(quote.createdAt()).isEqualTo(now);
        assertThat(quote.expiresAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void isExpiredAtExactExpiryInstant() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        Quote quote = Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );

        assertThat(quote.isExpired(now.plusSeconds(300))).isTrue();
    }

    @Test
    void isNotExpiredOneNanosecondBeforeExpiry() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));


        Quote quote = Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(5));

        assertThat(quote.isExpired(quote.expiresAt().minusNanos(1)))
                .isFalse();
    }

    @Test
    void rejectsZeroValidityDuration() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));


        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeValidityDuration() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");

        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroSourceAmountWithCustomPolicy() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        FeePolicy zeroFeePolicy = source ->
                Money.of(BigDecimal.ZERO, source.currency());

        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(BigDecimal.ZERO, eur),
                rateSnapshot,
                zeroFeePolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSourceAmountWithCustomPolicy() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        FeePolicy zeroFeePolicy = source ->
                Money.of(BigDecimal.ZERO, source.currency());
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("-100.00"), eur),
                rateSnapshot,
                zeroFeePolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRateWhoseSourceCurrencyDoesNotMatchAmount() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), usd, eur),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsFeeInDifferentCurrency() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        FeePolicy invalidPolicy = source ->
                Money.of(new BigDecimal("2.00"), usd);
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                invalidPolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeFeeFromPolicy() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        FeePolicy invalidPolicy = source ->
                Money.of(new BigDecimal("-2.00"), source.currency());
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                invalidPolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesFeeWhenPolicyChangesAfterCreation() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        AtomicReference<Money> currentFee = new AtomicReference<>(
                Money.of(new BigDecimal("2.00"), eur));
        FeePolicy changingPolicy = source -> currentFee.get();

        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        Quote quote = Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                changingPolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5));

        currentFee.set(Money.of(new BigDecimal("5.00"), eur));

        assertThat(quote.fee()).isEqualTo(
                Money.of(new BigDecimal("2.00"), eur));
    }

    @Test
    void rejectsNullFeeFromPolicy() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        FeePolicy invalidPolicy = source -> null;
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                invalidPolicy,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("feePolicy returned null");
    }

    @Test
    void rejectsNullSourceAmount() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                null,
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceAmount");
    }

    @Test
    void rejectsNullRateSnapshot() {
        Currency eur = Currency.getInstance("EUR");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                null,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rateSnapshot");
    }

    @Test
    void rejectsNullFeePolicy() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                null,
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("feePolicy");
    }

    @Test
    void rejectsNullClock() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                null,
                Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clock");
    }

    @Test
    void rejectsNullValidity() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        RateSnapshot rateSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                "fixture-provider",
                "rate-v1",
                now.minusSeconds(60));

        assertThatThrownBy(() -> Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                rateSnapshot,
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(
                        now,
                        ZoneOffset.UTC),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("validity");
    }

    @Test
    void preservesRateAndProvenance() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));
        Instant asOf = Instant.parse("2026-10-08T11:59:00Z");

        RateSnapshot snapshot = new RateSnapshot(
                rate, "fixture-provider", "rate-v1", asOf);

        assertThat(snapshot.rate()).isEqualTo(rate);
        assertThat(snapshot.provider()).isEqualTo("fixture-provider");
        assertThat(snapshot.version()).isEqualTo("rate-v1");
        assertThat(snapshot.asOf()).isEqualTo(asOf);
    }

    @Test
    void preservesRateSnapshotWhenProviderRateChanges() {
        Currency eur = Currency.getInstance("EUR");
        Currency usd = Currency.getInstance("USD");
        Instant now = Instant.parse("2026-10-08T12:00:00Z");

        AtomicReference<RateSnapshot> providerRate = new AtomicReference<>(
                new RateSnapshot(
                        DirectedExchangeRate.of(new BigDecimal("1.10"), eur, usd),
                        "fixture-provider", "rate-v1", now.minusSeconds(60)));

        Quote quote = Quote.create(
                Money.of(new BigDecimal("100.00"), eur),
                providerRate.get(),
                new FixedFeePolicy(Money.of(new BigDecimal("2.00"), eur)),
                Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(5));

        providerRate.set(new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.20"), eur, usd),
                "fixture-provider", "rate-v2", now));

        assertThat(quote.rateSnapshot().version()).isEqualTo("rate-v1");
        assertThat(quote.rateSnapshot().rate().rate())
                .isEqualTo(new BigDecimal("1.10000000"));
        assertThat(quote.destinationAmount()).isEqualTo(
                Money.of(new BigDecimal("110.00"), usd));
    }
}

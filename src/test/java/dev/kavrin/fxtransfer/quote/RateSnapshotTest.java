package dev.kavrin.fxtransfer.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RateSnapshotTest {

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
    void rejectsNullRate() {
        assertThatThrownBy(() -> new RateSnapshot(
                null,
                "fixture-provider",
                "rate-v1",
                Instant.parse("2026-10-08T11:59:00Z")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rate");
    }

    @Test
    void rejectsNullAsOf() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> new RateSnapshot(
                rate, "fixture-provider", "rate-v1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("asOf");
    }

    @Test
    void rejectsBlankProvider() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> new RateSnapshot(
                rate, "   ", "rate-v1",
                Instant.parse("2026-10-08T11:59:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankVersion() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> new RateSnapshot(
                rate, "fixture-provider", "   ",
                Instant.parse("2026-10-08T11:59:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullProvider() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> new RateSnapshot(
                rate, null, "rate-v1",
                Instant.parse("2026-10-08T11:59:00Z")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("provider");
    }

    @Test
    void rejectsNullVersion() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD"));

        assertThatThrownBy(() -> new RateSnapshot(
                rate, "fixture-provider", null,
                Instant.parse("2026-10-08T11:59:00Z")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("version");
    }
}

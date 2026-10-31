package dev.kavrin.fxtransfer.quote.domain;

import dev.kavrin.fxtransfer.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteRestoreTest {

    private final Currency eur = Currency.getInstance("EUR");
    private final Currency usd = Currency.getInstance("USD");
    private final Instant created = Instant.parse("2020-01-01T00:00:00.123456789Z");
    private final RateSnapshot snapshot = new RateSnapshot(
            DirectedExchangeRate.of(new BigDecimal("1.10000000"), eur, usd),
            "historical-provider", "historical-v1", created.minusSeconds(60));

    @Test
    void restoresExpiredSnapshotWithoutReplacingHistoricalValues() {
        var restored = Quote.restore(money("100.00", eur), money("110.00", usd),
                money("7.25", eur), snapshot, created, created.plusSeconds(60));

        assertThat(restored.fee()).isEqualTo(money("7.25", eur));
        assertThat(restored.rateSnapshot()).isEqualTo(snapshot);
        assertThat(restored.createdAt()).isEqualTo(created);
        assertThat(restored.expiresAt()).isEqualTo(created.plusSeconds(60));
        assertThat(restored.isExpired(created.plusSeconds(60))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    void rejectsNonPositiveSource(String source) {
        assertThatThrownBy(() -> Quote.restore(money(source, eur), money("110.00", usd),
                money("0.00", eur), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("source amount must be positive");
    }

    @ParameterizedTest
    @ValueSource(strings = {"109.99", "110.01", "-1.00"})
    void rejectsDestinationInconsistentWithSavedRate(String destination) {
        assertThatThrownBy(() -> Quote.restore(money("100.00", eur), money(destination, usd),
                money("0.00", eur), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destination amount must match the stored rate conversion");
    }

    @Test
    void rejectsWrongDestinationCurrency() {
        assertThatThrownBy(() -> Quote.restore(money("100.00", eur), money("110.00", eur),
                money("0.00", eur), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourceThatDoesNotMatchRateDirection() {
        assertThatThrownBy(() -> Quote.restore(money("100.00", usd), money("110.00", eur),
                money("0.00", usd), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount currency does not match source currency");
    }

    @Test
    void rejectsNegativeFee() {
        assertThatThrownBy(() -> Quote.restore(money("100.00", eur), money("110.00", usd),
                money("-0.01", eur), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("fee must be non-negative");
    }

    @Test
    void rejectsFeeInWrongCurrency() {
        assertThatThrownBy(() -> Quote.restore(money("100.00", eur), money("110.00", usd),
                money("0.00", usd), snapshot, created, created.plusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fee currency must match source amount currency");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsExpiryAtOrBeforeCreation(long seconds) {
        assertThatThrownBy(() -> Quote.restore(money("100.00", eur), money("110.00", usd),
                money("0.00", eur), snapshot, created, created.plusSeconds(seconds)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("expiry must be after creation");
    }

    @Test
    void acceptsZeroRoundedDestinationAndZeroFee() {
        var rate = new RateSnapshot(DirectedExchangeRate.of(new BigDecimal("0.00000001"), eur, usd),
                "provider", "v1", created);
        var restored = Quote.restore(money("0.01", eur), money("0.00", usd), money("0.00", eur),
                rate, created, created.plusSeconds(60));
        assertThat(restored.destinationAmount()).isEqualTo(money("0.00", usd));
    }

    private static Money money(String amount, Currency currency) {
        return Money.of(new BigDecimal(amount), currency);
    }
}

package dev.kavrin.fxtransfer.quote.infrastructure.rate;

import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredRateProviderTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant AS_OF = Instant.parse("2026-10-12T09:00:00Z");

    @Test
    void rejectsNullSnapshotList() {
        assertThatThrownBy(() -> new ConfiguredRateProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Snapshots list cannot be null");
    }

    @Test
    void rejectsEmptySnapshotList() {
        assertThatThrownBy(() -> new ConfiguredRateProvider(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Snapshots list cannot be empty");
    }

    @Test
    void rejectsNullSnapshotInsideList() {
        var snapshots = Arrays.asList(eurToUsdSnapshot(), null);

        assertThatThrownBy(() -> new ConfiguredRateProvider(snapshots))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("RateSnapshot cannot be null");
    }

    @Test
    void rejectsDuplicatePairWithDifferentRates() {
        assertThatThrownBy(() -> new ConfiguredRateProvider(List.of(eurToUsdSnapshot(), snapshot(EUR, USD, "1.20000000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate rate for currency pair");
    }

    @Test
    void rejectsRepeatedSnapshot() {
        var snapshot = eurToUsdSnapshot();

        assertThatThrownBy(() ->
                new ConfiguredRateProvider(List.of(snapshot, snapshot)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate rate for currency pair");
    }

    @Test
    void acceptsSingleConfiguredPair() {
        var configuredRateProvider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));
        assertThat(configuredRateProvider.getRate(EUR, USD)).isEqualTo(eurToUsdSnapshot());
    }

    @Test
    void acceptsSeparatelyConfiguredDirections() {
        var configuredRateProvider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot(), usdToEurSnapshot()));
        assertThat(configuredRateProvider.getRate(EUR, USD)).isEqualTo(eurToUsdSnapshot());
        assertThat(configuredRateProvider.getRate(USD, EUR)).isEqualTo(usdToEurSnapshot());
    }

    @Test
    void rejectsNullSourceCurrency() {
        var provider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));

        assertThatThrownBy(() -> provider.getRate(null, USD))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Source currency cannot be null");
    }

    @Test
    void rejectsNullDestinationCurrency() {
        var provider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));

        assertThatThrownBy(() -> provider.getRate(EUR, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Destination currency cannot be null");
    }

    @Test
    void rejectsSameCurrencyLookup() {
        var provider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));

        assertThatThrownBy(() -> provider.getRate(EUR, EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source and destination currencies must differ");
    }

    @Test
    void rejectsUnconfiguredPair() {
        var provider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));
        var gbp = Currency.getInstance("GBP");

        assertThatThrownBy(() -> provider.getRate(EUR, gbp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rate configured for currency pair")
                .hasMessageContaining("EUR", "GBP");
    }

    @Test
    void doesNotInferReverseRate() {
        var provider = new ConfiguredRateProvider(List.of(eurToUsdSnapshot()));

        assertThatThrownBy(() -> provider.getRate(USD, EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rate configured for currency pair");
    }

    @Test
    void preservesConfiguredSnapshotEvidence() {
        var historicalSnapshot = new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal("1.12345678"), EUR, USD),
                "historical-provider", "rates-2025-01", Instant.parse("2025-01-01T09:00:00Z"));
        var provider = new ConfiguredRateProvider(List.of(historicalSnapshot));

        // Record equality checks the rate, provider, version and original asOf together.
        assertThat(provider.getRate(EUR, USD)).isEqualTo(historicalSnapshot);
    }

    @Test
    void clearingOriginalListDoesNotRemoveConfiguredRate() {
        var originalSnapshot = eurToUsdSnapshot();
        var snapshots = new ArrayList<>(List.of(originalSnapshot));
        var provider = new ConfiguredRateProvider(snapshots);

        snapshots.clear();

        assertThat(provider.getRate(EUR, USD)).isEqualTo(originalSnapshot);
    }

    @Test
    void replacingOriginalListElementDoesNotChangeConfiguredRate() {
        var originalSnapshot = eurToUsdSnapshot();
        var snapshots = new ArrayList<>(List.of(originalSnapshot));
        var provider = new ConfiguredRateProvider(snapshots);

        snapshots.set(0, snapshot(EUR, USD, "1.20000000"));

        assertThat(provider.getRate(EUR, USD)).isEqualTo(originalSnapshot);
    }

    @Test
    void addingToOriginalListDoesNotConfigureAnotherPair() {
        var snapshots = new ArrayList<>(List.of(eurToUsdSnapshot()));
        var provider = new ConfiguredRateProvider(snapshots);

        snapshots.add(usdToEurSnapshot());

        assertThatThrownBy(() -> provider.getRate(USD, EUR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rate configured for currency pair");
    }

    private RateSnapshot eurToUsdSnapshot() {
        return snapshot(EUR, USD, "1.10000000");
    }

    private RateSnapshot usdToEurSnapshot() {
        return snapshot(USD, EUR, "0.88000000");
    }

    private RateSnapshot snapshot(Currency source, Currency destination, String decimalRate) {
        return new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal(decimalRate), source, destination),
                "test-fixture", "fixture-v1", AS_OF);
    }
}

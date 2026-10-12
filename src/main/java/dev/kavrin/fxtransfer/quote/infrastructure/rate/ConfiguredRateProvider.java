package dev.kavrin.fxtransfer.quote.infrastructure.rate;

import dev.kavrin.fxtransfer.quote.application.port.RateProvider;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfiguredRateProvider implements RateProvider {

    private final Map<CurrencyPair, RateSnapshot> rates;

    public ConfiguredRateProvider(List<RateSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "Snapshots list cannot be null");
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("Snapshots list cannot be empty");
        }

        Map<CurrencyPair, RateSnapshot> configuredRates = new HashMap<>();
        for (RateSnapshot snapshot : snapshots) {
            Objects.requireNonNull(snapshot, "RateSnapshot cannot be null");
            CurrencyPair pair = new CurrencyPair(snapshot.rate().sourceCurrency(), snapshot.rate().destinationCurrency());
            if (configuredRates.containsKey(pair)) {
                throw new IllegalArgumentException("Duplicate rate for currency pair: " + pair);
            }
            configuredRates.put(pair, snapshot);
        }

        this.rates = Map.copyOf(configuredRates);
    }

    @Override
    public RateSnapshot getRate(
            Currency sourceCurrency,
            Currency destinationCurrency
    ) {
        Objects.requireNonNull(sourceCurrency, "Source currency cannot be null");
        Objects.requireNonNull(destinationCurrency, "Destination currency cannot be null");
        if (sourceCurrency.equals(destinationCurrency)) {
            throw new IllegalArgumentException("Source and destination currencies must differ");
        }

        CurrencyPair pair = new CurrencyPair(sourceCurrency, destinationCurrency);
        RateSnapshot snapshot = rates.get(pair);
        if (snapshot == null) {
            throw new IllegalArgumentException("No rate configured for currency pair: " + pair);
        }
        return snapshot;
    }

    private record CurrencyPair(
            Currency sourceCurrency,
            Currency destinationCurrency
    ) {}
}

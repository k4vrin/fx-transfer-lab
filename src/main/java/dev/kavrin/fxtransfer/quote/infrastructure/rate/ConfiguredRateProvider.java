package dev.kavrin.fxtransfer.quote.infrastructure.rate;

import dev.kavrin.fxtransfer.quote.application.port.RateProvider;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConfiguredRateProvider implements RateProvider {
    private final Map<CurrencyPair, RateSnapshot> rates;

    private record CurrencyPair(
            Currency sourceCurrency,
            Currency destinationCurrency
    ) {
    }

    public ConfiguredRateProvider(List<RateSnapshot> snapshots) {
        Objects.requireNonNull(snapshots);
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("snapshots must not be empty");
        }
        if (snapshots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("snapshots must not be null");
        }
        Map<CurrencyPair, RateSnapshot> snapshotMap = new HashMap<>();

        for (RateSnapshot snapshot : snapshots) {
            CurrencyPair currPair = new CurrencyPair(
                    snapshot.rate().sourceCurrency(),
                    snapshot.rate().destinationCurrency()
            );
            snapshotMap.putIfAbsent(currPair, snapshot);
        }
        this.rates = Map.copyOf(snapshotMap);
    }

    @Override
    public RateSnapshot getRate(Currency source, Currency destination) {
        throw new UnsupportedOperationException("Lookup is the next step");
    }
}

package dev.kavrin.fxtransfer.quote;

import java.time.Instant;
import java.util.Objects;

public record RateSnapshot(
        DirectedExchangeRate rate,
        String provider,
        String version,
        Instant asOf) {

    public RateSnapshot {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(asOf, "asOf");
        if (provider.isBlank()) {
            throw new IllegalArgumentException("provider must not be blank");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
    }
}

package dev.kavrin.fxtransfer.quote;

import dev.kavrin.fxtransfer.fee.FeePolicy;
import dev.kavrin.fxtransfer.shared.Money;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class Quote {

    private final Money sourceAmount;
    private final Money destinationAmount;
    private final Money fee;
    private final RateSnapshot rateSnapshot;
    private final Instant createdAt;
    private final Instant expiresAt;

    private Quote(
            Money sourceAmount,
            Money destinationAmount,
            Money fee,
            RateSnapshot rateSnapshot,
            Instant createdAt,
            Instant expiresAt) {
        this.sourceAmount = sourceAmount;
        this.destinationAmount = destinationAmount;
        this.fee = fee;
        this.rateSnapshot = rateSnapshot;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static Quote create(
            Money sourceAmount,
            RateSnapshot rateSnapshot,
            FeePolicy feePolicy,
            Clock clock,
            Duration validity
    ) {
        Objects.requireNonNull(sourceAmount, "sourceAmount");
        Objects.requireNonNull(rateSnapshot, "rateSnapshot");
        Objects.requireNonNull(feePolicy, "feePolicy");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(validity, "validity");
        if (validity.isNegative() || validity.isZero()) {
            throw new IllegalArgumentException("validity must be positive");
        }
        if (!sourceAmount.isPositive()) {
            throw new IllegalArgumentException("source amount must be positive");
        }
        Money fee = feePolicy.calculate(sourceAmount);
        Objects.requireNonNull(fee, "feePolicy returned null");
        if (fee.currency() != sourceAmount.currency()) {
            throw new IllegalArgumentException("fee currency must match source amount currency");
        }
        if (fee.amount().signum() < 0) {
            throw new IllegalArgumentException("fee must be non-negative");
        }
        Instant now = clock.instant();
        return new Quote(
                sourceAmount,
                rateSnapshot.rate().convert(sourceAmount),
                fee,
                rateSnapshot,
                now,
                now.plus(validity)
        );
    }

    public RateSnapshot rateSnapshot() {
        return rateSnapshot;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Money sourceAmount() {
        return sourceAmount;
    }

    public Money destinationAmount() {
        return destinationAmount;
    }

    public Money fee() {
        return fee;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

}

package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;

import java.util.Objects;

public final class FixedFeePolicy implements FeePolicy {

    private final Money fixedFee;

    public FixedFeePolicy(Money fixedFee) {
        this.fixedFee = Objects.requireNonNull(fixedFee, "fixedFee is null");

        if (fixedFee.amount().signum() < 0) {
            throw new IllegalArgumentException("fixedFee cannot be negative");
        }
    }

    @Override
    public Money calculate(Money sourceAmount) {
        Objects.requireNonNull(sourceAmount, "sourceAmount");
        if (sourceAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("sourceAmount must be positive");
        }
        if (!fixedFee.currency().equals(sourceAmount.currency())) {
            throw new IllegalArgumentException("currency mismatch");
        }

        return fixedFee;
    }
}

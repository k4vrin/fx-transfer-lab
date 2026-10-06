package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PercentageFeePolicy implements FeePolicy {

    private final BigDecimal percentage;

    public PercentageFeePolicy(BigDecimal percentage) {
        if (percentage == null) {
            throw new NullPointerException("percentage is null");
        }
        if (percentage.signum() <= 0) {
            throw new IllegalArgumentException("percentage must be positive");
        }
        if (percentage.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("percentage must be less than 1");
        }
        this.percentage = percentage;
    }

    @Override
    public Money calculate(Money sourceAmount) {
        if (sourceAmount == null) {
            throw new NullPointerException("sourceAmount is null");
        }
        if (sourceAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException("sourceAmount must be positive");
        }

        BigDecimal feeAmount = sourceAmount.amount()
                .multiply(percentage)
                .setScale(2, RoundingMode.HALF_EVEN);
        return Money.of(feeAmount, sourceAmount.currency());
    }

}

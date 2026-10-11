package dev.kavrin.fxtransfer.fee.domain;

import dev.kavrin.fxtransfer.shared.domain.Money;

@FunctionalInterface
public interface FeePolicy {
    Money calculate(Money sourceAmount);
}

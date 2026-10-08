package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;

@FunctionalInterface
public interface FeePolicy {
    Money calculate(Money sourceAmount);
}

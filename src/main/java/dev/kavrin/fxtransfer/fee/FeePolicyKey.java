package dev.kavrin.fxtransfer.fee;

import java.util.Locale;
import java.util.Objects;

public record FeePolicyKey(String value) {

    public FeePolicyKey {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("policy key must not be blank");
        }
    }
}
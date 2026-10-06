package dev.kavrin.fxtransfer.fee;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FeePolicyRegistry {

    private final Map<FeePolicyKey, FeePolicy> policies = new HashMap<>();

    public void register(FeePolicyKey key, FeePolicy policy) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(policy, "policy");
        if (policies.containsKey(key)) {
            throw new IllegalArgumentException("policy key already registered: " + key.value());
        }

        policies.put(key, policy);
    }

    public FeePolicy get(FeePolicyKey key) {
        Objects.requireNonNull(key, "key");
        if (!policies.containsKey(key)) {
            throw new IllegalArgumentException("policy key is not registered: " + key.value());
        }
        return policies.get(key);
    }
}
package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FeePolicyRegistryTest {

    @Test
    void retrievesRegisteredPolicyUsingEquivalentKey() {
        FeePolicyRegistry registry = new FeePolicyRegistry();
        FeePolicy policy = new FixedFeePolicy(
                Money.of(new BigDecimal("2.00"), Currency.getInstance("EUR")));

        registry.register(new FeePolicyKey(" FIXED "), policy);

        assertThat(registry.get(new FeePolicyKey("fixed")))
                .isSameAs(policy);
    }

    @Test
    void rejectsDuplicateNormalizedKey() {
        FeePolicyRegistry registry = new FeePolicyRegistry();
        Currency eur = Currency.getInstance("EUR");
        FeePolicy original = new FixedFeePolicy(
                Money.of(new BigDecimal("2.00"), eur));
        FeePolicy replacement = new FixedFeePolicy(
                Money.of(new BigDecimal("3.00"), eur));

        registry.register(new FeePolicyKey("VIP"), original);

        assertThatThrownBy(() ->
                registry.register(new FeePolicyKey(" vip "), replacement))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(registry.get(new FeePolicyKey("vip")))
                .isSameAs(original);
    }

    @Test
    void rejectsUnregisteredPolicyKey() {
        FeePolicyRegistry registry = new FeePolicyRegistry();

        assertThatThrownBy(() ->
                registry.get(new FeePolicyKey("premium")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("policy key is not registered: premium");
    }

    @Test
    void rejectsNullPolicy() {
        FeePolicyRegistry registry = new FeePolicyRegistry();
        FeePolicyKey key = new FeePolicyKey("fixed");

        assertThatThrownBy(() -> registry.register(key, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("policy");
    }

    @Test
    void rejectsNullRegistrationKey() {
        FeePolicyRegistry registry = new FeePolicyRegistry();
        FeePolicy policy = new FixedFeePolicy(
                Money.of(new BigDecimal("2.00"), Currency.getInstance("EUR")));

        assertThatThrownBy(() -> registry.register(null, policy))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    @Test
    void rejectsNullLookupKey() {
        FeePolicyRegistry registry = new FeePolicyRegistry();

        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("key");
    }

    @Test
    void supportsThirdPolicyThroughCommonContract() {
        FeePolicyRegistry registry = new FeePolicyRegistry();
        FeePolicy vipPolicy = sourceAmount -> {
            Objects.requireNonNull(sourceAmount, "sourceAmount");
            if (sourceAmount.amount().signum() <= 0) {
                throw new IllegalArgumentException("sourceAmount must be positive");
            }
            return Money.of(BigDecimal.ZERO, sourceAmount.currency());
        };

        registry.register(new FeePolicyKey("vip"), vipPolicy);

        Money sourceAmount = Money.of(
                new BigDecimal("100.00"), Currency.getInstance("EUR"));
        FeePolicy selectedPolicy = registry.get(new FeePolicyKey("VIP"));
        Money fee = selectedPolicy.calculate(sourceAmount);

        assertThat(fee).isEqualTo(
                Money.of(new BigDecimal("0.00"), sourceAmount.currency()));
    }
}

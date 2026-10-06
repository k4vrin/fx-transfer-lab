package dev.kavrin.fxtransfer.fee;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FeePolicyKeyTest {

    @Test
    void normalizesPolicyKey() {
        FeePolicyKey key = new FeePolicyKey(" VIP ");

        assertThat(key.value()).isEqualTo("vip");
    }

    @Test
    void rejectsBlankPolicyKey() {
        assertThatThrownBy(() -> new FeePolicyKey("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equivalentKeysHaveEqualValuesAndHashCodes() {
        FeePolicyKey first = new FeePolicyKey(" VIP ");
        FeePolicyKey second = new FeePolicyKey("vip");

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void rejectsNullPolicyKey() {
        assertThatThrownBy(() -> new FeePolicyKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("value");
    }

    @Test
    void rejectsEmptyPolicyKey() {
        assertThatThrownBy(() -> new FeePolicyKey(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

}

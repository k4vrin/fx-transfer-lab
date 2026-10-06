package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PercentageFeePolicyTest {

    @Test
    void calculatesPercentageFeeInSourceCurrency() {
        Currency eur = Currency.getInstance("EUR");
        Money sourceAmount = Money.of(new BigDecimal("100.00"), eur);
        FeePolicy policy = new PercentageFeePolicy(
                new BigDecimal("0.02"));

        Money result = policy.calculate(sourceAmount);

        assertThat(result).isEqualTo(
                Money.of(new BigDecimal("2.00"), eur));
    }

    @Test
    void roundsExactHalfToEvenCent() {
        Currency eur = Currency.getInstance("EUR");
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.01"));
        Money sourceAmount = Money.of(new BigDecimal("100.50"), eur);

        Money result = policy.calculate(sourceAmount);

        assertThat(result).isEqualTo(
                Money.of(new BigDecimal("1.00"), eur));
    }

    @Test
    void roundsExactHalfUpToEvenCent() {
        Currency eur = Currency.getInstance("EUR");
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.01"));
        Money sourceAmount = Money.of(new BigDecimal("101.50"), eur);

        Money result = policy.calculate(sourceAmount);

        assertThat(result).isEqualTo(
                Money.of(new BigDecimal("1.02"), eur));
    }

    @Test
    void rejectsZeroPercentageRate() {
        assertThatThrownBy(() ->
                new PercentageFeePolicy(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativePercentageRate() {
        assertThatThrownBy(() ->
                new PercentageFeePolicy(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPercentageRate() {
        assertThatThrownBy(() -> new PercentageFeePolicy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullSourceAmount() {
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.02"));

        assertThatThrownBy(() -> policy.calculate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsZeroSourceAmount() {
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.02"));
        Money sourceAmount = Money.of(
                BigDecimal.ZERO, Currency.getInstance("EUR"));

        assertThatThrownBy(() -> policy.calculate(sourceAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSourceAmount() {
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.02"));
        Money sourceAmount = Money.of(
                new BigDecimal("-100.00"), Currency.getInstance("EUR"));

        assertThatThrownBy(() -> policy.calculate(sourceAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsFeeToRoundToZero() {
        Currency usd = Currency.getInstance("USD");
        FeePolicy policy = new PercentageFeePolicy(new BigDecimal("0.01"));
        Money sourceAmount = Money.of(new BigDecimal("0.01"), usd);

        Money result = policy.calculate(sourceAmount);

        assertThat(result).isEqualTo(
                Money.of(new BigDecimal("0.00"), usd));
    }

    @Test
    void rejectsPercentageRateAboveOne() {
        assertThatThrownBy(() ->
                new PercentageFeePolicy(new BigDecimal("1.25")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPercentageRateOfOne() {
        assertThatThrownBy(() ->
                new PercentageFeePolicy(BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

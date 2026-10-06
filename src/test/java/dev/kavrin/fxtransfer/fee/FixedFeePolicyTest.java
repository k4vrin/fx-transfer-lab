package dev.kavrin.fxtransfer.fee;

import dev.kavrin.fxtransfer.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FixedFeePolicyTest {

    @Test
    void returnsConfiguredFeeInSourceCurrency() {
        Currency euro = Currency.getInstance("EUR");
        Money fixedFee = Money.of(new BigDecimal("2.00"), euro);
        Money sourceAmount = Money.of(new BigDecimal("100.00"), euro);
        FeePolicy policy = new FixedFeePolicy(fixedFee);

        Money result = policy.calculate(sourceAmount);

        assertThat(result).isEqualTo(fixedFee);
    }

    @Test
    void rejectsSourceCurrencyMismatch() {
        Money fixedFee = Money.of(
                new BigDecimal("2.00"), Currency.getInstance("EUR"));
        Money sourceAmount = Money.of(
                new BigDecimal("100.00"), Currency.getInstance("USD"));
        FeePolicy policy = new FixedFeePolicy(fixedFee);

        assertThatThrownBy(() -> policy.calculate(sourceAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullFixedFee() {
        assertThatThrownBy(() -> new FixedFeePolicy(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNegativeFixedFee() {
        Money negativeFee = Money.of(
                new BigDecimal("-1.00"),
                Currency.getInstance("EUR"));

        assertThatThrownBy(() -> new FixedFeePolicy(negativeFee))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsZeroFixedFee() {
        Currency eur = Currency.getInstance("EUR");
        Money zeroFee = Money.of(new BigDecimal("0.00"), eur);
        FeePolicy policy = new FixedFeePolicy(zeroFee);
        Money sourceAmount = Money.of(new BigDecimal("100.00"), eur);

        assertThat(policy.calculate(sourceAmount)).isEqualTo(zeroFee);
    }

    @Test
    void rejectsNullSourceAmount() {
        Money fixedFee = Money.of(
                new BigDecimal("2.00"), Currency.getInstance("EUR"));
        FeePolicy policy = new FixedFeePolicy(fixedFee);

        assertThatThrownBy(() -> policy.calculate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceAmount");
    }

    @Test
    void rejectsZeroSourceAmount() {
        Currency eur = Currency.getInstance("EUR");
        FeePolicy policy = new FixedFeePolicy(
                Money.of(new BigDecimal("2.00"), eur));
        Money sourceAmount = Money.of(new BigDecimal("0.00"), eur);

        assertThatThrownBy(() -> policy.calculate(sourceAmount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeSourceAmount() {
        Currency eur = Currency.getInstance("EUR");
        FeePolicy policy = new FixedFeePolicy(
                Money.of(new BigDecimal("2.00"), eur));
        Money sourceAmount = Money.of(new BigDecimal("-100.00"), eur);

        assertThatThrownBy(() -> policy.calculate(sourceAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceAmount must be positive");
    }
}

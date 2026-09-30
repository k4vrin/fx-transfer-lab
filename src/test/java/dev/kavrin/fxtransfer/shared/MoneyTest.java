package dev.kavrin.fxtransfer.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MoneyTest {

    @Test
    void normalizesEuroAmountToTwoDecimalPlaces() {
        Money money = Money.of(
                new BigDecimal("10.0"),
                Currency.getInstance("EUR")
        );

        assertThat(money.amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(money.currency()).isEqualTo(Currency.getInstance("EUR"));
    }


    @Test
    void rejectsAmountThatRequiresRounding() {
        assertThatThrownBy(() -> Money.of(
                new BigDecimal("10.001"),
                Currency.getInstance("EUR")
        )).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatThrownBy(() -> Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("GBP")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency is not supported");
    }


    @Test
    void equalAmountAndCurrenciesProduceEqualMoney() {
        Money first = Money.of(
                new BigDecimal("10.0"),
                Currency.getInstance("EUR")
        );

        Money second = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void sameAmountInDifferentCurrenciesIsNotEqual() {
        Money euros = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );
        Money dollars = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("USD")
        );

        assertThat(euros).isNotEqualTo(dollars);
    }

    @Test
    void addsAmountsInTheSameCurrency() {
        Money first = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );
        Money second = Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("EUR")
        );

        Money result = first.add(second);

        assertThat(result).isEqualTo(Money.of(
                new BigDecimal("12.50"),
                Currency.getInstance("EUR")
        ));
        assertThat(first.amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(second.amount()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void rejectsAdditionAcrossCurrencies() {
        Money euros = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );

        Money dollars = Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("USD")
        );

        assertThatThrownBy(() -> euros.add(dollars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency mismatch");
    }

}

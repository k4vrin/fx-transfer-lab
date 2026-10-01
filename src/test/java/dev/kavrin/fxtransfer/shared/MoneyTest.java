package dev.kavrin.fxtransfer.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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

    @Test
    void subtractsAmountsInTheSameCurrency() {
        Money first = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );
        Money second = Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("EUR")
        );

        Money result = first.subtract(second);

        assertThat(result).isEqualTo(Money.of(
                new BigDecimal("7.50"),
                Currency.getInstance("EUR")
        ));
        assertThat(first.amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(second.amount()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void rejectsSubtractionAcrossCurrencies() {
        Money euros = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );

        Money dollars = Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("USD")
        );

        assertThatThrownBy(() -> euros.subtract(dollars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency mismatch");
    }

    @Test
    void comparesAmountsInTheSameCurrency() {
        Money smaller = Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("EUR")
        );
        Money larger = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );

        assertThat(smaller.compareTo(larger)).isNegative();

        assertThat(larger.compareTo(smaller)).isPositive();
        assertThat(smaller.compareTo(Money.of(
                new BigDecimal("2.50"),
                Currency.getInstance("EUR")
        ))).isZero();
    }

    @Test
    void rejectsComparisonAcrossCurrencies() {
        Money euros = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("EUR")
        );
        Money dollars = Money.of(
                new BigDecimal("10.00"),
                Currency.getInstance("USD")
        );

        assertThatThrownBy(() -> euros.compareTo(dollars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency mismatch");
    }

    @Test
    void identifiesWhetherAmountIsZero() {
        Money zero = Money.of(
                new BigDecimal("0.00"),
                Currency.getInstance("EUR")
        );
        Money nonzero = Money.of(
                new BigDecimal("0.01"),
                Currency.getInstance("EUR")
        );

        assertThat(zero.isZero()).isTrue();
        assertThat(nonzero.isZero()).isFalse();
    }

    @Test
    void identifiesOnlyAmountsGreaterThanZeroAsPositive() {
        Currency eur = Currency.getInstance("EUR");

        Money positive = Money.of(new BigDecimal("0.01"), eur);
        Money zero = Money.of(new BigDecimal("0.00"), eur);
        Money negative = Money.of(new BigDecimal("-0.01"), eur);

        assertThat(positive.isPositive()).isTrue();
        assertThat(zero.isPositive()).isFalse();
        assertThat(negative.isPositive()).isFalse();
    }

    @Test
    void rejectsNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(
                        null,
                        Currency.getInstance("EUR")
                ))
                .withMessage("amount is null");
    }

    @Test
    void rejectsNullCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of(
                        new BigDecimal("10.00"),
                        null
                ))
                .withMessage("currency is null");
    }

    @Test
    void doesNotExposeDoubleBasedConstruction() {
        assertThatThrownBy(() ->
                Money.class.getMethod(
                        "of",
                        double.class,
                        Currency.class
                )
        ).isInstanceOf(NoSuchMethodException.class);
    }

}

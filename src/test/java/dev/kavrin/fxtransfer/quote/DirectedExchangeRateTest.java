package dev.kavrin.fxtransfer.quote;

import dev.kavrin.fxtransfer.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.*;

class DirectedExchangeRateTest {

    @Test
    void normalizesRateAndPreservesDirection() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.1"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        );

        assertThat(rate.rate())
                .isEqualTo(new BigDecimal("1.10000000"));
        assertThat(rate.sourceCurrency())
                .isEqualTo(Currency.getInstance("EUR"));
        assertThat(rate.destinationCurrency())
                .isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void acceptsRedundantDecimalPlacesWhenNoRoundingIsRequired() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.100000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        );

        assertThat(rate.rate())
                .isEqualTo(new BigDecimal("1.10000000"));
    }

    @Test
    void rejectsUnsupportedSourceCurrency() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("1.10000000"),
                Currency.getInstance("GBP"),
                Currency.getInstance("USD")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source currency is not supported");
    }

    @Test
    void rejectsUnsupportedDestinationCurrency() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("1.10000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("GBP")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destination currency is not supported");
    }

    @Test
    void rejectsSameSourceAndDestinationCurrency() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("1.10000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("EUR")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source and destination currencies must be different");
    }

    @Test
    void rejectsZeroRate() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("0.00000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be positive");
    }

    @Test
    void rejectsNegativeRate() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("-1.10000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be positive");
    }

    @Test
    void rejectsNullRate() {
        assertThatNullPointerException()
                .isThrownBy(() -> DirectedExchangeRate.of(
                        null,
                        Currency.getInstance("EUR"),
                        Currency.getInstance("USD")
                ))
                .withMessage("rate must not be null");
    }

    @Test
    void rejectsNullSourceCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> DirectedExchangeRate.of(
                        new BigDecimal("1.10000000"),
                        null,
                        Currency.getInstance("USD")
                ))
                .withMessage("source currency must not be null");
    }

    @Test
    void rejectsNullDestinationCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> DirectedExchangeRate.of(
                        new BigDecimal("1.10000000"),
                        Currency.getInstance("EUR"),
                        null
                ))
                .withMessage("destination currency must not be null");
    }

    @Test
    void rejectsRateThatRequiresRounding() {
        assertThatThrownBy(() -> DirectedExchangeRate.of(
                new BigDecimal("1.123456789"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        ))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void convertsSourceMoneyInTheSupportedDirection() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        );
        Money source = Money.of(
                new BigDecimal("100.00"),
                Currency.getInstance("EUR")
        );

        Money result = rate.convert(source);

        assertThat(result).isEqualTo(Money.of(
                new BigDecimal("110.00"),
                Currency.getInstance("USD")
        ));
    }

    @Test
    void roundsExactHalfToTheEvenNeighbor() {
        Money oneEuro = Money.of(
                new BigDecimal("1.00"),
                Currency.getInstance("EUR")
        );

        Money lowerEvenResult = DirectedExchangeRate.of(
                new BigDecimal("1.00500000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        ).convert(oneEuro);

        Money upperEvenResult = DirectedExchangeRate.of(
                new BigDecimal("1.01500000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        ).convert(oneEuro);

        assertThat(lowerEvenResult.amount()).isEqualTo(new BigDecimal("1.00"));
        assertThat(upperEvenResult.amount()).isEqualTo(new BigDecimal("1.02"));
    }

    @Test
    void rejectsMoneyInTheWrongSourceCurrency() {
        DirectedExchangeRate rate = DirectedExchangeRate.of(
                new BigDecimal("1.10000000"),
                Currency.getInstance("EUR"),
                Currency.getInstance("USD")
        );
        Money dollars = Money.of(
                new BigDecimal("100.00"),
                Currency.getInstance("USD")
        );

        assertThatThrownBy(() -> rate.convert(dollars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount currency does not match source currency");
    }
}
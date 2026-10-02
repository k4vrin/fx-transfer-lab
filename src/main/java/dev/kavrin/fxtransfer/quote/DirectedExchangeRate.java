package dev.kavrin.fxtransfer.quote;

import dev.kavrin.fxtransfer.shared.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable exchange rate that converts money in one specific currency direction.
 *
 * <p>The rate represents destination-currency units per one source-currency unit and is
 * stored with eight decimal places without implicit rounding. Conversion accepts only
 * source-currency money and rounds the final destination amount to two decimal places
 * using {@link RoundingMode#HALF_EVEN}.</p>
 */
public final class DirectedExchangeRate {
    private final BigDecimal rate;
    private final Currency sourceCurrency;
    private final Currency destinationCurrency;
    private static final Set<Currency> SUPPORTED_CURRENCIES = Set.of(
            Currency.getInstance("EUR"),
            Currency.getInstance("USD")
    );

    private DirectedExchangeRate(BigDecimal rate, Currency sourceCurrency, Currency destinationCurrency) {
        this.rate = rate;
        this.sourceCurrency = sourceCurrency;
        this.destinationCurrency = destinationCurrency;
    }

    /**
     * Creates a rate for a specific source-to-destination currency direction.
     *
     * @param rate destination-currency units per one source-currency unit
     * @param sourceCurrency the currency accepted by this rate
     * @param destinationCurrency the currency produced by this rate
     * @return an immutable directed exchange rate
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if either currency is unsupported, both currencies
     *         are equal, or the rate is not positive
     * @throws ArithmeticException if normalizing the rate to eight decimal places would
     *         require rounding
     */
    public static DirectedExchangeRate of(BigDecimal rate, Currency sourceCurrency, Currency destinationCurrency) {
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(sourceCurrency, "source currency must not be null");
        Objects.requireNonNull(destinationCurrency, "destination currency must not be null");
        if (!SUPPORTED_CURRENCIES.contains(sourceCurrency)) {
            throw new IllegalArgumentException("source currency is not supported");
        }
        if (!SUPPORTED_CURRENCIES.contains(destinationCurrency)) {
            throw new IllegalArgumentException("destination currency is not supported");
        }
        if (sourceCurrency.equals(destinationCurrency)) {
            throw new IllegalArgumentException("source and destination currencies must be different");
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        return new DirectedExchangeRate(rate.setScale(8, RoundingMode.UNNECESSARY), sourceCurrency, destinationCurrency);
    }

    public BigDecimal rate() {
        return rate;
    }

    public Currency sourceCurrency() {
        return sourceCurrency;
    }

    public Currency destinationCurrency() {
        return destinationCurrency;
    }

    /**
     * Converts source-currency money into this rate's destination currency.
     *
     * <p>The multiplication is exact and the result is rounded once, at the destination
     * monetary boundary, using {@link RoundingMode#HALF_EVEN}.</p>
     *
     * @param money the source-currency value to convert
     * @return the converted value in the destination currency
     * @throws NullPointerException if {@code money} is {@code null}
     * @throws IllegalArgumentException if the money's currency does not match this rate's
     *         source currency
     */
    public Money convert(Money money) {
        Objects.requireNonNull(money, "amount must not be null");
        if (!money.currency().equals(sourceCurrency)) {
            throw new IllegalArgumentException("amount currency does not match source currency");
        }
        BigDecimal convertedAmount = money.amount().multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
        return Money.of(convertedAmount, destinationCurrency);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DirectedExchangeRate that)) return false;
        return rate.equals(that.rate) &&
                sourceCurrency.equals(that.sourceCurrency) &&
                destinationCurrency.equals(that.destinationCurrency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rate, sourceCurrency, destinationCurrency);
    }
}

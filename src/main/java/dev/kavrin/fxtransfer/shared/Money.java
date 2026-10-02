package dev.kavrin.fxtransfer.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/**
 * An immutable monetary amount in one of the currencies supported by the application.
 *
 * <p>Amounts are represented with two decimal places. Construction never rounds: an
 * amount with meaningful precision beyond two decimal places is rejected. Arithmetic
 * is permitted only between values in the same currency.</p>
 */
public class Money {

    private final BigDecimal amount;
    private final Currency currency;
    private static final Set<Currency> SUPPORTED_CURRENCIES = Set.of(
            Currency.getInstance("EUR"),
            Currency.getInstance("USD")
    );

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }

    /**
     * Creates a monetary value and normalizes its amount to two decimal places.
     *
     * @param amount the exact monetary amount
     * @param currency the amount's currency
     * @return an immutable monetary value
     * @throws NullPointerException if {@code amount} or {@code currency} is {@code null}
     * @throws IllegalArgumentException if the currency is unsupported
     * @throws ArithmeticException if normalizing the amount would require rounding
     */
    public static Money of(BigDecimal amount, Currency currency) {

        Objects.requireNonNull(amount, "amount is null");
        Objects.requireNonNull(currency, "currency is null");

        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new IllegalArgumentException("currency is not supported");
        }

        return new Money(
                amount.setScale(2, RoundingMode.UNNECESSARY),
                currency
        );
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    /**
     * Adds another monetary value in the same currency.
     *
     * @param money the value to add
     * @return the sum in this value's currency
     * @throws NullPointerException if {@code money} is {@code null}
     * @throws IllegalArgumentException if the currencies differ
     */
    public Money add(Money money) {
        requireSameCurrency(money);

        return Money.of(this.amount.add(money.amount), money.currency);
    }

    /**
     * Subtracts another monetary value in the same currency.
     *
     * @param money the value to subtract
     * @return the difference in this value's currency
     * @throws NullPointerException if {@code money} is {@code null}
     * @throws IllegalArgumentException if the currencies differ
     */
    public Money subtract(Money money) {
        requireSameCurrency(money);

        return Money.of(this.amount.subtract(money.amount), money.currency);
    }

    /**
     * Compares this amount numerically with another amount in the same currency.
     *
     * @param money the value to compare with
     * @return a negative value, zero, or a positive value when this amount is less than,
     *         equal to, or greater than {@code money}, respectively
     * @throws NullPointerException if {@code money} is {@code null}
     * @throws IllegalArgumentException if the currencies differ
     */
    public int compareTo(Money money) {
        requireSameCurrency(money);

        return this.amount.compareTo(money.amount);
    }

    public boolean isZero() {
        return this.amount.signum() == 0;
    }

    public boolean isPositive() {
        return this.amount.signum() == 1;
    }

    private void requireSameCurrency(Money money) {
        Objects.requireNonNull(money, "money");

        if (!currency.equals(money.currency)) {
            throw new IllegalArgumentException("currency mismatch");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money money)) return false;
        return amount.equals(money.amount) && currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
}

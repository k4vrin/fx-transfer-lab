package dev.kavrin.fxtransfer.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public class Money {

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, Currency currency) {

        Objects.requireNonNull(amount, "amount is null");
        Objects.requireNonNull(currency, "currency is null");

        if (!currency.equals(Currency.getInstance("EUR"))
                && !currency.equals(Currency.getInstance("USD"))) {
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

    public Money add(Money money) {
        requireSameCurrency(money);

        return Money.of(this.amount.add(money.amount), money.currency);
    }

    public Money subtract(Money money) {
        requireSameCurrency(money);

        return Money.of(this.amount.subtract(money.amount), money.currency);
    }

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

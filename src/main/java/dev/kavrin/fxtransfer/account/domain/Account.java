package dev.kavrin.fxtransfer.account.domain;

import dev.kavrin.fxtransfer.shared.domain.Money;

import java.util.Objects;

/**
 * An immutable account state, independent of persistence.
 * Balance signs follow the account type; customer accounts prohibit overdrafts.
 */
public final class Account {

    private final String accountId;
    private final String customerId;
    private final AccountType accountType;
    private final Money openingBalance;
    private final Money bookedBalance;

    public Account(
            String accountId,
            String customerId,
            AccountType accountType,
            Money openingBalance,
            Money bookedBalance
    ) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(accountType, "accountType");
        Objects.requireNonNull(openingBalance, "openingBalance");
        Objects.requireNonNull(bookedBalance, "bookedBalance");

        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        if (!openingBalance.currency().equals(bookedBalance.currency())) {
            throw new IllegalArgumentException("account balances must use the same currency");
        }

        if (accountType == AccountType.CUSTOMER_LIABILITY) {
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("customer accounts must have a customerId");
            }
            if (openingBalance.amount().signum() < 0 || bookedBalance.amount().signum() < 0) {
                throw new IllegalArgumentException("customer account balances must not be negative");
            }
        } else if (customerId != null) {
            throw new IllegalArgumentException("internal accounts must not have a customerId");
        }

        this.accountId = accountId;
        this.customerId = customerId;
        this.accountType = accountType;
        this.openingBalance = openingBalance;
        this.bookedBalance = bookedBalance;
    }

    public String accountId() {
        return accountId;
    }

    public String customerId() {
        return customerId;
    }

    public AccountType accountType() {
        return accountType;
    }

    public Money openingBalance() {
        return openingBalance;
    }

    public Money bookedBalance() {
        return bookedBalance;
    }


    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String accountId;
        private String customerId;
        private AccountType accountType;
        private Money openingBalance;
        private Money bookedBalance;

        private Builder() {
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder accountType(AccountType accountType) {
            this.accountType = accountType;
            return this;
        }

        public Builder openingBalance(Money openingBalance) {
            this.openingBalance = openingBalance;
            return this;
        }

        public Builder bookedBalance(Money bookedBalance) {
            this.bookedBalance = bookedBalance;
            return this;
        }

        public Account build() {
            return new Account(
                    accountId,
                    customerId,
                    accountType,
                    openingBalance,
                    bookedBalance
            );
        }
    }

}

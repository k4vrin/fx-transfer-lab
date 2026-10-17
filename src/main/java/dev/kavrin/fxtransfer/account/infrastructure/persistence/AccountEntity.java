package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.domain.AccountType;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "account_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(
            name = "opening_balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal openingBalance;

    @Column(
            name = "booked_balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal bookedBalance;

    protected AccountEntity() {
        // Required by JPA when reconstructing an entity from a database row.
    }

    public AccountEntity(
            String accountId,
            String customerId,
            AccountType accountType,
            String currency,
            BigDecimal openingBalance,
            BigDecimal bookedBalance
    ) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.accountType = accountType;
        this.currency = currency;
        this.openingBalance = openingBalance;
        this.bookedBalance = bookedBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getBookedBalance() {
        return bookedBalance;
    }
}

package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.domain.Account;
import dev.kavrin.fxtransfer.shared.domain.Money;

import java.util.Currency;
import java.util.Objects;

public final class AccountEntityMapper {

    private AccountEntityMapper() {
    }

    public static Account toDomain(AccountEntity entity) {
        Objects.requireNonNull(entity, "entity");

        var currency = Currency.getInstance(
                entity.getCurrency()
        );

        return Account.builder()
                .accountId(entity.getAccountId())
                .customerId(entity.getCustomerId())
                .accountType(entity.getAccountType())
                .openingBalance(Money.of(entity.getOpeningBalance(), currency))
                .bookedBalance(Money.of(entity.getBookedBalance(), currency))
                .build();
    }
}

package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountEntityMapperTest {

    @Test
    void normalizesZeroBalancesToTwoDecimalPlaces() {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", BigDecimal.ZERO, BigDecimal.ZERO);

        var account = AccountEntityMapper.toDomain(entity);

        assertThat(account.openingBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.bookedBalance().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.openingBalance().amount().scale()).isEqualTo(2);
        assertThat(account.bookedBalance().amount().scale()).isEqualTo(2);
        assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void preservesEveryCustomerAccountField(String currency) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                currency, new BigDecimal("123.45"), new BigDecimal("67.89"));

        var account = AccountEntityMapper.toDomain(entity);

        assertThat(account.accountId()).isEqualTo("account-1");
        assertThat(account.customerId()).isEqualTo("customer-1");
        assertThat(account.accountType()).isEqualTo(AccountType.CUSTOMER_LIABILITY);
        assertThat(account.openingBalance().amount()).isEqualByComparingTo("123.45");
        assertThat(account.bookedBalance().amount()).isEqualByComparingTo("67.89");
        assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance(currency));
        assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance(currency));
    }

    @ParameterizedTest
    @CsvSource({
            "FX_CLEARING_ASSET, EUR",
            "FX_CLEARING_ASSET, USD",
            "FEE_REVENUE, EUR",
            "FEE_REVENUE, USD"
    })
    void preservesInternalAccountWithNullOwnerAndSignedBalances(AccountType type, String currency) {
        var entity = entity(null, type, currency,
                new BigDecimal("-10.25"), new BigDecimal("-20.50"));

        var account = AccountEntityMapper.toDomain(entity);

        assertThat(account.accountId()).isEqualTo("account-1");
        assertThat(account.customerId()).isNull();
        assertThat(account.accountType()).isEqualTo(type);
        assertThat(account.openingBalance().amount()).isEqualByComparingTo("-10.25");
        assertThat(account.bookedBalance().amount()).isEqualByComparingTo("-20.50");
        assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance(currency));
        assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance(currency));
    }

    @Test
    void rejectsNullEntity() {
        assertThatThrownBy(() -> AccountEntityMapper.toDomain(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("entity");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "JPY", BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> AccountEntityMapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency is not supported");
    }

    @ParameterizedTest
    @CsvSource({"1.001, 0.00", "0.00, 1.001"})
    void rejectsBalancesThatWouldRequireRounding(String opening, String booked) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", new BigDecimal(opening), new BigDecimal(booked));

        assertThatThrownBy(() -> AccountEntityMapper.toDomain(entity))
                .isInstanceOf(ArithmeticException.class);
    }

    @ParameterizedTest
    @CsvSource({"-0.01, 0.00", "0.00, -0.01"})
    void rejectsNegativeCustomerBalance(String opening, String booked) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", new BigDecimal(opening), new BigDecimal(booked));

        assertThatThrownBy(() -> AccountEntityMapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer account balances must not be negative");
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void rejectsOwnershipThatDoesNotMatchAccountType(AccountType type) {
        var owner = type == AccountType.CUSTOMER_LIABILITY ? null : "customer-1";
        var entity = entity(owner, type, "EUR", BigDecimal.ZERO, BigDecimal.ZERO);

        var expectedMessage = type == AccountType.CUSTOMER_LIABILITY
                ? "customer accounts must have a customerId"
                : "internal accounts must not have a customerId";
        assertThatThrownBy(() -> AccountEntityMapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    private static AccountEntity entity(
            String customerId,
            AccountType type,
            String currency,
            BigDecimal opening,
            BigDecimal booked
    ) {
        return new AccountEntity("account-1", customerId, type, currency, opening, booked);
    }
}

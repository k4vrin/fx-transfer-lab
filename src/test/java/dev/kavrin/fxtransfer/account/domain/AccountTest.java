package dev.kavrin.fxtransfer.account.domain;

import dev.kavrin.fxtransfer.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void preservesCustomerAccountState(String currency) {
        var opening = money("123.45", currency);
        var booked = money("67.89", currency);

        var account = new Account("account-1", "customer-1",
                AccountType.CUSTOMER_LIABILITY, opening, booked);

        assertThat(account.accountId()).isEqualTo("account-1");
        assertThat(account.customerId()).isEqualTo("customer-1");
        assertThat(account.accountType()).isEqualTo(AccountType.CUSTOMER_LIABILITY);
        assertThat(account.openingBalance()).isEqualTo(opening);
        assertThat(account.bookedBalance()).isEqualTo(booked);
    }

    @Test
    void acceptsZeroCustomerBalances() {
        var account = new Account("account-1", "customer-1",
                AccountType.CUSTOMER_LIABILITY, money("0", "EUR"), money("0", "EUR"));

        assertThat(account.openingBalance().isZero()).isTrue();
        assertThat(account.bookedBalance().isZero()).isTrue();
        assertThat(account.bookedBalance().amount().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "0.00, -0.01",
            "-0.01, 0.00"
    })
    void rejectsNegativeCustomerBalance(String opening, String booked) {
        assertThatThrownBy(() -> new Account("account-1", "customer-1",
                AccountType.CUSTOMER_LIABILITY, money(opening, "EUR"), money(booked, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer account balances must not be negative");
    }

    @ParameterizedTest
    @CsvSource({
            "FX_CLEARING_ASSET, EUR",
            "FX_CLEARING_ASSET, USD",
            "FEE_REVENUE, EUR",
            "FEE_REVENUE, USD"
    })
    void acceptsInternalAccountWithNullOwnerAndSignedBalances(AccountType type, String currency) {
        var account = new Account("internal-1", null, type,
                money("-10.25", currency), money("-20.50", currency));

        assertThat(account.accountId()).isEqualTo("internal-1");
        assertThat(account.customerId()).isNull();
        assertThat(account.accountType()).isEqualTo(type);
        assertThat(account.openingBalance()).isEqualTo(money("-10.25", currency));
        assertThat(account.bookedBalance()).isEqualTo(money("-20.50", currency));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsCustomerAccountWithoutOwner(String customerId) {
        assertThatThrownBy(() -> new Account("account-1", customerId,
                AccountType.CUSTOMER_LIABILITY, money("0", "EUR"), money("0", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("customer accounts must have a customerId");
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"FX_CLEARING_ASSET", "FEE_REVENUE"})
    void rejectsInternalAccountWithOwner(AccountType type) {
        assertThatThrownBy(() -> new Account("internal-1", "customer-1", type,
                money("0", "EUR"), money("0", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("internal accounts must not have a customerId");
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void rejectsBalancesInDifferentCurrencies(AccountType type) {
        var customerId = type == AccountType.CUSTOMER_LIABILITY ? "customer-1" : null;

        assertThatThrownBy(() -> new Account("account-1", customerId, type,
                money("0", "EUR"), money("0", "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("account balances must use the same currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankAccountId(String accountId) {
        assertThatThrownBy(() -> new Account(accountId, "customer-1",
                AccountType.CUSTOMER_LIABILITY, money("0", "EUR"), money("0", "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accountId must not be blank");
    }

    @ParameterizedTest
    @MethodSource("missingRequiredValues")
    void rejectsMissingRequiredValue(
            String accountId,
            AccountType type,
            Money opening,
            Money booked,
            String field
    ) {
        assertThatThrownBy(() -> new Account(accountId, "customer-1", type, opening, booked))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(field);
    }

    private static Stream<Arguments> missingRequiredValues() {
        var zero = money("0", "EUR");
        return Stream.of(
                Arguments.of(null, AccountType.CUSTOMER_LIABILITY, zero, zero, "accountId"),
                Arguments.of("account-1", null, zero, zero, "accountType"),
                Arguments.of("account-1", AccountType.CUSTOMER_LIABILITY, null, zero, "openingBalance"),
                Arguments.of("account-1", AccountType.CUSTOMER_LIABILITY, zero, null, "bookedBalance")
        );
    }

    private static Money money(String amount, String currency) {
        return Money.of(new BigDecimal(amount), Currency.getInstance(currency));
    }
}

package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.domain.AccountType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.fail;

// Implement every TODO, then remove @Disabled and verify this class as a batch.
// Each unfinished test fails explicitly if enabled before its assertions are written.
@Disabled("Learning scaffold: mapper assertions are not implemented yet")
class AccountEntityMapperTest {

    @Test
    void normalizesZeroBalancesToTwoDecimalPlaces() {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", BigDecimal.ZERO, BigDecimal.ZERO);

        var account = AccountEntityMapper.toDomain(entity);

        // TODO Assert both amounts are numeric zero, both scales are 2, and both currencies are EUR.
        fail("Implement zero balance normalization assertions");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void preservesEveryCustomerAccountField(String currency) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                currency, new BigDecimal("123.45"), new BigDecimal("67.89"));

        var account = AccountEntityMapper.toDomain(entity);

        // TODO Assert account ID, customer ID, type, both distinct amounts, and both currencies.
        fail("Implement customer field preservation assertions");
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

        // TODO Assert identity, null owner, type, both negative amounts, and both currencies.
        fail("Implement internal account preservation assertions");
    }

    @Test
    void rejectsNullEntity() {
        // TODO Wrap toDomain(null) in assertThatThrownBy; expect NullPointerException naming entity.
        fail("Implement null entity rejection assertion");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "JPY", BigDecimal.ZERO, BigDecimal.ZERO);

        // TODO Assert mapping throws IllegalArgumentException through Money's supported-currency rule.
        fail("Implement unsupported currency rejection assertion");
    }

    @ParameterizedTest
    @CsvSource({"1.001, 0.00", "0.00, 1.001"})
    void rejectsBalancesThatWouldRequireRounding(String opening, String booked) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", new BigDecimal(opening), new BigDecimal(booked));

        // TODO Assert mapping throws ArithmeticException instead of silently rounding either balance.
        fail("Implement excess precision rejection assertion");
    }

    @ParameterizedTest
    @CsvSource({"-0.01, 0.00", "0.00, -0.01"})
    void rejectsNegativeCustomerBalance(String opening, String booked) {
        var entity = entity("customer-1", AccountType.CUSTOMER_LIABILITY,
                "EUR", new BigDecimal(opening), new BigDecimal(booked));

        // TODO Assert mapping rejects the invalid customer state through Account's constructor.
        fail("Implement negative customer balance rejection assertion");
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void rejectsOwnershipThatDoesNotMatchAccountType(AccountType type) {
        var owner = type == AccountType.CUSTOMER_LIABILITY ? null : "customer-1";
        var entity = entity(owner, type, "EUR", BigDecimal.ZERO, BigDecimal.ZERO);

        // TODO Assert mapping throws IllegalArgumentException for the invalid ownership shape.
        fail("Implement invalid ownership rejection assertion");
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

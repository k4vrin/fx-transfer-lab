package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.domain.AccountType;
import dev.kavrin.fxtransfer.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
// Roll back fixture rows after each invocation, including each CSV case.
@Transactional
class AccountEntityIT {

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void loadsEveryCustomerAccountField() {
        var fixture = insertAccount(createCustomer(), AccountType.CUSTOMER_LIABILITY,
                "EUR", "123.45", "67.89");
        var account = loadAccount(fixture.accountId());

        // TODO: Assert non-null, both IDs, type, currency, and both exact balance values.
        // Use fixture accessors for expected values, not another database query.
        fail("Complete the customer account mapping assertions");
    }

    @ParameterizedTest
    @CsvSource({
            "FX_CLEARING_ASSET, EUR",
            "FX_CLEARING_ASSET, USD",
            "FEE_REVENUE, EUR",
            "FEE_REVENUE, USD"
    })
    void loadsInternalAccountWithNullOwnerAndSignedBalances(AccountType type, String currency) {
        var fixture = insertAccount(null, type, currency, "-10.25", "-20.50");
        var account = loadAccount(fixture.accountId());

        // TODO: Assert non-null, account ID, null customer ID, enum type, currency, and balances.
        fail("Complete the internal account mapping assertions");
    }

    @Test
    void preservesTwoDecimalPlacesForZeroBalances() {
        var fixture = insertAccount(createCustomer(), AccountType.CUSTOMER_LIABILITY,
                "USD", "0.00", "0.00");
        var account = loadAccount(fixture.accountId());

        // TODO: Check both balances equal zero numerically AND both have scale 2.
        // isEqualByComparingTo checks value; a separate scale() assertion checks representation.
        fail("Complete the zero value and scale assertions");
    }

    @Test
    void preservesBalancesAtDatabasePrecisionLimit() {
        var fixture = insertAccount(createCustomer(), AccountType.CUSTOMER_LIABILITY,
                "EUR", "99999999999999999.99", "99999999999999999.98");
        var account = loadAccount(fixture.accountId());

        // TODO: Check both balances against the fixture and assert scale 2.
        // NUMBER(19,2) permits 17 integer digits; these values must not pass through double.
        fail("Complete the large exact balance assertions");
    }

    @Test
    void returnsNullForMissingAccount() {
        var account = loadAccount(UUID.randomUUID().toString());

        // TODO: Assert that EntityManager.find returns null for an absent row.
        fail("Complete the missing account assertion");
    }

    private String createCustomer() {
        var customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        return customerId;
    }

    private AccountFixture insertAccount(
            String customerId,
            AccountType type,
            String currency,
            String opening,
            String booked
    ) {
        var fixture = new AccountFixture(UUID.randomUUID().toString(), customerId, type,
                currency, new BigDecimal(opening), new BigDecimal(booked));
        // Insert independently of JPA so incorrect entity mappings cannot create their own fixtures.
        jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type,
                    currency, opening_balance, booked_balance
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, fixture.accountId(), fixture.customerId(), fixture.accountType().name(),
                fixture.currency(), fixture.openingBalance(), fixture.bookedBalance());
        return fixture;
    }

    private AccountEntity loadAccount(String accountId) {
        // Force a database read instead of accepting a previously managed entity.
        entityManager.clear();
        return entityManager.find(AccountEntity.class, accountId);
    }

    private record AccountFixture(
            String accountId,
            String customerId,
            AccountType accountType,
            String currency,
            BigDecimal openingBalance,
            BigDecimal bookedBalance
    ) {}
}

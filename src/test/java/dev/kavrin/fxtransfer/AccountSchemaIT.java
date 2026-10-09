package dev.kavrin.fxtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
// Spring rolls back each test invocation, including each parameterized case.
@Transactional
class AccountSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void acceptsTwoCurrenciesForOneCustomer() {
        String customerId = createCustomer();
        String eurAccountId = insertAccount(customerId, "CUSTOMER_LIABILITY", "EUR", "100.00", "70.00");
        String usdAccountId = insertAccount(customerId, "CUSTOMER_LIABILITY", "USD", "50.00", "80.00");

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE customer_id = ?", Integer.class, customerId))
                .isEqualTo(2);
        assertBalances(eurAccountId, "100.00", "70.00");
        assertBalances(usdAccountId, "50.00", "80.00");
    }

    @Test
    void acceptsMultipleAccountsInSameCurrencyForOneCustomer() {
        String customerId = createCustomer();
        insertAccount(customerId, "CUSTOMER_LIABILITY", "EUR", "100.00", "100.00");
        insertAccount(customerId, "CUSTOMER_LIABILITY", "EUR", "20.00", "20.00");

        // The current contract deliberately has no unique(customer_id, currency).
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM account
                WHERE customer_id = ? AND currency = 'EUR'
                """, Integer.class, customerId)).isEqualTo(2);
    }

    @Test
    void acceptsZeroCustomerBalances() {
        String accountId = insertAccount(createCustomer(), "CUSTOMER_LIABILITY", "EUR", "0.00", "0.00");

        assertBalances(accountId, "0.00", "0.00");
    }

    // Each CSV row is a separate run: first value -> type, second value -> currency.
    // Cover both internal account types in both supported currencies with the same assertions.
    @ParameterizedTest
    @CsvSource({
            "FX_CLEARING_ASSET, EUR",
            "FX_CLEARING_ASSET, USD",
            "FEE_REVENUE, EUR",
            "FEE_REVENUE, USD"
    })
    void acceptsInternalAccountsWithoutCustomerAndWithSignedBalances(String type, String currency) {
        // Nonnegative balance checks apply only to customer accounts, so these negatives are intentional.
        String accountId = insertAccount(null, type, currency, "-10.00", "-20.00");

        assertThat(jdbc.queryForObject(
                "SELECT customer_id FROM account WHERE account_id = ?", String.class, accountId))
                .isNull();
        assertBalances(accountId, "-10.00", "-20.00");
    }

    @Test
    void rejectsFeeRevenueAccountWithCustomerOwner() {
        String customerId = createCustomer();

        assertRejected(
                () -> insertAccount(customerId, "FEE_REVENUE", "EUR", "0.00", "0.00"),
                "CK_ACCOUNT_OWNER");
    }

    @Test
    void rejectsDuplicateCustomerId() {
        String customerId = createCustomer();

        assertRejected(
                () -> jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId),
                "PK_CUSTOMER");
    }

    @Test
    void rejectsDuplicateAccountId() {
        String accountId = insertAccount(null, "FX_CLEARING_ASSET", "EUR", "0.00", "0.00");

        assertRejected(() -> jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type,
                    currency, opening_balance, booked_balance
                ) VALUES (?, NULL, 'FX_CLEARING_ASSET', 'USD', 0.00, 0.00)
                """, accountId), "PK_ACCOUNT");
    }

    @Test
    void rejectsNullCustomerId() {
        assertRejected(
                () -> jdbc.update("INSERT INTO customer (customer_id) VALUES (NULL)"),
                "ORA-01400");
    }

    // Each string becomes the column argument in a separate run.
    // Change one required field to NULL at a time so its NOT NULL rule is tested in isolation.
    @ParameterizedTest
    @ValueSource(strings = {
            "account_id", "account_type", "currency", "opening_balance", "booked_balance"
    })
    void rejectsNullRequiredAccountFields(String column) {
        String accountId = insertAccount(null, "FX_CLEARING_ASSET", "EUR", "0.00", "0.00");

        // Column names come only from the fixed test cases, never from request input.
        assertRejected(() -> jdbc.update(
                "UPDATE account SET " + column + " = NULL WHERE account_id = ?", accountId),
                "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "eur", "EU"})
    void rejectsUnsupportedOrNonCanonicalCurrency(String currency) {
        assertRejected(
                () -> insertAccount(null, "FX_CLEARING_ASSET", currency, "0.00", "0.00"),
                "CK_ACCOUNT_CURRENCY");
    }

    @Test
    void rejectsUnknownAccountType() {
        // An unknown type violates both the type and owner checks; either can be reported first.
        assertRejected(
                () -> insertAccount(null, "UNKNOWN", "EUR", "0.00", "0.00"),
                "ORA-02290");
    }

    @Test
    void rejectsDeletingCustomerReferencedByAccount() {
        String customerId = createCustomer();
        insertAccount(customerId, "CUSTOMER_LIABILITY", "EUR", "0.00", "0.00");

        assertRejected(
                () -> jdbc.update("DELETE FROM customer WHERE customer_id = ?", customerId),
                "FK_ACCOUNT_CUSTOMER");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE customer_id = ?", Integer.class, customerId))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"opening_balance", "booked_balance"})
    void rejectsNegativeCustomerBalanceOnUpdate(String column) {
        String accountId = insertAccount(createCustomer(), "CUSTOMER_LIABILITY", "EUR", "100.00", "100.00");

        assertRejected(() -> jdbc.update(
                "UPDATE account SET " + column + " = -0.01 WHERE account_id = ?", accountId),
                "CK_CUSTOMER_BALANCE");
        assertBalances(accountId, "100.00", "100.00");
    }

    @Test
    void preservesExactCentAmounts() {
        String accountId = insertAccount(createCustomer(), "CUSTOMER_LIABILITY", "EUR", "123.45", "67.89");

        assertBalances(accountId, "123.45", "67.89");
    }

    @Test
    void rejectsBalanceOutsideDatabasePrecision() {
        // NUMBER(19,2) allows at most 17 integer digits.
        assertRejected(() -> insertAccount(
                null, "FX_CLEARING_ASSET", "EUR", "100000000000000000.00", "0.00"),
                "ORA-01438");
    }

    private String createCustomer() {
        String customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        return customerId;
    }

    private String insertAccount(String customerId, String type, String currency,
                                 String opening, String booked) {
        String accountId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type,
                    currency, opening_balance, booked_balance
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, customerId, type, currency,
                new BigDecimal(opening), new BigDecimal(booked));
        return accountId;
    }

    private void assertBalances(String accountId, String opening, String booked) {
        assertThat(jdbc.queryForObject(
                "SELECT opening_balance FROM account WHERE account_id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo(new BigDecimal(opening));
        assertThat(jdbc.queryForObject(
                "SELECT booked_balance FROM account WHERE account_id = ?", BigDecimal.class, accountId))
                .isEqualByComparingTo(new BigDecimal(booked));
    }

    private void assertRejected(Runnable action, String expectedDetail) {
        // Oracle ORA-01407 is not translated to DataIntegrityViolationException here.
        DataAccessException failure = catchThrowableOfType(
                DataAccessException.class, action::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage()).contains(expectedDetail);
    }
}

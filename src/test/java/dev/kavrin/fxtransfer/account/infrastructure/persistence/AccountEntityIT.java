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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(account).isNotNull();
        assertThat(account.getAccountId()).isEqualTo(fixture.accountId());
        assertThat(account.getCustomerId()).isEqualTo(fixture.customerId());
        assertThat(account.getAccountType()).isEqualTo(fixture.accountType());
        assertThat(account.getCurrency()).isEqualTo(fixture.currency());
        assertThat(account.getOpeningBalance()).isEqualByComparingTo(fixture.openingBalance());
        assertThat(account.getBookedBalance()).isEqualByComparingTo(fixture.bookedBalance());
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

        assertThat(account).isNotNull();
        assertThat(account.getAccountId()).isEqualTo(fixture.accountId());
        assertThat(account.getCustomerId()).isNull();
        assertThat(account.getAccountType()).isEqualTo(fixture.accountType());
        assertThat(account.getCurrency()).isEqualTo(fixture.currency());
        assertThat(account.getOpeningBalance()).isEqualByComparingTo(fixture.openingBalance());
        assertThat(account.getBookedBalance()).isEqualByComparingTo(fixture.bookedBalance());
    }

    @Test
    void loadsZeroBalancesWithoutRequiringDatabaseScale() {
        var fixture = insertAccount(createCustomer(), AccountType.CUSTOMER_LIABILITY,
                "USD", "0.00", "0.00");
        var account = loadAccount(fixture.accountId());

        assertThat(account).isNotNull();
        // Entity loading preserves numeric value; mapping to Money will normalize decimal scale.
        assertThat(account.getOpeningBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getBookedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void preservesBalancesAtDatabasePrecisionLimit() {
        var fixture = insertAccount(createCustomer(), AccountType.CUSTOMER_LIABILITY,
                "EUR", "99999999999999999.99", "99999999999999999.98");
        var account = loadAccount(fixture.accountId());

        // NUMBER(19,2) permits 17 integer digits; these values must not pass through double.
        assertThat(account).isNotNull();
        assertThat(account.getOpeningBalance()).isEqualByComparingTo(fixture.openingBalance());
        assertThat(account.getBookedBalance()).isEqualByComparingTo(fixture.bookedBalance());
    }

    @Test
    void returnsNullForMissingAccount() {
        var account = loadAccount(UUID.randomUUID().toString());

        assertThat(account).isNull();
    }

    @Test
    void persistsAndReloadsCustomerAccount() {
        var accountId = UUID.randomUUID().toString();
        var customerId = createCustomer();

        var account = new AccountEntity(
                accountId,
                customerId,
                AccountType.CUSTOMER_LIABILITY,
                "EUR",
                new BigDecimal("123.45"),
                new BigDecimal("67.89")
        );

        entityManager.persist(account);
        entityManager.flush();
        entityManager.clear();

        var reloaded = entityManager.find(
                AccountEntity.class,
                accountId
        );

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getAccountId()).isEqualTo(account.getAccountId());
        assertThat(reloaded.getCustomerId())
                .isEqualTo(customerId);
        assertThat(reloaded.getAccountType()).isEqualTo(account.getAccountType());
        assertThat(reloaded.getCurrency()).isEqualTo(account.getCurrency());
        assertThat(reloaded.getOpeningBalance()).isEqualByComparingTo(account.getOpeningBalance());
        assertThat(reloaded.getBookedBalance()).isEqualByComparingTo(account.getBookedBalance());
    }

    @Test
    void persistsAndReloadsInternalAccount() {
        var accountId = UUID.randomUUID().toString();

        var account = new AccountEntity(
                accountId,
                null,
                AccountType.FX_CLEARING_ASSET,
                "USD",
                new BigDecimal("-10.25"),
                new BigDecimal("-20.50")
        );

        entityManager.persist(account);
        entityManager.flush();
        entityManager.clear();

        var reloaded = entityManager.find(
                AccountEntity.class,
                accountId
        );

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getAccountId()).isEqualTo(account.getAccountId());
        assertThat(reloaded.getCustomerId()).isNull();
        assertThat(reloaded.getAccountType()).isEqualTo(account.getAccountType());
        assertThat(reloaded.getCurrency()).isEqualTo(account.getCurrency());
        assertThat(reloaded.getOpeningBalance()).isEqualByComparingTo(account.getOpeningBalance());
        assertThat(reloaded.getBookedBalance()).isEqualByComparingTo(account.getBookedBalance());
    }

    @ParameterizedTest
    @CsvSource({
            "0.00, -0.01",
            "-0.01, 0.00"
    })
    void rejectsNegativeCustomerBalance(String opening, String booked) {
        var account = new AccountEntity(
                UUID.randomUUID().toString(),
                createCustomer(),
                AccountType.CUSTOMER_LIABILITY,
                "EUR",
                new BigDecimal(opening),
                new BigDecimal(booked)
        );

        assertThatThrownBy(() -> {
            entityManager.persist(account);
            entityManager.flush();
        }).hasStackTraceContaining("CK_CUSTOMER_BALANCE");
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

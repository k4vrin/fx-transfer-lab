package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.application.AccountRepository;
import dev.kavrin.fxtransfer.account.domain.AccountType;
import dev.kavrin.fxtransfer.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
// JDBC fixtures and repository reads share the transaction; every case rolls back its rows.
@Transactional
class AccountPersistenceAdapterIT {

    @Autowired
    private AccountRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void findsCustomerAccountThroughApplicationPort(String currency) {
        var customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        var accountId = insertAccount(customerId, AccountType.CUSTOMER_LIABILITY,
                currency, "123.45", "0.00");

        assertThat(repository.findById(accountId)).hasValueSatisfying(account -> {
            assertThat(account.accountId()).isEqualTo(accountId);
            assertThat(account.customerId()).isEqualTo(customerId);
            assertThat(account.accountType()).isEqualTo(AccountType.CUSTOMER_LIABILITY);
            assertThat(account.openingBalance().amount()).isEqualTo(new BigDecimal("123.45"));
            assertThat(account.bookedBalance().amount()).isEqualTo(new BigDecimal("0.00"));
            assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance(currency));
            assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance(currency));
        });
    }

    @ParameterizedTest
    @CsvSource({
            "FX_CLEARING_ASSET, EUR",
            "FX_CLEARING_ASSET, USD",
            "FEE_REVENUE, EUR",
            "FEE_REVENUE, USD"
    })
    void findsInternalAccountThroughApplicationPort(AccountType type, String currency) {
        var accountId = insertAccount(null, type, currency, "-10.25", "-20.50");

        assertThat(repository.findById(accountId)).hasValueSatisfying(account -> {
            assertThat(account.accountId()).isEqualTo(accountId);
            assertThat(account.customerId()).isNull();
            assertThat(account.accountType()).isEqualTo(type);
            assertThat(account.openingBalance().amount()).isEqualTo(new BigDecimal("-10.25"));
            assertThat(account.bookedBalance().amount()).isEqualTo(new BigDecimal("-20.50"));
            assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance(currency));
            assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance(currency));
        });
    }

    @Test
    void returnsEmptyForMissingAccountThroughApplicationPort() {
        assertThat(repository.findById(UUID.randomUUID().toString())).isEmpty();
    }

    private String insertAccount(String customerId, AccountType type, String currency,
                                 String opening, String booked) {
        var accountId = UUID.randomUUID().toString();
        // Independent SQL fixtures verify the Spring Data lookup and mapper together.
        jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type,
                    currency, opening_balance, booked_balance
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, accountId, customerId, type.name(), currency,
                new BigDecimal(opening), new BigDecimal(booked));
        return accountId;
    }
}

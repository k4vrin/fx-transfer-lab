package dev.kavrin.fxtransfer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.password=test-only-service-connection")
@ActiveProfiles("local")
@Transactional
class LocalSeedSchemaIT {

    private static final String CUSTOMER_ID = "10000000-0000-0000-0000-000000000001";
    private static final String CUSTOMER_EUR = "20000000-0000-0000-0000-000000000001";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void localProfileSeedsSyntheticCustomerAndFiveAccountsOnContainerDatabase() {
        // ServiceConnection overrides the local datasource; this must not use the persistent DB.
        String url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        assertThat(url).doesNotContain("localhost:1521/");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer WHERE customer_id = ?",
                Integer.class, CUSTOMER_ID)).isEqualTo(1);
        assertThat(jdbc.queryForList("""
                SELECT account_type || ':' || currency AS shape FROM account
                WHERE account_id IN (
                    '20000000-0000-0000-0000-000000000001',
                    '20000000-0000-0000-0000-000000000002',
                    '20000000-0000-0000-0000-000000000003',
                    '20000000-0000-0000-0000-000000000004',
                    '20000000-0000-0000-0000-000000000005')
                """, String.class)).containsExactlyInAnyOrder(
                "CUSTOMER_LIABILITY:EUR", "CUSTOMER_LIABILITY:USD",
                "FX_CLEARING_ASSET:EUR", "FX_CLEARING_ASSET:USD", "FEE_REVENUE:EUR");
        assertBalance("20000000-0000-0000-0000-000000000001", "1000.00", "1000.00");
        assertBalance("20000000-0000-0000-0000-000000000002", "0.00", "0.00");
        assertBalance("20000000-0000-0000-0000-000000000003", "0.00", "0.00");
        assertBalance("20000000-0000-0000-0000-000000000004", "10000.00", "10000.00");
        assertBalance("20000000-0000-0000-0000-000000000005", "0.00", "0.00");
    }

    @Test
    void startsWithoutQuotesOrBookedOperations() {
        for (String table : new String[]{"quote", "transfer", "journal", "journal_entry", "idempotency_record"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                    .as("initial rows in %s", table).isZero();
        }
    }

    @Test
    void rerunningSeedDoesNotDuplicateRowsOrResetExistingBalances() {
        // A direct change simulates an existing projection; this is not a booking/reconciliation test.
        jdbc.update("UPDATE account SET booked_balance = 898.00 WHERE account_id = ?", CUSTOMER_EUR);
        for (int attempt = 0; attempt < 2; attempt++) {
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/local/R__seed_initial_accounts.sql"));
                return null;
            });
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Integer.class)).isEqualTo(5);
        assertBalance(CUSTOMER_EUR, "1000.00", "898.00");
    }

    private void assertBalance(String accountId, String opening, String booked) {
        assertThat(jdbc.queryForObject("SELECT opening_balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId)).isEqualByComparingTo(opening);
        assertThat(jdbc.queryForObject("SELECT booked_balance FROM account WHERE account_id = ?",
                BigDecimal.class, accountId)).isEqualByComparingTo(booked);
    }
}

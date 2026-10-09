package dev.kavrin.fxtransfer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FxTransferLabApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoads() {
    }

    @Test
    void rejectsCustomerAccountWithMissingCustomer() {
        String accountId = UUID.randomUUID().toString();
        String missingCustomerId = UUID.randomUUID().toString();

        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                        INSERT INTO account (
                            account_id, customer_id, account_type,
                            currency, opening_balance, booked_balance
                        ) VALUES (?, ?, 'CUSTOMER_LIABILITY', 'EUR', 100.00, 100.00)
                        """, accountId, missingCustomerId));

        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage())
                .contains("FK_ACCOUNT_CUSTOMER");
    }

    @Test
    void rejectsCustomerAccountWithoutOwner() {
        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                    INSERT INTO account (
                        account_id, customer_id, account_type,
                        currency, opening_balance, booked_balance
                    ) VALUES (?, NULL, 'CUSTOMER_LIABILITY', 'EUR', 100.00, 100.00)
                    """, UUID.randomUUID().toString()));

        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage())
                .contains("CK_ACCOUNT_OWNER");
    }

    @Test
    @Transactional
    void rejectsInternalAccountWithCustomerOwner() {
        String customerId = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO customer (customer_id) VALUES (?)",
                customerId);

        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                    INSERT INTO account (
                        account_id, customer_id, account_type,
                        currency, opening_balance, booked_balance
                    ) VALUES (?, ?, 'FX_CLEARING_ASSET', 'EUR', 100.00, 100.00)
                    """, UUID.randomUUID().toString(), customerId));

        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage())
                .contains("CK_ACCOUNT_OWNER");
    }

    @Test
    @Transactional
    void rejectsCustomerAccountWithNegativeOpeningBalance() {
        String customerId = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO customer (customer_id) VALUES (?)",
                customerId);

        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                    INSERT INTO account (
                        account_id, customer_id, account_type,
                        currency, opening_balance, booked_balance
                    ) VALUES (?, ?, 'CUSTOMER_LIABILITY', 'EUR', -0.01, 0.00)
                    """, UUID.randomUUID().toString(), customerId));

        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage())
                .contains("CK_CUSTOMER_BALANCE");
    }

    @Test
    @Transactional
    void rejectsCustomerAccountWithNegativeBookedBalance() {
        String customerId = UUID.randomUUID().toString();

        jdbc.update(
                "INSERT INTO customer (customer_id) VALUES (?)",
                customerId);

        DataIntegrityViolationException failure = catchThrowableOfType(
                DataIntegrityViolationException.class,
                () -> jdbc.update("""
                    INSERT INTO account (
                        account_id, customer_id, account_type,
                        currency, opening_balance, booked_balance
                    ) VALUES (?, ?, 'CUSTOMER_LIABILITY', 'EUR', 100.00, -0.01)
                    """, UUID.randomUUID().toString(), customerId));

        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage())
                .contains("CK_CUSTOMER_BALANCE");
    }

}

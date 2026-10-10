package dev.kavrin.fxtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Savepoint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JournalSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void defaultProfileDoesNotLoadLocalSeedData() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM customer WHERE customer_id = ?",
                Integer.class, "10000000-0000-0000-0000-000000000001")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM "flyway_schema_history"
                WHERE "script" = 'R__seed_initial_accounts.sql'
                """, Integer.class)).isZero();
    }

    @Test
    void storesJournalLinkedToTransferWithNanosecondCreationTime() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        String journalId = insertJournal(fixture.transferId());

        assertThat(jdbc.queryForObject(
                "SELECT transfer_id FROM journal WHERE journal_id = ?",
                String.class, journalId)).isEqualTo(fixture.transferId());
        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT created_at FROM journal WHERE journal_id = ?",
                (rs, rowNumber) -> rs.getObject(1, OffsetDateTime.class), journalId);
        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(Instant.parse("2026-10-10T12:01:00.123456789Z"));
    }

    @Test
    void rejectsDuplicateJournalIdForDifferentTransfers() {
        Fixture first = createFixture();
        Fixture second = createFixture();
        insertTransfer(first);
        insertTransfer(second);
        String journalId = insertJournal(first.transferId());

        // Use a different transfer to isolate the primary key from transfer uniqueness.
        assertRejected(() -> insertJournal(journalId, second.transferId()), "PK_JOURNAL");
        assertThat(journalCount(first.transferId())).isEqualTo(1);
        assertThat(journalCount(second.transferId())).isZero();
    }

    @Test
    void rejectsSecondJournalForSameTransfer() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        insertJournal(fixture.transferId());

        assertRejected(() -> insertJournal(fixture.transferId()), "UQ_JOURNAL_TRANSFER");
        assertThat(journalCount(fixture.transferId())).isEqualTo(1);
    }

    @Test
    void rejectsJournalForMissingTransfer() {
        assertRejected(() -> insertJournal(UUID.randomUUID().toString()), "FK_JOURNAL_TRANSFER");
    }

    @Test
    void rejectsDeletingTransferReferencedByJournal() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        insertJournal(fixture.transferId());

        assertRejected(() -> jdbc.update("DELETE FROM transfer WHERE transfer_id = ?",
                fixture.transferId()), "FK_JOURNAL_TRANSFER");
        assertThat(transferCount(fixture.transferId())).isEqualTo(1);
        assertThat(journalCount(fixture.transferId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal_id", "transfer_id", "created_at"})
    void rejectsNullRequiredFields(String column) {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        String journalId = insertJournal(fixture.transferId());

        // Direct updates probe constraints; they are not an API for editing accounting history.
        assertRejected(() -> jdbc.update("UPDATE journal SET " + column + " = NULL WHERE journal_id = ?",
                journalId), "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal_id", "transfer_id"})
    void rejectsEmptyIdentifiers(String column) {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        String journalId = insertJournal(fixture.transferId());

        // Oracle treats an empty VARCHAR2 string as NULL.
        assertRejected(() -> jdbc.update("UPDATE journal SET " + column + " = ? WHERE journal_id = ?",
                "", journalId), "ORA-01407");
    }

    @Test
    void rollbackOfJournalInsertAllowsSameTransferToBeLinkedAgain() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint beforeJournal = connection.setSavepoint();
            insertJournal(fixture.transferId());
            assertThat(journalCount(fixture.transferId())).isEqualTo(1);
            connection.rollback(beforeJournal);
            return null;
        });

        assertThat(transferCount(fixture.transferId())).isEqualTo(1);
        assertThat(journalCount(fixture.transferId())).isZero();
        insertJournal(fixture.transferId());
        assertThat(journalCount(fixture.transferId())).isEqualTo(1);
    }

    @Test
    void rollbackRemovesTransferAndJournalTogether() {
        Fixture fixture = createFixture();

        // Existing customer/accounts/quote survive. This probes Oracle transaction behavior,
        // not the future booking service or atomicity of journal entries and balances.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint beforeBooking = connection.setSavepoint();
            insertTransfer(fixture);
            insertJournal(fixture.transferId());
            assertThat(transferCount(fixture.transferId())).isEqualTo(1);
            assertThat(journalCount(fixture.transferId())).isEqualTo(1);
            connection.rollback(beforeBooking);
            return null;
        });

        assertThat(transferCount(fixture.transferId())).isZero();
        assertThat(journalCount(fixture.transferId())).isZero();
        insertTransfer(fixture);
        insertJournal(fixture.transferId());
        assertThat(journalCount(fixture.transferId())).isEqualTo(1);
    }

    private Fixture createFixture() {
        String customerId = UUID.randomUUID().toString();
        String quoteId = UUID.randomUUID().toString();
        String sourceId = UUID.randomUUID().toString();
        String destinationId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        jdbc.update("""
                INSERT INTO account (account_id, customer_id, account_type, currency,
                    opening_balance, booked_balance)
                VALUES (?, ?, 'CUSTOMER_LIABILITY', 'EUR', 1000.00, 1000.00)
                """, sourceId, customerId);
        jdbc.update("""
                INSERT INTO account (account_id, customer_id, account_type, currency,
                    opening_balance, booked_balance)
                VALUES (?, ?, 'CUSTOMER_LIABILITY', 'USD', 1000.00, 1000.00)
                """, destinationId, customerId);
        jdbc.update("""
                INSERT INTO quote (quote_id, customer_id, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate,
                    rate_provider, rate_version, rate_as_of, created_at, expires_at)
                VALUES (?, ?, 'EUR', 'USD', 100.00, 110.00, 2.00, 1.10000000,
                    'fixture-provider', 'rate-v1',
                    TIMESTAMP '2026-10-10 11:59:00 +00:00',
                    TIMESTAMP '2026-10-10 12:00:00 +00:00',
                    TIMESTAMP '2026-10-10 12:05:00 +00:00')
                """, quoteId, customerId);
        return new Fixture(UUID.randomUUID().toString(), customerId, quoteId, sourceId, destinationId);
    }

    private void insertTransfer(Fixture fixture) {
        assertThat(jdbc.update("""
                INSERT INTO transfer (transfer_id, customer_id, quote_id,
                    source_account_id, destination_account_id, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate, rate_provider, rate_version,
                    rate_as_of, status, booked_at)
                SELECT ?, ?, quote_id, ?, ?, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate, rate_provider, rate_version,
                    rate_as_of, 'BOOKED', TIMESTAMP '2026-10-10 12:01:00 +00:00'
                FROM quote WHERE quote_id = ?
                """, fixture.transferId(), fixture.customerId(), fixture.sourceId(),
                fixture.destinationId(), fixture.quoteId())).isEqualTo(1);
    }

    private String insertJournal(String transferId) {
        String journalId = UUID.randomUUID().toString();
        insertJournal(journalId, transferId);
        return journalId;
    }

    private void insertJournal(String journalId, String transferId) {
        assertThat(jdbc.update("""
                INSERT INTO journal (journal_id, transfer_id, created_at)
                VALUES (?, ?, TIMESTAMP '2026-10-10 15:31:00.123456789 +03:30')
                """, journalId, transferId)).isEqualTo(1);
    }

    private int journalCount(String transferId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal WHERE transfer_id = ?",
                Integer.class, transferId);
    }

    private int transferCount(String transferId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE transfer_id = ?",
                Integer.class, transferId);
    }

    private void assertRejected(Runnable action, String expectedDetail) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, action::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage()).contains(expectedDetail);
    }

    private record Fixture(String transferId, String customerId, String quoteId,
                           String sourceId, String destinationId) {
    }
}

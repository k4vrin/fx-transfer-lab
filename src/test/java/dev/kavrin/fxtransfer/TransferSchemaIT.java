package dev.kavrin.fxtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Savepoint;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class TransferSchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storesBookedTransferWithQuoteSnapshotAndSeparateBookingTime() {
        Fixture fixture = createFixture();
        String transferId = insertTransfer(fixture);

        assertThat(jdbc.queryForObject("SELECT status FROM transfer WHERE transfer_id = ?",
                String.class, transferId)).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("SELECT quote_id FROM transfer WHERE transfer_id = ?",
                String.class, transferId)).isEqualTo(fixture.quoteId());
        assertDecimal(transferId, "source_amount", "100.00");
        assertDecimal(transferId, "destination_amount", "112.35");
        assertDecimal(transferId, "fee_amount", "2.99");
        assertDecimal(transferId, "rate", "1.12345678");
        assertInstant(transferId, "rate_as_of", "2026-10-09T11:59:00.123456789Z");
        assertInstant(transferId, "booked_at", "2026-10-09T12:01:00.123456789Z");
    }

    @Test
    void acceptsRepeatedUseOfCustomerAndAccountsWithDifferentQuotes() {
        Fixture first = createFixture();
        insertTransfer(first);
        Fixture second = new Fixture(first.customerId(), createQuote(first.customerId()),
                first.sourceAccountId(), first.destinationAccountId());
        insertTransfer(second);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM transfer
                WHERE customer_id = ? AND source_account_id = ? AND destination_account_id = ?
                """, Integer.class, first.customerId(), first.sourceAccountId(), first.destinationAccountId()))
                .isEqualTo(2);
    }

    @Test
    void rejectsSecondTransferUsingSameQuote() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);

        assertRejected(() -> insertTransfer(fixture), "UQ_TRANSFER_QUOTE");
        assertThat(transferCount(fixture.quoteId())).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateTransferId() {
        Fixture first = createFixture();
        String transferId = insertTransfer(first);
        Fixture second = new Fixture(first.customerId(), createQuote(first.customerId()),
                first.sourceAccountId(), first.destinationAccountId());

        // A new quote isolates the primary-key failure from quote-use uniqueness.
        assertRejected(() -> insertTransfer(transferId, second), "PK_TRANSFER");
    }

    @Test
    void rollbackOfTransferInsertLeavesQuoteReusable() {
        Fixture fixture = createFixture();

        // Roll back only the attempted transfer, retaining the pre-existing quote and accounts.
        // This checks Oracle rollback of quote-use uniqueness, not application booking atomicity.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint beforeTransfer = connection.setSavepoint();
            insertTransfer(fixture);
            assertThat(transferCount(fixture.quoteId())).isEqualTo(1);
            connection.rollback(beforeTransfer);
            // Oracle JDBC does not support releaseSavepoint; the outer test rollback clears it.
            return null;
        });

        assertThat(transferCount(fixture.quoteId())).isZero();
        insertTransfer(fixture);
        assertThat(transferCount(fixture.quoteId())).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "customer_id, FK_TRANSFER_CUSTOMER",
            "quote_id, FK_TRANSFER_QUOTE",
            "source_account_id, FK_TRANSFER_SOURCE_ACCOUNT",
            "destination_account_id, FK_TRANSFER_DESTINATION_ACCOUNT"
    })
    void rejectsMissingReferencedRows(String column, String constraint) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, column, UUID.randomUUID().toString()), constraint);
    }

    @ParameterizedTest
    @CsvSource({
            "quote, quote_id, FK_TRANSFER_QUOTE",
            "account, source_account_id, FK_TRANSFER_SOURCE_ACCOUNT",
            "account, destination_account_id, FK_TRANSFER_DESTINATION_ACCOUNT"
    })
    void rejectsDeletingReferencedQuoteOrAccount(String table, String referenceColumn, String constraint) {
        String transferId = insertTransfer(createFixture());
        String referencedId = jdbc.queryForObject(
                "SELECT " + referenceColumn + " FROM transfer WHERE transfer_id = ?", String.class, transferId);
        String idColumn = table.equals("quote") ? "quote_id" : "account_id";

        assertRejected(() -> jdbc.update(
                "DELETE FROM " + table + " WHERE " + idColumn + " = ?", referencedId), constraint);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE transfer_id = ?",
                Integer.class, transferId)).isEqualTo(1);
    }

    @Test
    void rejectsIdenticalSourceAndDestinationAccounts() {
        Fixture fixture = createFixture();
        String transferId = insertTransfer(fixture);

        assertRejected(() -> update(transferId, "destination_account_id", fixture.sourceAccountId()),
                "CK_TRANSFER_DISTINCT_ACCOUNTS");
    }

    // Direct SQL updates isolate the schema rules; they are not an API for editing booked history.
    @ParameterizedTest
    @ValueSource(strings = {
            "transfer_id", "customer_id", "quote_id", "source_account_id", "destination_account_id",
            "source_currency", "destination_currency", "source_amount", "destination_amount",
            "fee_amount", "rate", "rate_provider", "rate_version", "rate_as_of", "status", "booked_at"
    })
    void rejectsNullRequiredFields(String column) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, column, null), "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED", "REVERSED", "booked"})
    void rejectsStatusesOutsideSynchronousFoundation(String status) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, "status", status), "CK_TRANSFER_STATUS");
    }

    @ParameterizedTest
    @CsvSource({
            "source_currency, GBP, CK_TRANSFER_SOURCE_CURRENCY",
            "source_currency, eur, CK_TRANSFER_SOURCE_CURRENCY",
            "source_currency, EU, CK_TRANSFER_SOURCE_CURRENCY",
            "destination_currency, GBP, CK_TRANSFER_DESTINATION_CURRENCY",
            "destination_currency, usd, CK_TRANSFER_DESTINATION_CURRENCY",
            "destination_currency, US, CK_TRANSFER_DESTINATION_CURRENCY"
    })
    void rejectsUnsupportedOrNonCanonicalCurrencies(String column, String value, String constraint) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, column, value), constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void rejectsSameCurrencyPair(String currency) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> jdbc.update("""
                UPDATE transfer SET source_currency = ?, destination_currency = ?
                WHERE transfer_id = ?
                """, currency, currency, transferId), "CK_TRANSFER_CURRENCY_PAIR");
    }

    @ParameterizedTest
    @CsvSource({
            "source_amount, 0.00, CK_TRANSFER_SOURCE_AMOUNT",
            "source_amount, -0.01, CK_TRANSFER_SOURCE_AMOUNT",
            "destination_amount, -0.01, CK_TRANSFER_DESTINATION_AMOUNT",
            "fee_amount, -0.01, CK_TRANSFER_FEE_AMOUNT",
            "rate, 0.00000000, CK_TRANSFER_RATE",
            "rate, -0.00000001, CK_TRANSFER_RATE"
    })
    void rejectsInvalidAmountsAndRates(String column, BigDecimal value, String constraint) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, column, value), constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"destination_amount", "fee_amount"})
    void acceptsZeroDestinationAmountOrFee(String column) {
        String transferId = insertTransfer(createFixture());

        assertThat(update(transferId, column, BigDecimal.ZERO)).isEqualTo(1);
        assertDecimal(transferId, column, "0.00");
    }

    @ParameterizedTest
    @CsvSource({
            "source_amount, 100000000000000000.00",
            "destination_amount, 100000000000000000.00",
            "fee_amount, 100000000000000000.00",
            "rate, 100000000000.00000000"
    })
    void rejectsNumbersBeyondStoragePrecision(String column, BigDecimal value) {
        String transferId = insertTransfer(createFixture());

        assertRejected(() -> update(transferId, column, value), "ORA-01438");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void rejectsWhitespaceOnlyProvenance(String column) {
        String transferId = insertTransfer(createFixture());
        String constraint = column.equals("rate_provider") ? "CK_TRANSFER_RATE_PROVIDER" : "CK_TRANSFER_RATE_VERSION";

        assertRejected(() -> update(transferId, column, " \t\n "), constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void rejectsEmptyProvenance(String column) {
        String transferId = insertTransfer(createFixture());

        // Oracle treats an empty VARCHAR2 string as NULL.
        assertRejected(() -> update(transferId, column, ""), "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void preservesProvenanceWithoutNormalization(String column) {
        String transferId = insertTransfer(createFixture());
        String value = " Fixture-V1 ";
        update(transferId, column, value);

        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM transfer WHERE transfer_id = ?", String.class, transferId))
                .isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void enforcesProvenanceLengthLimit(String column) {
        String transferId = insertTransfer(createFixture());
        String maximumLength = "x".repeat(100);
        update(transferId, column, maximumLength);

        assertRejected(() -> update(transferId, column, "x".repeat(101)), "ORA-12899");
        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM transfer WHERE transfer_id = ?", String.class, transferId))
                .isEqualTo(maximumLength);
    }

    private Fixture createFixture() {
        String customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        return new Fixture(customerId, createQuote(customerId),
                createAccount(customerId, "EUR"), createAccount(customerId, "USD"));
    }

    private String createAccount(String customerId, String currency) {
        String accountId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type, currency, opening_balance, booked_balance
                ) VALUES (?, ?, 'CUSTOMER_LIABILITY', ?, 1000.00, 1000.00)
                """, accountId, customerId, currency);
        return accountId;
    }

    private String createQuote(String customerId) {
        String quoteId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO quote (
                    quote_id, customer_id, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate,
                    rate_provider, rate_version, rate_as_of, created_at, expires_at
                ) VALUES (
                    ?, ?, 'EUR', 'USD', 100.00, 112.35, 2.99, 1.12345678,
                    'fixture-provider', 'rate-v1',
                    TIMESTAMP '2026-10-09 11:59:00.123456789 +00:00',
                    TIMESTAMP '2026-10-09 12:00:00.123456789 +00:00',
                    TIMESTAMP '2026-10-09 12:05:00.123456789 +00:00'
                )
                """, quoteId, customerId);
        return quoteId;
    }

    private String insertTransfer(Fixture fixture) {
        String transferId = UUID.randomUUID().toString();
        insertTransfer(transferId, fixture);
        return transferId;
    }

    private void insertTransfer(String transferId, Fixture fixture) {
        int inserted = jdbc.update("""
                INSERT INTO transfer (
                    transfer_id, customer_id, quote_id, source_account_id, destination_account_id,
                    source_currency, destination_currency, source_amount, destination_amount,
                    fee_amount, rate, rate_provider, rate_version, rate_as_of, status, booked_at
                ) SELECT ?, ?, quote_id, ?, ?, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate, rate_provider,
                    rate_version, rate_as_of, 'BOOKED',
                    TIMESTAMP '2026-10-09 15:31:00.123456789 +03:30'
                  FROM quote WHERE quote_id = ?
                """, transferId, fixture.customerId(), fixture.sourceAccountId(),
                fixture.destinationAccountId(), fixture.quoteId());
        assertThat(inserted).isEqualTo(1);
    }

    private int transferCount(String quoteId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE quote_id = ?", Integer.class, quoteId);
    }

    private int update(String transferId, String column, Object value) {
        // All interpolated identifiers come from fixed test cases, not user input.
        return jdbc.update("UPDATE transfer SET " + column + " = ? WHERE transfer_id = ?", value, transferId);
    }

    private void assertDecimal(String transferId, String column, String expected) {
        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM transfer WHERE transfer_id = ?", BigDecimal.class, transferId))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    private void assertInstant(String transferId, String column, String expected) {
        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT " + column + " FROM transfer WHERE transfer_id = ?",
                (rs, rowNumber) -> rs.getObject(1, OffsetDateTime.class), transferId);
        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(Instant.parse(expected));
    }

    private void assertRejected(Runnable action, String expectedDetail) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, action::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage()).contains(expectedDetail);
    }

    private record Fixture(String customerId, String quoteId,
                           String sourceAccountId, String destinationAccountId) {
    }
}

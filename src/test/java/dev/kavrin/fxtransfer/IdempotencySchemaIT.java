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
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Savepoint;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IdempotencySchemaIT {

    // A format fixture only; canonical request hashing belongs to later application tests.
    private static final String FINGERPRINT = "0123456789abcdef".repeat(4);

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void acceptsReservationWithoutResult() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");

        assertThat(text(id, "status")).isEqualTo("IN_PROGRESS");
        assertThat(text(id, "transfer_id")).isNull();
        assertThat(jdbc.queryForObject("""
                SELECT response_http_status FROM idempotency_record WHERE idempotency_id = ?
                """, Integer.class, id)).isNull();
        assertThat(text(id, "response_body")).isNull();
        assertThat(text(id, "request_fingerprint")).isEqualTo(FINGERPRINT);
        assertThat(text(id, "quote_id")).isEqualTo(fixture.quoteId());
        assertThat(text(id, "source_account_id")).isEqualTo(fixture.sourceAccountId());
        assertThat(text(id, "destination_account_id")).isEqualTo(fixture.destinationAccountId());
    }

    @Test
    void acceptsCompleteSuccessfulResultInOneUpdate() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        String response = "{\"transferId\":\"" + fixture.transferId() + "\",\"status\":\"BOOKED\"}";

        complete(id, fixture, response);

        assertThat(text(id, "status")).isEqualTo("SUCCEEDED");
        assertThat(text(id, "transfer_id")).isEqualTo(fixture.transferId());
        assertThat(jdbc.queryForObject("""
                SELECT response_http_status FROM idempotency_record WHERE idempotency_id = ?
                """, Integer.class, id)).isEqualTo(201);
        assertThat(text(id, "response_body")).isEqualTo(response);
    }

    @Test
    void permitsSameKeyForDifferentCustomers() {
        Fixture alice = createFixture();
        Fixture bob = createFixture();
        reserve(alice, "request-123");
        reserve(bob, "request-123");

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM idempotency_record
                WHERE customer_id IN (?, ?) AND idempotency_key = 'request-123'
                """, Integer.class, alice.customerId(), bob.customerId())).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "changed"})
    void rejectsDuplicateScopeRegardlessOfFingerprint(String request) {
        Fixture fixture = createFixture();
        reserve(fixture, "request-123");
        String fingerprint = request.equals("same") ? FINGERPRINT : "f".repeat(64);

        assertRejected(() -> reserve(UUID.randomUUID().toString(), fixture, "request-123", fingerprint),
                "UQ_IDEMPOTENCY_SCOPE");
        assertThat(recordCount(fixture.customerId(), "request-123")).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateRecordId() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "first-key");

        // A different key isolates primary-key rejection from scope uniqueness.
        assertRejected(() -> reserve(id, fixture, "second-key", FINGERPRINT), "PK_IDEMPOTENCY_RECORD");
    }

    @Test
    void rollbackRemovesReservationAndTransferAndAllowsSameKeyRetry() {
        Fixture fixture = createFixture();

        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint beforeBooking = connection.setSavepoint();
            String id = reserve(fixture, "retry-key");
            insertTransfer(fixture);
            complete(id, fixture, "{\"status\":\"BOOKED\"}");
            connection.rollback(beforeBooking);
            // Oracle does not support releaseSavepoint; the outer test rollback clears it.
            return null;
        });

        assertThat(recordCount(fixture.customerId(), "retry-key")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE quote_id = ?",
                Integer.class, fixture.quoteId())).isZero();
        String retryId = reserve(fixture, "retry-key");
        insertTransfer(fixture);
        complete(retryId, fixture, "{\"status\":\"BOOKED\"}");
        assertThat(recordCount(fixture.customerId(), "retry-key")).isEqualTo(1);
        assertThat(text(retryId, "status")).isEqualTo("SUCCEEDED");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "idempotency_id", "customer_id", "operation", "idempotency_key", "request_fingerprint",
            "quote_id", "source_account_id", "destination_account_id", "status", "created_at"
    })
    void rejectsNullRequiredFields(String column) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, column, null), "ORA-01407");
    }

    @Test
    void rejectsMissingCustomer() {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "customer_id", UUID.randomUUID().toString()), "FK_IDEMPOTENCY_CUSTOMER");
    }

    @Test
    void rejectsMissingTransferInSuccessfulResult() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        complete(id, fixture, "{}");

        assertRejected(() -> update(id, "transfer_id", UUID.randomUUID().toString()), "FK_IDEMPOTENCY_TRANSFER");
    }

    @Test
    void rejectsDeletingReferencedTransfer() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        complete(id, fixture, "{}");

        assertRejected(() -> jdbc.update("DELETE FROM transfer WHERE transfer_id = ?", fixture.transferId()),
                "FK_IDEMPOTENCY_TRANSFER");
        assertThat(text(id, "transfer_id")).isEqualTo(fixture.transferId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "FAILED", "succeeded"})
    void rejectsUnknownStates(String status) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "status", status), "CK_IDEMPOTENCY_RESULT");
    }

    @Test
    void rejectsSuccessWithoutResultFields() {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "status", "SUCCEEDED"), "CK_IDEMPOTENCY_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer_id", "response_http_status", "response_body"})
    void rejectsPartialResultsWhileInProgress(String column) {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        Object value = switch (column) {
            case "transfer_id" -> fixture.transferId();
            case "response_http_status" -> 201;
            case "response_body" -> "{}";
            default -> throw new IllegalArgumentException(column);
        };

        assertRejected(() -> update(id, column, value), "CK_IDEMPOTENCY_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transfer_id", "response_http_status", "response_body"})
    void rejectsMissingFieldInSuccessfulResult(String column) {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        complete(id, fixture, "{}");

        assertRejected(() -> update(id, column, null), "CK_IDEMPOTENCY_RESULT");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 202, 400, 500})
    void rejectsWrongSuccessfulHttpStatus(int httpStatus) {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        complete(id, fixture, "{}");

        assertRejected(() -> update(id, "response_http_status", httpStatus), "CK_IDEMPOTENCY_RESULT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "f", "0123456789abcdef"})
    void acceptsLowercaseHexFingerprint(String pattern) {
        String id = reserve(createFixture(), "request-123");
        String value = pattern.repeat(64 / pattern.length());

        assertThat(update(id, "request_fingerprint", value)).isEqualTo(1);
        assertThat(text(id, "request_fingerprint")).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 63})
    void rejectsShortFingerprint(int length) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "request_fingerprint", "a".repeat(length)), "CK_IDEMPOTENCY_FINGERPRINT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "g", " ", "\n"})
    void rejectsInvalidFingerprintCharacters(String character) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "request_fingerprint", "a".repeat(63) + character),
                "CK_IDEMPOTENCY_FINGERPRINT");
    }

    @Test
    void rejectsFingerprintBeyondStorageLength() {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "request_fingerprint", "a".repeat(65)), "ORA-12899");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVERSE_FX_TRANSFER", "create_fx_transfer"})
    void rejectsUnsupportedOperation(String operation) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "operation", operation), "CK_IDEMPOTENCY_OPERATION");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t", "\n"})
    void rejectsWhitespaceOnlyKey(String key) {
        String id = reserve(createFixture(), "request-123");

        assertRejected(() -> update(id, "idempotency_key", key), "CK_IDEMPOTENCY_KEY");
    }

    @Test
    void rejectsEmptyKey() {
        String id = reserve(createFixture(), "request-123");

        // Oracle treats an empty VARCHAR2 string as NULL.
        assertRejected(() -> update(id, "idempotency_key", ""), "ORA-01407");
    }

    @Test
    void preservesDistinctKeysWithoutFeePolicyNormalization() {
        Fixture fixture = createFixture();
        for (String key : new String[]{"Key", "key", " key "}) {
            String id = reserve(fixture, key);
            assertThat(text(id, "idempotency_key")).isEqualTo(key);
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE customer_id = ?",
                Integer.class, fixture.customerId())).isEqualTo(3);
    }

    @Test
    void enforcesKeyLengthLimit() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "k".repeat(100));

        assertRejected(() -> update(id, "idempotency_key", "k".repeat(101)), "ORA-12899");
        assertThat(text(id, "idempotency_key")).isEqualTo("k".repeat(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"id\":", "{id:1}", "{'id':1}", "not-json"})
    void rejectsMalformedOrNonStrictJson(String response) {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        complete(id, fixture, "{}");

        assertRejected(() -> update(id, "response_body", response), "CK_IDEMPOTENCY_RESPONSE_JSON");
    }

    @Test
    void preservesLargeJsonResponseSnapshotExactly() {
        Fixture fixture = createFixture();
        String id = reserve(fixture, "request-123");
        insertTransfer(fixture);
        String response = "{\"transferId\":\"" + fixture.transferId() + "\",\"detail\":\"" + "x".repeat(5000) + "\"}";

        complete(id, fixture, response);

        assertThat(text(id, "response_body")).isEqualTo(response);
    }

    @Test
    void preservesCreationInstantAndNanoseconds() {
        String id = reserve(createFixture(), "request-123");
        OffsetDateTime stored = jdbc.queryForObject("""
                SELECT created_at FROM idempotency_record WHERE idempotency_id = ?
                """, (rs, rowNumber) -> rs.getObject(1, OffsetDateTime.class), id);

        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(Instant.parse("2026-10-09T12:01:00.123456789Z"));
    }

    private Fixture createFixture() {
        Fixture fixture = new Fixture(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", fixture.customerId());
        createAccount(fixture.sourceAccountId(), fixture.customerId(), "EUR");
        createAccount(fixture.destinationAccountId(), fixture.customerId(), "USD");
        jdbc.update("""
                INSERT INTO quote (
                    quote_id, customer_id, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate,
                    rate_provider, rate_version, rate_as_of, created_at, expires_at
                ) VALUES (
                    ?, ?, 'EUR', 'USD', 100.00, 110.00, 2.00, 1.10000000,
                    'fixture-provider', 'rate-v1',
                    TIMESTAMP '2026-10-09 11:59:00.123456789 +00:00',
                    TIMESTAMP '2026-10-09 12:00:00.123456789 +00:00',
                    TIMESTAMP '2026-10-09 12:05:00.123456789 +00:00'
                )
                """, fixture.quoteId(), fixture.customerId());
        return fixture;
    }

    private void createAccount(String accountId, String customerId, String currency) {
        jdbc.update("""
                INSERT INTO account (
                    account_id, customer_id, account_type, currency, opening_balance, booked_balance
                ) VALUES (?, ?, 'CUSTOMER_LIABILITY', ?, 1000.00, 1000.00)
                """, accountId, customerId, currency);
    }

    private String reserve(Fixture fixture, String key) {
        String id = UUID.randomUUID().toString();
        reserve(id, fixture, key, FINGERPRINT);
        return id;
    }

    private void reserve(String id, Fixture fixture, String key, String fingerprint) {
        jdbc.update("""
                INSERT INTO idempotency_record (
                    idempotency_id, customer_id, operation, idempotency_key,
                    request_fingerprint, quote_id, source_account_id, destination_account_id,
                    status, created_at
                ) VALUES (?, ?, 'CREATE_FX_TRANSFER', ?, ?, ?, ?, ?, 'IN_PROGRESS',
                    TIMESTAMP '2026-10-09 15:31:00.123456789 +03:30')
                """, id, fixture.customerId(), key, fingerprint, fixture.quoteId(),
                fixture.sourceAccountId(), fixture.destinationAccountId());
    }

    private void insertTransfer(Fixture fixture) {
        assertThat(jdbc.update("""
                INSERT INTO transfer (
                    transfer_id, customer_id, quote_id, source_account_id, destination_account_id,
                    source_currency, destination_currency, source_amount, destination_amount,
                    fee_amount, rate, rate_provider, rate_version, rate_as_of, status, booked_at
                ) SELECT ?, customer_id, quote_id, ?, ?, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate, rate_provider,
                    rate_version, rate_as_of, 'BOOKED',
                    TIMESTAMP '2026-10-09 12:01:00.123456789 +00:00'
                  FROM quote WHERE quote_id = ?
                """, fixture.transferId(), fixture.sourceAccountId(), fixture.destinationAccountId(), fixture.quoteId()))
                .isEqualTo(1);
    }

    private void complete(String id, Fixture fixture, String response) {
        // State and result must change together to satisfy the row-shape check.
        assertThat(jdbc.update("""
                UPDATE idempotency_record SET status = 'SUCCEEDED', transfer_id = ?,
                    response_http_status = 201, response_body = ? WHERE idempotency_id = ?
                """, fixture.transferId(), new SqlParameterValue(Types.CLOB, response), id)).isEqualTo(1);
    }

    private int recordCount(String customerId, String key) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM idempotency_record
                WHERE customer_id = ? AND operation = 'CREATE_FX_TRANSFER' AND idempotency_key = ?
                """, Integer.class, customerId, key);
    }

    private int update(String id, String column, Object value) {
        // SQL identifiers come only from the fixed test cases; data values use bind parameters.
        return jdbc.update("UPDATE idempotency_record SET " + column + " = ? WHERE idempotency_id = ?", value, id);
    }

    private String text(String id, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM idempotency_record WHERE idempotency_id = ?",
                String.class, id);
    }

    private void assertRejected(Runnable action, String expectedDetail) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, action::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage()).contains(expectedDetail);
    }

    private record Fixture(String customerId, String quoteId, String sourceAccountId,
                           String destinationAccountId, String transferId) {
    }
}

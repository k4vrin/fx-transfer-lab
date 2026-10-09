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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuoteSchemaIT {

    // Direct SQL mutations isolate schema checks; they do not define the application's immutable quote API.

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void acceptsMultipleQuotesForOneCustomer() {
        String customerId = createCustomer();
        insertQuote(customerId);
        insertQuote(customerId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM quote WHERE customer_id = ?", Integer.class, customerId))
                .isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void acceptsBothSupportedCurrencyDirections(String source, String destination) {
        String quoteId = insertQuote(createCustomer());

        assertThat(jdbc.update("""
                UPDATE quote SET source_currency = ?, destination_currency = ?
                WHERE quote_id = ?
                """, source, destination, quoteId)).isEqualTo(1);
    }

    @Test
    void preservesMoneyAndEightDecimalRate() {
        String quoteId = insertQuote(createCustomer());

        assertDecimal(quoteId, "source_amount", "100.00");
        assertDecimal(quoteId, "destination_amount", "112.35");
        assertDecimal(quoteId, "fee_amount", "2.99");
        assertDecimal(quoteId, "rate", "1.12345678");
    }

    @Test
    void preservesTimestampNanosecondsAndInstants() {
        String quoteId = insertQuote(createCustomer());

        assertInstant(quoteId, "rate_as_of", "2026-10-09T11:59:00.123456789Z");
        assertInstant(quoteId, "created_at", "2026-10-09T12:00:00.123456789Z");
        assertInstant(quoteId, "expires_at", "2026-10-09T12:05:00.123456789Z");
    }

    @ParameterizedTest
    @ValueSource(strings = {"destination_amount", "fee_amount"})
    void acceptsZeroDestinationAmountOrFee(String column) {
        String quoteId = insertQuote(createCustomer());

        assertThat(update(quoteId, column, BigDecimal.ZERO)).isEqualTo(1);
        assertDecimal(quoteId, column, "0.00");
    }

    @Test
    void rejectsDuplicateQuoteId() {
        String customerId = createCustomer();
        String quoteId = insertQuote(customerId);

        assertRejected(() -> insertQuote(quoteId, customerId), "PK_QUOTE");
    }

    @Test
    void rejectsMissingCustomer() {
        assertRejected(() -> insertQuote(UUID.randomUUID().toString()), "FK_QUOTE_CUSTOMER");
    }

    @Test
    void rejectsDeletingCustomerWithQuote() {
        String customerId = createCustomer();
        insertQuote(customerId);

        assertRejected(() -> jdbc.update(
                "DELETE FROM customer WHERE customer_id = ?", customerId), "FK_QUOTE_CUSTOMER");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM quote WHERE customer_id = ?", Integer.class, customerId))
                .isEqualTo(1);
    }

    // Each case changes one column on an otherwise valid row to isolate its NOT NULL rule.
    @ParameterizedTest
    @ValueSource(strings = {
            "quote_id", "customer_id", "source_currency", "destination_currency",
            "source_amount", "destination_amount", "fee_amount", "rate",
            "rate_provider", "rate_version", "rate_as_of", "created_at", "expires_at"
    })
    void rejectsNullRequiredFields(String column) {
        String quoteId = insertQuote(createCustomer());

        assertRejected(() -> update(quoteId, column, null), "ORA-01407");
    }

    @ParameterizedTest
    @CsvSource({
            "source_currency, GBP, CK_QUOTE_SOURCE_CURRENCY",
            "source_currency, eur, CK_QUOTE_SOURCE_CURRENCY",
            "source_currency, EU, CK_QUOTE_SOURCE_CURRENCY",
            "destination_currency, GBP, CK_QUOTE_DESTINATION_CURRENCY",
            "destination_currency, usd, CK_QUOTE_DESTINATION_CURRENCY",
            "destination_currency, US, CK_QUOTE_DESTINATION_CURRENCY"
    })
    void rejectsUnsupportedOrNonCanonicalCurrencies(String column, String value, String constraint) {
        String quoteId = insertQuote(createCustomer());

        assertRejected(() -> update(quoteId, column, value), constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void rejectsSameCurrencyPair(String currency) {
        String quoteId = insertQuote(createCustomer());

        assertRejected(() -> jdbc.update("""
                UPDATE quote SET source_currency = ?, destination_currency = ?
                WHERE quote_id = ?
                """, currency, currency, quoteId), "CK_QUOTE_CURRENCY_PAIR");
    }

    @ParameterizedTest
    @CsvSource({
            "source_amount, 0.00, CK_QUOTE_SOURCE_AMOUNT",
            "source_amount, -0.01, CK_QUOTE_SOURCE_AMOUNT",
            "destination_amount, -0.01, CK_QUOTE_DESTINATION_AMOUNT",
            "fee_amount, -0.01, CK_QUOTE_FEE_AMOUNT",
            "rate, 0.00000000, CK_QUOTE_RATE",
            "rate, -0.00000001, CK_QUOTE_RATE"
    })
    void rejectsInvalidAmountsAndRates(String column, BigDecimal value, String constraint) {
        String quoteId = insertQuote(createCustomer());

        assertRejected(() -> update(quoteId, column, value), constraint);
    }

    @ParameterizedTest
    @CsvSource({
            "source_amount, 100000000000000000.00",
            "destination_amount, 100000000000000000.00",
            "fee_amount, 100000000000000000.00",
            "rate, 100000000000.00000000"
    })
    void rejectsNumbersBeyondStoragePrecision(String column, BigDecimal value) {
        String quoteId = insertQuote(createCustomer());

        assertRejected(() -> update(quoteId, column, value), "ORA-01438");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void rejectsWhitespaceOnlyProvenance(String column) {
        String quoteId = insertQuote(createCustomer());
        String constraint = column.equals("rate_provider") ? "CK_QUOTE_RATE_PROVIDER" : "CK_QUOTE_RATE_VERSION";

        assertRejected(() -> update(quoteId, column, " \t\n "), constraint);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void rejectsEmptyProvenance(String column) {
        String quoteId = insertQuote(createCustomer());

        // Oracle treats an empty VARCHAR2 string as NULL, so NOT NULL rejects it.
        assertRejected(() -> update(quoteId, column, ""), "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void preservesProvenanceIncludingSurroundingWhitespace(String column) {
        String quoteId = insertQuote(createCustomer());
        String value = " Fixture-V1 ";

        update(quoteId, column, value);

        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM quote WHERE quote_id = ?", String.class, quoteId))
                .isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"rate_provider", "rate_version"})
    void enforcesProvenanceLengthLimit(String column) {
        String quoteId = insertQuote(createCustomer());
        String maximumLength = "x".repeat(100);
        update(quoteId, column, maximumLength);

        assertRejected(() -> update(quoteId, column, "x".repeat(101)), "ORA-12899");
        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM quote WHERE quote_id = ?", String.class, quoteId))
                .isEqualTo(maximumLength);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-10-09 11:59:59.123456789 +00:00",
            "2026-10-09 12:00:00.123456789 +00:00",
            "2026-10-09 15:30:00.123456789 +03:30"
    })
    void rejectsExpiryBeforeOrAtCreation(String expiry) {
        String quoteId = insertQuote(createCustomer());

        // The last case has a different offset but represents exactly the creation instant.
        assertRejected(() -> jdbc.update(
                "UPDATE quote SET expires_at = TIMESTAMP '" + expiry + "' WHERE quote_id = ?", quoteId),
                "CK_QUOTE_EXPIRY");
    }

    @Test
    void acceptsExpiryOneNanosecondAfterCreation() {
        String quoteId = insertQuote(createCustomer());
        jdbc.update("""
                UPDATE quote SET expires_at =
                    TIMESTAMP '2026-10-09 15:30:00.123456790 +03:30'
                WHERE quote_id = ?
                """, quoteId);

        assertInstant(quoteId, "expires_at", "2026-10-09T12:00:00.123456790Z");
    }

    private String createCustomer() {
        String customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        return customerId;
    }

    private String insertQuote(String customerId) {
        String quoteId = UUID.randomUUID().toString();
        insertQuote(quoteId, customerId);
        return quoteId;
    }

    private void insertQuote(String quoteId, String customerId) {
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
    }

    private int update(String quoteId, String column, Object value) {
        // SQL identifiers cannot use bind parameters; column names come only from fixed test cases.
        return jdbc.update("UPDATE quote SET " + column + " = ? WHERE quote_id = ?", value, quoteId);
    }

    private void assertDecimal(String quoteId, String column, String expected) {
        assertThat(jdbc.queryForObject(
                "SELECT " + column + " FROM quote WHERE quote_id = ?", BigDecimal.class, quoteId))
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    private void assertInstant(String quoteId, String column, String expected) {
        OffsetDateTime stored = jdbc.queryForObject(
                "SELECT " + column + " FROM quote WHERE quote_id = ?",
                (rs, rowNumber) -> rs.getObject(1, OffsetDateTime.class), quoteId);
        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(Instant.parse(expected));
    }

    private void assertRejected(Runnable action, String expectedDetail) {
        DataAccessException failure = catchThrowableOfType(DataAccessException.class, action::run);
        assertThat(failure).isNotNull();
        assertThat(failure.getMostSpecificCause().getMessage()).contains(expectedDetail);
    }
}

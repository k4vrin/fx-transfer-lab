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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JournalEntrySchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storesFiveEntriesUnderOneJournalAndBalancesEachCurrencyInTheFixture() {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String clearingEur = createInternalAccount("FX_CLEARING_ASSET", "EUR");
        String clearingUsd = createInternalAccount("FX_CLEARING_ASSET", "USD");
        String revenueEur = createInternalAccount("FEE_REVENUE", "EUR");
        insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "102.00");
        insertEntry(journalId, clearingEur, "EUR", "CREDIT", "100.00");
        insertEntry(journalId, revenueEur, "EUR", "CREDIT", "2.00");
        insertEntry(journalId, clearingUsd, "USD", "DEBIT", "110.00");
        insertEntry(journalId, fixture.destinationId(), "USD", "CREDIT", "110.00");

        assertThat(entryCount(journalId)).isEqualTo(5);
        for (String currency : new String[]{"EUR", "USD"}) {
            BigDecimal signedTotal = jdbc.queryForObject("""
                    SELECT SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END)
                    FROM journal_entry WHERE journal_id = ? AND currency = ?
                    """, BigDecimal.class, journalId, currency);
            assertThat(signedTotal).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @ParameterizedTest
    @CsvSource({"EUR, DEBIT", "EUR, CREDIT", "USD, DEBIT", "USD, CREDIT"})
    void acceptsBothDirectionsForMatchingAccountCurrency(String currency, String direction) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String accountId = currency.equals("EUR") ? fixture.sourceId() : fixture.destinationId();
        String entryId = insertEntry(journalId, accountId, currency, direction, "12.34");

        assertThat(jdbc.queryForObject("SELECT amount FROM journal_entry WHERE entry_id = ?",
                BigDecimal.class, entryId)).isEqualByComparingTo("12.34");
        assertThat(jdbc.queryForObject("SELECT direction FROM journal_entry WHERE entry_id = ?",
                String.class, entryId)).isEqualTo(direction);
    }

    @Test
    void acceptsSameAccountInDifferentJournals() {
        Fixture first = createFixture();
        Fixture second = createFixture();
        String firstJournal = createJournal(first);
        String secondJournal = createJournal(second);
        insertEntry(firstJournal, first.sourceId(), "EUR", "DEBIT", "10.00");
        insertEntry(secondJournal, first.sourceId(), "EUR", "CREDIT", "10.00");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry WHERE account_id = ?",
                Integer.class, first.sourceId())).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateEntryId() {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String entryId = insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");

        assertRejected(() -> insertEntry(entryId, journalId, fixture.sourceId(),
                "EUR", "CREDIT", "1.00"), "PK_JOURNAL_ENTRY");
        assertThat(entryCount(journalId)).isEqualTo(1);
    }

    @Test
    void rejectsMissingJournal() {
        Fixture fixture = createFixture();
        assertRejected(() -> insertEntry(UUID.randomUUID().toString(), fixture.sourceId(),
                "EUR", "DEBIT", "1.00"), "FK_JOURNAL_ENTRY_JOURNAL");
    }

    @Test
    void rejectsMissingAccount() {
        String journalId = createJournal(createFixture());
        assertRejected(() -> insertEntry(journalId, UUID.randomUUID().toString(),
                "EUR", "DEBIT", "1.00"), "FK_JOURNAL_ENTRY_ACCOUNT_CURRENCY");
    }

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void rejectsAccountCurrencyMismatch(String accountCurrency, String entryCurrency) {
        String journalId = createJournal(createFixture());
        String accountId = createInternalAccount("FX_CLEARING_ASSET", accountCurrency);
        assertRejected(() -> insertEntry(journalId, accountId, entryCurrency, "DEBIT", "1.00"),
                "FK_JOURNAL_ENTRY_ACCOUNT_CURRENCY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GBP", "eur", "usd", "EU"})
    void rejectsCurrencyThatDoesNotMatchReferencedAccount(String currency) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        assertRejected(() -> insertEntry(journalId, fixture.sourceId(), currency, "DEBIT", "1.00"),
                "FK_JOURNAL_ENTRY_ACCOUNT_CURRENCY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"debit", "credit", "OTHER", " "})
    void rejectsInvalidDirection(String direction) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        assertRejected(() -> insertEntry(journalId, fixture.sourceId(), "EUR", direction, "1.00"),
                "CK_JOURNAL_ENTRY_DIRECTION");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-0.01"})
    void rejectsNonPositiveAmount(String amount) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        assertRejected(() -> insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", amount),
                "CK_JOURNAL_ENTRY_AMOUNT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"entry_id", "journal_id", "account_id", "direction", "amount", "currency"})
    void rejectsNullRequiredFields(String column) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String entryId = insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");
        assertRejected(() -> update(entryId, column, null), "ORA-01407");
    }

    @ParameterizedTest
    @ValueSource(strings = {"entry_id", "journal_id", "account_id", "direction", "currency"})
    void rejectsEmptyRequiredStrings(String column) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String entryId = insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");
        // Oracle treats an empty VARCHAR2 value as NULL, including composite FK columns.
        assertRejected(() -> update(entryId, column, ""), "ORA-01407");
    }

    @Test
    void rejectsDeletingJournalWithEntries() {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");
        assertRejected(() -> jdbc.update("DELETE FROM journal WHERE journal_id = ?", journalId),
                "FK_JOURNAL_ENTRY_JOURNAL");
        assertThat(entryCount(journalId)).isEqualTo(1);
        assertThat(journalCount(journalId)).isEqualTo(1);
    }

    @Test
    void rejectsDeletingAccountWithEntries() {
        String journalId = createJournal(createFixture());
        // This account is not referenced by a transfer, isolating the entry's foreign key.
        String accountId = createInternalAccount("FEE_REVENUE", "EUR");
        insertEntry(journalId, accountId, "EUR", "CREDIT", "1.00");
        assertRejected(() -> jdbc.update("DELETE FROM account WHERE account_id = ?", accountId),
                "FK_JOURNAL_ENTRY_ACCOUNT_CURRENCY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE account_id = ?",
                Integer.class, accountId)).isEqualTo(1);
        assertThat(entryCount(journalId)).isEqualTo(1);
    }

    @Test
    void rejectsChangingCurrencyOfAccountWithEntries() {
        String journalId = createJournal(createFixture());
        String accountId = createInternalAccount("FEE_REVENUE", "EUR");
        insertEntry(journalId, accountId, "EUR", "CREDIT", "1.00");
        assertRejected(() -> jdbc.update("UPDATE account SET currency = 'USD' WHERE account_id = ?",
                accountId), "FK_JOURNAL_ENTRY_ACCOUNT_CURRENCY");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "99999999999999999.99"})
    void storesPositiveAmountsAtStorageBoundaries(String amount) {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        String entryId = insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", amount);
        assertThat(jdbc.queryForObject("SELECT amount FROM journal_entry WHERE entry_id = ?",
                BigDecimal.class, entryId)).isEqualByComparingTo(amount);
    }

    @Test
    void rejectsAmountBeyondStoragePrecision() {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        assertRejected(() -> insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT",
                "100000000000000000.00"), "ORA-01438");
    }

    @Test
    void rowConstraintsDoNotEnforceWholeJournalBalanceOrMinimumEntryCount() {
        Fixture fixture = createFixture();
        String journalId = createJournal(fixture);
        insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "102.00");
        assertThat(entryCount(journalId)).isEqualTo(1);
        insertEntry(journalId, fixture.sourceId(), "EUR", "CREDIT", "100.00");
        BigDecimal imbalance = jdbc.queryForObject("""
                SELECT SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END)
                FROM journal_entry WHERE journal_id = ? AND currency = 'EUR'
                """, BigDecimal.class, journalId);
        assertThat(imbalance).isEqualByComparingTo("2.00");
    }

    @Test
    void rollbackRemovesJournalAndEntriesTogether() {
        Fixture fixture = createFixture();
        insertTransfer(fixture);
        String journalId = UUID.randomUUID().toString();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint beforeJournal = connection.setSavepoint();
            insertJournal(journalId, fixture.transferId());
            insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");
            assertThat(entryCount(journalId)).isEqualTo(1);
            connection.rollback(beforeJournal);
            return null;
        });
        assertThat(journalCount(journalId)).isZero();
        assertThat(entryCount(journalId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transfer WHERE transfer_id = ?",
                Integer.class, fixture.transferId())).isEqualTo(1);
        insertJournal(journalId, fixture.transferId());
        insertEntry(journalId, fixture.sourceId(), "EUR", "DEBIT", "1.00");
        assertThat(entryCount(journalId)).isEqualTo(1);
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


    private String createJournal(Fixture fixture) {
        insertTransfer(fixture);
        String journalId = UUID.randomUUID().toString();
        insertJournal(journalId, fixture.transferId());
        return journalId;
    }

    private void insertJournal(String journalId, String transferId) {
        assertThat(jdbc.update("""
                INSERT INTO journal (journal_id, transfer_id, created_at)
                VALUES (?, ?, TIMESTAMP '2026-10-10 12:01:00 +00:00')
                """, journalId, transferId)).isEqualTo(1);
    }

    private String createInternalAccount(String type, String currency) {
        String accountId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO account (account_id, account_type, currency, opening_balance, booked_balance)
                VALUES (?, ?, ?, 1000.00, 1000.00)
                """, accountId, type, currency);
        return accountId;
    }

    private String insertEntry(String journalId, String accountId, String currency,
                               String direction, String amount) {
        String entryId = UUID.randomUUID().toString();
        insertEntry(entryId, journalId, accountId, currency, direction, amount);
        return entryId;
    }

    private void insertEntry(String entryId, String journalId, String accountId, String currency,
                             String direction, String amount) {
        assertThat(jdbc.update("""
                INSERT INTO journal_entry (entry_id, journal_id, account_id, currency, direction, amount)
                VALUES (?, ?, ?, ?, ?, ?)
                """, entryId, journalId, accountId, currency, direction, new BigDecimal(amount))).isEqualTo(1);
    }

    private int entryCount(String journalId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry WHERE journal_id = ?",
                Integer.class, journalId);
    }

    private int journalCount(String journalId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM journal WHERE journal_id = ?",
                Integer.class, journalId);
    }

    private int update(String entryId, String column, Object value) {
        // Identifiers come only from fixed test cases; direct updates isolate constraints.
        return jdbc.update("UPDATE journal_entry SET " + column + " = ? WHERE entry_id = ?", value, entryId);
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

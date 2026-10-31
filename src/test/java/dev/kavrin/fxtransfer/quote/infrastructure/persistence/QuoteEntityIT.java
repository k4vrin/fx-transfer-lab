package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.quote.domain.Quote;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.shared.domain.Money;
import dev.kavrin.fxtransfer.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuoteEntityIT {

    private static final Instant CREATED = Instant.parse("2020-01-01T00:00:00.123456789Z");
    private static final Instant EXPIRES = CREATED.plusSeconds(300).plusNanos(7);
    private static final Instant AS_OF = CREATED.minusSeconds(60).minusNanos(3);

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void loadsIndependentSqlSnapshotWithoutChangingHistoricalValues(String source, String destination) {
        var quoteId = UUID.randomUUID().toString();
        var customerId = createCustomer();
        insertQuote(quoteId, customerId, source, destination, "152.41");

        var entity = entityManager.find(QuoteEntity.class, quoteId);

        assertThat(entity).isNotNull();
        assertThat(entity.getQuoteId()).isEqualTo(quoteId);
        assertThat(entity.getCustomerId()).isEqualTo(customerId);
        assertSnapshot(QuoteEntityMapper.toDomain(entity), source, destination);
    }

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void persistsFlushesAndReloadsSnapshotWithNanosecondInstants(String source, String destination) {
        var quoteId = UUID.randomUUID().toString();
        var customerId = createCustomer();
        var quote = snapshot(source, destination);
        entityManager.persist(QuoteEntityMapper.toEntity(quoteId, customerId, quote));
        entityManager.flush();
        entityManager.clear();

        var loaded = entityManager.find(QuoteEntity.class, quoteId);
        assertThat(loaded.getQuoteId()).isEqualTo(quoteId);
        assertThat(loaded.getCustomerId()).isEqualTo(customerId);
        assertSnapshot(QuoteEntityMapper.toDomain(loaded), source, destination);

        // Inspect raw stored values as well, so paired mapper errors cannot mask each other.
        jdbc.queryForObject("SELECT * FROM quote WHERE quote_id = ?", (rs, row) -> {
            assertThat(rs.getString("customer_id")).isEqualTo(customerId);
            assertThat(rs.getString("source_currency")).isEqualTo(source);
            assertThat(rs.getString("destination_currency")).isEqualTo(destination);
            assertThat(rs.getBigDecimal("source_amount")).isEqualByComparingTo("123.45");
            assertThat(rs.getBigDecimal("destination_amount")).isEqualByComparingTo("152.41");
            assertThat(rs.getBigDecimal("fee_amount")).isEqualByComparingTo("2.50");
            assertThat(rs.getBigDecimal("rate")).isEqualByComparingTo("1.23456789");
            assertThat(rs.getString("rate_provider")).isEqualTo("historical-provider");
            assertThat(rs.getString("rate_version")).isEqualTo("historical-v1");
            assertThat(rs.getObject("rate_as_of", OffsetDateTime.class).toInstant()).isEqualTo(AS_OF);
            assertThat(rs.getObject("created_at", OffsetDateTime.class).toInstant()).isEqualTo(CREATED);
            assertThat(rs.getObject("expires_at", OffsetDateTime.class).toInstant()).isEqualTo(EXPIRES);
            return true;
        }, quoteId);
    }

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void rejectsStoredAmountsThatRowConstraintsCannotValidate(String source, String destination) {
        var quoteId = UUID.randomUUID().toString();
        // Oracle permits this nonnegative destination, but it disagrees with the saved rate.
        insertQuote(quoteId, createCustomer(), source, destination, "999.99");
        var entity = entityManager.find(QuoteEntity.class, quoteId);

        assertThatThrownBy(() -> QuoteEntityMapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destination amount must match the stored rate conversion");
    }

    private String createCustomer() {
        var customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        return customerId;
    }

    private void insertQuote(String quoteId, String customerId, String source,
                             String destination, String destinationAmount) {
        jdbc.update("""
                INSERT INTO quote (quote_id, customer_id, source_currency, destination_currency,
                    source_amount, destination_amount, fee_amount, rate, rate_provider,
                    rate_version, rate_as_of, created_at, expires_at)
                VALUES (?, ?, ?, ?, 123.45, ?, 2.50, 1.23456789, 'historical-provider',
                    'historical-v1', TO_TIMESTAMP_TZ(?, 'YYYY-MM-DD"T"HH24:MI:SS.FF9TZH:TZM'),
                    TO_TIMESTAMP_TZ(?, 'YYYY-MM-DD"T"HH24:MI:SS.FF9TZH:TZM'),
                    TO_TIMESTAMP_TZ(?, 'YYYY-MM-DD"T"HH24:MI:SS.FF9TZH:TZM'))
                """, quoteId, customerId, source, destination, new BigDecimal(destinationAmount),
                AS_OF.toString().replace("Z", "+00:00"),
                CREATED.toString().replace("Z", "+00:00"),
                EXPIRES.toString().replace("Z", "+00:00"));
    }

    private static Quote snapshot(String source, String destination) {
        var from = Currency.getInstance(source);
        var to = Currency.getInstance(destination);
        return Quote.restore(Money.of(new BigDecimal("123.45"), from),
                Money.of(new BigDecimal("152.41"), to), Money.of(new BigDecimal("2.50"), from),
                new RateSnapshot(DirectedExchangeRate.of(new BigDecimal("1.23456789"), from, to),
                        "historical-provider", "historical-v1", AS_OF), CREATED, EXPIRES);
    }

    private static void assertSnapshot(Quote quote, String source, String destination) {
        assertThat(quote).usingRecursiveComparison().isEqualTo(snapshot(source, destination));
        assertThat(quote.sourceAmount().amount().scale()).isEqualTo(2);
        assertThat(quote.destinationAmount().amount().scale()).isEqualTo(2);
        assertThat(quote.fee().amount().scale()).isEqualTo(2);
        assertThat(quote.rateSnapshot().rate().rate().scale()).isEqualTo(8);
        assertThat(quote.isExpired(EXPIRES)).isTrue();
        assertThat(quote.isExpired(EXPIRES.minusNanos(1))).isFalse();
    }
}

package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.quote.application.port.QuoteRepository;
import dev.kavrin.fxtransfer.quote.application.port.StoredQuote;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.Quote;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.shared.domain.Money;
import dev.kavrin.fxtransfer.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class QuotePersistenceAdapterIT {
    @Autowired private QuoteRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void insertsAndFindsSnapshotWithIdentityAndOwnership(String source, String destination) {
        var customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        var stored = new StoredQuote(UUID.randomUUID().toString(), customerId, snapshot(source, destination));
        repository.insert(stored);
        entityManager.flush();
        entityManager.clear();

        var found = repository.findById(stored.quoteId()).orElseThrow();
        assertThat(found.quoteId()).isEqualTo(stored.quoteId());
        assertThat(found.customerId()).isEqualTo(customerId);
        assertThat(found.quote()).usingRecursiveComparison().isEqualTo(stored.quote());
        assertThat(jdbc.queryForObject("SELECT customer_id FROM quote WHERE quote_id = ?",
                String.class, stored.quoteId())).isEqualTo(customerId);
    }

    @Test
    void missingQuoteReturnsEmpty() {
        assertThat(repository.findById(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void duplicateIdentityCannotOverwriteSnapshot() {
        var customerId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer (customer_id) VALUES (?)", customerId);
        var stored = new StoredQuote(UUID.randomUUID().toString(), customerId, snapshot("EUR", "USD"));
        repository.insert(stored);
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> {
            repository.insert(new StoredQuote(stored.quoteId(), customerId, snapshot("USD", "EUR")));
            entityManager.flush();
        }).isInstanceOf(RuntimeException.class);
    }

    private static Quote snapshot(String source, String destination) {
        var from = Currency.getInstance(source);
        var to = Currency.getInstance(destination);
        var created = Instant.parse("2020-01-01T00:00:00.123456789Z");
        return Quote.restore(Money.of(new BigDecimal("123.45"), from),
                Money.of(new BigDecimal("152.41"), to), Money.of(new BigDecimal("2.50"), from),
                new RateSnapshot(DirectedExchangeRate.of(new BigDecimal("1.23456789"), from, to),
                        "provider", "v1", created.minusSeconds(60)), created, created.plusSeconds(300));
    }
}

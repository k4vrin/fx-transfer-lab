package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.quote.application.port.StoredQuote;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.Quote;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.shared.domain.Money;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QuotePersistenceAdapterTest {
    private final EntityManager entityManager = mock(EntityManager.class);
    private final QuotePersistenceAdapter adapter = new QuotePersistenceAdapter(entityManager);

    @Test
    void insertUsesPersistAndPreservesTheSnapshot() {
        var stored = fixture();
        adapter.insert(stored);
        var entity = ArgumentCaptor.forClass(QuoteEntity.class);
        verify(entityManager).persist(entity.capture());
        verifyNoMoreInteractions(entityManager);
        assertThat(entity.getValue().getQuoteId()).isEqualTo(stored.quoteId());
        assertThat(entity.getValue().getCustomerId()).isEqualTo(stored.customerId());
        assertThat(QuoteEntityMapper.toDomain(entity.getValue())).usingRecursiveComparison().isEqualTo(stored.quote());
    }

    @Test
    void findReturnsIdentityOwnershipAndSnapshotOrEmpty() {
        var stored = fixture();
        when(entityManager.find(QuoteEntity.class, stored.quoteId()))
                .thenReturn(QuoteEntityMapper.toEntity(stored.quoteId(), stored.customerId(), stored.quote()));
        var found = adapter.findById(stored.quoteId()).orElseThrow();
        assertThat(found.quoteId()).isEqualTo(stored.quoteId());
        assertThat(found.customerId()).isEqualTo(stored.customerId());
        assertThat(found.quote()).usingRecursiveComparison().isEqualTo(stored.quote());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    private static StoredQuote fixture() {
        var eur = Currency.getInstance("EUR");
        var usd = Currency.getInstance("USD");
        var created = Instant.parse("2020-01-01T00:00:00.123456789Z");
        var snapshot = new RateSnapshot(DirectedExchangeRate.of(new BigDecimal("1.23456789"), eur, usd),
                "provider", "v1", created.minusSeconds(60));
        var quote = Quote.restore(Money.of(new BigDecimal("123.45"), eur),
                Money.of(new BigDecimal("152.41"), usd), Money.of(new BigDecimal("2.50"), eur),
                snapshot, created, created.plusSeconds(300));
        return new StoredQuote("quote-1", "customer-1", quote);
    }
}

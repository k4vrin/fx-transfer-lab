package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.fee.domain.FixedFeePolicy;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.Quote;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteEntityMapperTest {

    @ParameterizedTest
    @CsvSource({"EUR, USD", "USD, EUR"})
    void preservesCompleteSnapshotInBothDirections(String source, String destination) {
        var quote = quote(source, destination);

        var entity = QuoteEntityMapper.toEntity("quote-1", "customer-1", quote);
        var restored = QuoteEntityMapper.toDomain(entity);

        assertThat(entity.getQuoteId()).isEqualTo("quote-1");
        assertThat(entity.getCustomerId()).isEqualTo("customer-1");
        assertThat(restored).usingRecursiveComparison().isEqualTo(quote);
        assertThat(restored.isExpired(quote.expiresAt())).isTrue();
        assertThat(restored.isExpired(quote.expiresAt().minusNanos(1))).isFalse();
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> QuoteEntityMapper.toDomain(null))
                .isInstanceOf(NullPointerException.class).hasMessage("entity");
        assertThatThrownBy(() -> QuoteEntityMapper.toEntity("q", "c", null))
                .isInstanceOf(NullPointerException.class).hasMessage("quote");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1234567890123456789012345678901234567"})
    void rejectsInvalidIdentity(String id) {
        var quote = quote("EUR", "USD");
        assertThatThrownBy(() -> QuoteEntityMapper.toEntity(id, "customer-1", quote))
                .isInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class);
        assertThatThrownBy(() -> QuoteEntityMapper.toEntity("quote-1", id, quote))
                .isInstanceOf(id == null ? NullPointerException.class : IllegalArgumentException.class);
    }

    static Quote quote(String source, String destination) {
        var now = Instant.parse("2026-10-29T12:00:00.123456789Z");
        var sourceCurrency = Currency.getInstance(source);
        var rate = DirectedExchangeRate.of(new BigDecimal("1.23456789"),
                sourceCurrency, Currency.getInstance(destination));
        return Quote.create(Money.of(new BigDecimal("123.45"), sourceCurrency),
                new RateSnapshot(rate, "saved-provider", "saved-version", now.minusSeconds(60)),
                new FixedFeePolicy(Money.of(new BigDecimal("2.50"), sourceCurrency)),
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(300).plusNanos(7));
    }
}

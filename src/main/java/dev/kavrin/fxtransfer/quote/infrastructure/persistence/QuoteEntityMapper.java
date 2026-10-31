package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.Quote;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.shared.domain.Money;

import java.util.Currency;
import java.util.Objects;

public final class QuoteEntityMapper {

    private QuoteEntityMapper() {
    }

    public static QuoteEntity toEntity(String quoteId, String customerId, Quote quote) {
        Objects.requireNonNull(quote, "quote");
        requireId(quoteId, "quoteId");
        requireId(customerId, "customerId");
        var snapshot = quote.rateSnapshot();
        return new QuoteEntity(quoteId, customerId,
                quote.sourceAmount().currency().getCurrencyCode(),
                quote.destinationAmount().currency().getCurrencyCode(),
                quote.sourceAmount().amount(), quote.destinationAmount().amount(),
                quote.fee().amount(), snapshot.rate().rate(), snapshot.provider(),
                snapshot.version(), snapshot.asOf(), quote.createdAt(), quote.expiresAt());
    }

    public static Quote toDomain(QuoteEntity entity) {
        Objects.requireNonNull(entity, "entity");
        var sourceCurrency = Currency.getInstance(entity.getSourceCurrency());
        var destinationCurrency = Currency.getInstance(entity.getDestinationCurrency());
        var snapshot = new RateSnapshot(
                DirectedExchangeRate.of(entity.getRate(), sourceCurrency, destinationCurrency),
                entity.getRateProvider(), entity.getRateVersion(), entity.getRateAsOf());
        return Quote.restore(
                Money.of(entity.getSourceAmount(), sourceCurrency),
                Money.of(entity.getDestinationAmount(), destinationCurrency),
                Money.of(entity.getFeeAmount(), sourceCurrency),
                snapshot, entity.getCreatedAt(), entity.getExpiresAt());
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException(name + " must contain 1 to 36 characters");
        }
    }
}

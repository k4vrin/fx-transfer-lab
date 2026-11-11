package dev.kavrin.fxtransfer.quote.application.port;

import dev.kavrin.fxtransfer.quote.domain.Quote;

import java.util.Objects;

/** Identity and ownership accompany the immutable monetary snapshot. */
public record StoredQuote(String quoteId, String customerId, Quote quote) {
    public StoredQuote {
        requireId(quoteId, "quoteId");
        requireId(customerId, "customerId");
        Objects.requireNonNull(quote, "quote");
    }

    private static void requireId(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException(name + " must contain 1 to 36 characters");
        }
    }
}

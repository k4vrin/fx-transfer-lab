package dev.kavrin.fxtransfer.quote.application.port;

import java.util.Optional;

public interface QuoteRepository {
    /** Insert a new snapshot; an existing identity must never be overwritten. */
    void insert(StoredQuote quote);

    Optional<StoredQuote> findById(String quoteId);
}

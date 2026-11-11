package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import dev.kavrin.fxtransfer.quote.application.port.QuoteRepository;
import dev.kavrin.fxtransfer.quote.application.port.StoredQuote;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class QuotePersistenceAdapter implements QuoteRepository {
    private final EntityManager entityManager;

    public QuotePersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void insert(StoredQuote stored) {
        entityManager.persist(QuoteEntityMapper.toEntity(stored.quoteId(), stored.customerId(), stored.quote()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredQuote> findById(String quoteId) {
        return Optional.ofNullable(entityManager.find(QuoteEntity.class, quoteId))
                .map(entity -> new StoredQuote(entity.getQuoteId(), entity.getCustomerId(),
                        QuoteEntityMapper.toDomain(entity)));
    }
}

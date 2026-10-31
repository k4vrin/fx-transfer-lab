package dev.kavrin.fxtransfer.quote.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "quote")
public class QuoteEntity {

    @Id
    @Column(name = "quote_id", nullable = false, updatable = false, length = 36)
    private String quoteId;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 36)
    private String customerId;

    @Column(name = "source_currency", nullable = false, updatable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "destination_currency", nullable = false, updatable = false, length = 3)
    private String destinationCurrency;

    @Column(name = "source_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal sourceAmount;

    @Column(name = "destination_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal destinationAmount;

    @Column(name = "fee_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "rate", nullable = false, updatable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "rate_provider", nullable = false, updatable = false, length = 100)
    private String rateProvider;

    @Column(name = "rate_version", nullable = false, updatable = false, length = 100)
    private String rateVersion;

    @Column(name = "rate_as_of", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(9) WITH TIME ZONE")
    private Instant rateAsOf;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(9) WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(9) WITH TIME ZONE")
    private Instant expiresAt;

    protected QuoteEntity() {
        // Required by JPA when loading a saved snapshot.
    }

    public QuoteEntity(
            String quoteId,
            String customerId,
            String sourceCurrency,
            String destinationCurrency,
            BigDecimal sourceAmount,
            BigDecimal destinationAmount,
            BigDecimal feeAmount,
            BigDecimal rate,
            String rateProvider,
            String rateVersion,
            Instant rateAsOf,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.quoteId = quoteId;
        this.customerId = customerId;
        this.sourceCurrency = sourceCurrency;
        this.destinationCurrency = destinationCurrency;
        this.sourceAmount = sourceAmount;
        this.destinationAmount = destinationAmount;
        this.feeAmount = feeAmount;
        this.rate = rate;
        this.rateProvider = rateProvider;
        this.rateVersion = rateVersion;
        this.rateAsOf = rateAsOf;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getQuoteId() {
        return quoteId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getSourceCurrency() {
        return sourceCurrency;
    }

    public String getDestinationCurrency() {
        return destinationCurrency;
    }

    public BigDecimal getSourceAmount() {
        return sourceAmount;
    }

    public BigDecimal getDestinationAmount() {
        return destinationAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getRateProvider() {
        return rateProvider;
    }

    public String getRateVersion() {
        return rateVersion;
    }

    public Instant getRateAsOf() {
        return rateAsOf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}

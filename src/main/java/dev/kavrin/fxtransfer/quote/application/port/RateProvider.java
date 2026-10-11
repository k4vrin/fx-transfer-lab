package dev.kavrin.fxtransfer.quote.application.port;

import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;

import java.util.Currency;

@FunctionalInterface
public interface RateProvider {
    RateSnapshot getRate(
            Currency sourceCurrency,
            Currency destinationCurrency
    );
}
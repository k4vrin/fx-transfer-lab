package dev.kavrin.fxtransfer.quote.infrastructure.configuration;

import dev.kavrin.fxtransfer.quote.application.port.RateProvider;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import dev.kavrin.fxtransfer.quote.infrastructure.rate.ConfiguredRateProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;

// This bean factory is self-contained, so calls between @Bean methods need no interception.
// If another bean needs the provider, inject it as a method parameter instead of calling rateProvider().
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalRateConfiguration {

    @Bean
    public RateProvider rateProvider() {
        RateSnapshot eurToUsd = new RateSnapshot(
                DirectedExchangeRate.of(
                        new BigDecimal("1.10000000"),
                        Currency.getInstance("EUR"),
                        Currency.getInstance("USD")
                ),
                "local-fixture",
                "fixture-v1",
                Instant.parse("2026-10-12T09:00:00Z")
        );

        RateSnapshot usdToEur = new RateSnapshot(
                DirectedExchangeRate.of(
                        new BigDecimal("0.88000000"),
                        Currency.getInstance("USD"),
                        Currency.getInstance("EUR")
                ),
                "local-fixture",
                "fixture-v1",
                Instant.parse("2026-10-12T09:00:00Z")
        );

        return new ConfiguredRateProvider(List.of(eurToUsd, usdToEur));

    }
}

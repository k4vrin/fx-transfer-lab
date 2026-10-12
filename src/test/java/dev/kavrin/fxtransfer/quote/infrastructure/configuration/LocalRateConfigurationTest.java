package dev.kavrin.fxtransfer.quote.infrastructure.configuration;

import dev.kavrin.fxtransfer.quote.application.port.RateProvider;
import dev.kavrin.fxtransfer.quote.domain.DirectedExchangeRate;
import dev.kavrin.fxtransfer.quote.domain.RateSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class LocalRateConfigurationTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant AS_OF = Instant.parse("2026-10-12T09:00:00Z");

    @Test
    void registersExactlyOneRateProviderWithLocalProfile() {
        withLocalContext(context -> {
            var beans = context.getBeansOfType(RateProvider.class);
            assertThat(beans).hasSize(1);
        });
    }

    @Test
    void doesNotRegisterRateProviderWithoutAnActiveProfile() {
        withContext(context -> {
            var beans = context.getBeansOfType(RateProvider.class);
            assertThat(beans).isEmpty();
        });
    }

    @Test
    void doesNotRegisterRateProviderWithOnlyProductionProfile() {
        withContext(context -> {
            var beans = context.getBeansOfType(RateProvider.class);
            assertThat(beans).isEmpty();
        }, "prod");
    }

    @Test
    void exposesConfiguredEurToUsdSnapshot() {
        withLocalContext(context -> {
            var snapshot = expectedSnapshot(EUR, USD, "1.10000000");
            var actual = provider(context).getRate(EUR, USD);
            assertThat(actual).isEqualTo(snapshot);
        });
    }

    @Test
    void exposesSeparatelyConfiguredUsdToEurSnapshot() {
        withLocalContext(context -> {
            var snapshot = expectedSnapshot(USD, EUR, "0.88000000");
            var actual = provider(context).getRate(USD, EUR);
            assertThat(actual).isEqualTo(snapshot);
        });
    }

    @Test
    void repeatedBeanLookupsReturnTheSameProviderInstance() {
        withLocalContext(context -> {
            var provider1 = provider(context);
            var provider2 = provider(context);
            assertThat(provider1).isSameAs(provider2);
        });
    }

    private void withLocalContext(Consumer<AnnotationConfigApplicationContext> assertions) {
        withContext(assertions, "local");
    }

    private void withContext(
            Consumer<AnnotationConfigApplicationContext> assertions,
            String... activeProfiles
    ) {
        // Load only this configuration so these wiring tests need no database or Boot startup.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(activeProfiles);
            context.register(LocalRateConfiguration.class);
            context.refresh();
            assertions.accept(context);
        }
    }

    private RateProvider provider(AnnotationConfigApplicationContext context) {
        return context.getBean(RateProvider.class);
    }

    private RateSnapshot expectedSnapshot(Currency source, Currency destination, String rate) {
        return new RateSnapshot(
                DirectedExchangeRate.of(new BigDecimal(rate), source, destination),
                "local-fixture",
                "fixture-v1",
                AS_OF
        );
    }
}

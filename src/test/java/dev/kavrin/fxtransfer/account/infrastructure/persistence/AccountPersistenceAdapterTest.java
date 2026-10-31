package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.application.AccountRepository;
import dev.kavrin.fxtransfer.account.domain.AccountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AccountPersistenceAdapterTest {

    private final AccountJpaRepository jpaRepository = mock(AccountJpaRepository.class);
    private final AccountRepository repository = new AccountPersistenceAdapter(jpaRepository);

    @ParameterizedTest
    @ValueSource(strings = {"EUR", "USD"})
    void returnsMappedAccountWhenFound(String currency) {
        when(jpaRepository.findById("account-1")).thenReturn(Optional.of(entity(currency)));

        var result = repository.findById("account-1");

        assertThat(result).hasValueSatisfying(account -> {
            assertThat(account.accountId()).isEqualTo("account-1");
            assertThat(account.customerId()).isEqualTo("customer-1");
            assertThat(account.accountType()).isEqualTo(AccountType.CUSTOMER_LIABILITY);
            assertThat(account.openingBalance().amount()).isEqualTo(new BigDecimal("123.45"));
            assertThat(account.bookedBalance().amount()).isEqualTo(new BigDecimal("67.89"));
            assertThat(account.openingBalance().currency()).isEqualTo(Currency.getInstance(currency));
            assertThat(account.bookedBalance().currency()).isEqualTo(Currency.getInstance(currency));
        });
        verify(jpaRepository).findById("account-1");
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void returnsEmptyWhenAccountIsMissing() {
        when(jpaRepository.findById("missing-account")).thenReturn(Optional.empty());

        assertThat(repository.findById("missing-account")).isEmpty();

        verify(jpaRepository).findById("missing-account");
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void propagatesRepositoryFailureInsteadOfReportingMissingAccount() {
        var failure = new DataAccessResourceFailureException("database unavailable");
        when(jpaRepository.findById("account-1")).thenThrow(failure);

        assertThatThrownBy(() -> repository.findById("account-1")).isSameAs(failure);

        verify(jpaRepository).findById("account-1");
        verifyNoMoreInteractions(jpaRepository);
    }

    @Test
    void propagatesInvalidPersistedStateInsteadOfReportingMissingAccount() {
        // JPY is valid as a currency code but unsupported by the Money domain contract.
        when(jpaRepository.findById("account-1")).thenReturn(Optional.of(entity("JPY")));

        assertThatThrownBy(() -> repository.findById("account-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("currency is not supported");

        verify(jpaRepository).findById("account-1");
        verifyNoMoreInteractions(jpaRepository);
    }

    private static AccountEntity entity(String currency) {
        return new AccountEntity("account-1", "customer-1", AccountType.CUSTOMER_LIABILITY,
                currency, new BigDecimal("123.45"), new BigDecimal("67.89"));
    }
}

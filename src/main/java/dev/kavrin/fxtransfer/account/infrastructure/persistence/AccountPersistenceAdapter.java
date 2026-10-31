package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import dev.kavrin.fxtransfer.account.application.AccountRepository;
import dev.kavrin.fxtransfer.account.domain.Account;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository accountJpaRepository;

    public AccountPersistenceAdapter(AccountJpaRepository accountJpaRepository) {
        this.accountJpaRepository = accountJpaRepository;
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return accountJpaRepository.findById(accountId)
                .map(AccountEntityMapper::toDomain);
    }
}

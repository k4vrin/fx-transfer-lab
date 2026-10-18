package dev.kavrin.fxtransfer.account.application;

import dev.kavrin.fxtransfer.account.domain.Account;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(String accountId);
}

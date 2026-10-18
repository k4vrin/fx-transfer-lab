package dev.kavrin.fxtransfer.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, String> {
}

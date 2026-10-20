package com.planno.store.identity.repository;

import com.planno.store.identity.domain.EmailTwoFactorChallenge;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTwoFactorChallengeRepository extends JpaRepository<EmailTwoFactorChallenge, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from EmailTwoFactorChallenge challenge where challenge.userId = :userId")
    Optional<EmailTwoFactorChallenge> findByUserIdForUpdate(@Param("userId") UUID userId);
}

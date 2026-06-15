package com.myeasybudget.user.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    /** Invalidate any outstanding (unconsumed) tokens for a user before issuing a fresh one. */
    @Modifying
    @Query("delete from EmailVerificationTokenEntity t where t.user.id = :userId and t.consumedAt is null")
    void deleteUnconsumedByUserId(@Param("userId") UUID userId);
}

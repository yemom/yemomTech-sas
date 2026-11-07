package com.YemomBizGrow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    Optional<UserToken> findByTokenTypeAndTokenHash(UserTokenType tokenType, String tokenHash);

    @Modifying
    @Query("update UserToken t set t.consumedAt = :now where t.userId = :userId and t.tokenType = :type and t.consumedAt is null")
    int invalidateExistingForUser(@Param("userId") UUID userId, @Param("type") UserTokenType type, @Param("now") Instant now);
}

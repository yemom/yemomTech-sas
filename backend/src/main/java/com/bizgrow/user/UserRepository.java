package com.YemomBizGrow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are normalized (lowercase) before storage, so this exact-match lookup is correct. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}

package com.YemomBizGrow.user;

import com.YemomBizGrow.common.entity.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A platform account (one human, potentially member of many organizations).
 *
 * <p>Invariants enforced by the database, not just the code: unique normalized
 * email ({@code uq_users_email}), non-null password hash, status from a closed set.
 * The entity is never serialized to clients — the auth module maps it to DTOs.</p>
 */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uq_users_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class User extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Stored trimmed + lowercased by the application before persistence. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** BCrypt hash — never exposed, never logged, never mapped to a DTO. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Optional; international format, e.g. +251934000000 (E.164-ish, stored as text). */
    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}

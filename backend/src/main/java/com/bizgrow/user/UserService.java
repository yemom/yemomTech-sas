package com.YemomBizGrow.user;

import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.auth.dto.RegisterRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns every write to the {@code users} table — registration, password hashing,
 * login bookkeeping. Other modules (auth, invitations) ask this service for users
 * instead of touching {@link UserRepository} directly, so account invariants
 * (normalized e-mail, hashed password, status rules) have exactly one gatekeeper.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean emailExists(String normalizedEmail) {
        return userRepository.existsByEmail(normalizedEmail);
    }

    /**
     * Creates an ACTIVE account with a BCrypt-hashed password.
     *
     * @throws ConflictException when the e-mail is already registered
     */
    @Transactional
    public User create(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(trimToNull(request.phoneNumber()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists.");
        }
    }

    public Optional<User> findByEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(normalizeEmail(rawEmail));
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> NotFoundException.resource("User"));
    }

    /** Batch lookup for member lists (avoids N+1 when rendering a page of members). */
    public java.util.Map<java.util.UUID, User> findUsersByIds(java.util.Collection<java.util.UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
    }

    /** BCrypt verification (constant-cost, salted). */
    public boolean matchesPassword(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void markLogin(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        });
    }

    @Transactional
    public User verifyEmail(UUID userId) {
        User user = getById(userId);
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        return userRepository.save(user);
    }

    @Transactional
    public void updatePassword(UUID userId, String rawNewPassword) {
        User user = getById(userId);
        user.setPasswordHash(passwordEncoder.encode(rawNewPassword));
        userRepository.save(user);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

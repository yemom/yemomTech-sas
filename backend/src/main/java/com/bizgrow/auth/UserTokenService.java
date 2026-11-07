package com.YemomBizGrow.auth;

import com.YemomBizGrow.common.config.AppProperties;
import com.YemomBizGrow.common.email.EmailDelivery;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.util.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserTokenService {

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);

    private final UserTokenRepository tokenRepository;
    private final EmailDelivery emailDelivery;
    private final AppProperties properties;

    public UserTokenService(UserTokenRepository tokenRepository,
                            EmailDelivery emailDelivery,
                            AppProperties properties) {
        this.tokenRepository = tokenRepository;
        this.emailDelivery = emailDelivery;
        this.properties = properties;
    }

    @Transactional
    public String issueEmailVerificationToken(UUID userId, String recipientEmail) {
        Instant now = Instant.now();
        tokenRepository.invalidateExistingForUser(userId, UserTokenType.EMAIL_VERIFICATION, now);

        String rawToken = TokenHasher.newRawToken();
        UserToken token = new UserToken();
        token.setUserId(userId);
        token.setTokenType(UserTokenType.EMAIL_VERIFICATION);
        token.setTokenHash(TokenHasher.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(VERIFICATION_TTL));
        token.setCreatedAt(now);
        tokenRepository.save(token);

        String url = properties.getFrontend().getBaseUrl() + "/verify-email?token=" + rawToken;
        emailDelivery.sendVerificationEmail(recipientEmail, rawToken, url);
        return rawToken;
    }

    @Transactional
    public UUID verifyEmailToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Verification token is required.");
        }

        String hash = TokenHasher.sha256Hex(rawToken);
        UserToken token = tokenRepository.findByTokenTypeAndTokenHash(UserTokenType.EMAIL_VERIFICATION, hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification token."));

        if (token.isExpired() || token.isConsumed()) {
            throw new BadRequestException("Invalid or expired verification token.");
        }

        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);
        return token.getUserId();
    }

    @Transactional
    public String issuePasswordResetToken(UUID userId, String recipientEmail) {
        Instant now = Instant.now();
        tokenRepository.invalidateExistingForUser(userId, UserTokenType.PASSWORD_RESET, now);

        String rawToken = TokenHasher.newRawToken();
        UserToken token = new UserToken();
        token.setUserId(userId);
        token.setTokenType(UserTokenType.PASSWORD_RESET);
        token.setTokenHash(TokenHasher.sha256Hex(rawToken));
        token.setExpiresAt(now.plus(PASSWORD_RESET_TTL));
        token.setCreatedAt(now);
        tokenRepository.save(token);

        String url = properties.getFrontend().getBaseUrl() + "/reset-password?token=" + rawToken;
        emailDelivery.sendPasswordResetEmail(recipientEmail, rawToken, url);
        return rawToken;
    }

    @Transactional
    public UUID consumePasswordResetToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Password reset token is required.");
        }

        String hash = TokenHasher.sha256Hex(rawToken);
        UserToken token = tokenRepository.findByTokenTypeAndTokenHash(UserTokenType.PASSWORD_RESET, hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token."));

        if (token.isExpired() || token.isConsumed()) {
            throw new BadRequestException("Invalid or expired password reset token.");
        }

        token.setConsumedAt(Instant.now());
        tokenRepository.save(token);
        return token.getUserId();
    }
}

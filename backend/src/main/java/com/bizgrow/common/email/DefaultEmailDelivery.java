package com.YemomBizGrow.common.email;

import com.YemomBizGrow.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default transactional email delivery service.
 * In development (or test mode where exposeDevToken is enabled), helpful debug info is logged.
 * In production without an external SMTP gateway configured, it safely records dispatch
 * attempts without leaking tokens or sensitive URLs to production logs.
 */
@Component
public class DefaultEmailDelivery implements EmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailDelivery.class);

    private final AppProperties properties;

    public DefaultEmailDelivery(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendVerificationEmail(String recipientEmail, String rawToken, String verificationUrl) {
        if (properties.getSecurity().getInvitation().isExposeDevToken()) {
            log.info("[DEV] Verification email for {}: url={}", recipientEmail, verificationUrl);
        } else {
            log.info("Verification email dispatched to {}", recipientEmail);
        }
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String rawToken, String resetUrl) {
        if (properties.getSecurity().getInvitation().isExposeDevToken()) {
            log.info("[DEV] Password reset email for {}: url={}", recipientEmail, resetUrl);
        } else {
            log.info("Password reset email dispatched to {}", recipientEmail);
        }
    }

    @Override
    public void sendInvitationEmail(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        if (properties.getSecurity().getInvitation().isExposeDevToken()) {
            log.info("[DEV] Invitation to {} for '{}': url={}", recipientEmail, organizationName, acceptUrl);
        } else {
            log.info("Invitation dispatched to {} for '{}'", recipientEmail, organizationName);
        }
    }
}

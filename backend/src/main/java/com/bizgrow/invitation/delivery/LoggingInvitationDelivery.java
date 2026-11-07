package com.bizgrow.invitation.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PRODUCTION delivery path (default when {@code expose-dev-token} is off).
 *
 * <p>No SMTP/mail provider is provisioned yet, so this adapter does the honest
 * thing: it records that an invitation must be sent (recipient + organization,
 * NO token) and logs a clear warning that delivery is pending infrastructure.
 * The API response never contains the token in this mode, so the invitation is
 * unusable-but-safe until mail is configured — which matches the specification's
 * "blocked by missing external configuration" category instead of pretending a
 * notification was sent.</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.invitation.expose-dev-token", havingValue = "false", matchIfMissing = true)
public class LoggingInvitationDelivery implements InvitationDelivery {

    private static final Logger log = LoggerFactory.getLogger(LoggingInvitationDelivery.class);

    @Override
    public void deliver(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        // Intentionally no rawToken / no acceptUrl here: production logs must never
        // contain material that grants access to an organization.
        log.warn("Invitation created for {} to join '{}' — no mail transport configured yet, "
                + "email not sent. Configure SMTP to enable delivery.", recipientEmail, organizationName);
    }
}

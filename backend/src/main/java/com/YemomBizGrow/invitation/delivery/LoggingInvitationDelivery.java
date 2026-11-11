package com.YemomBizGrow.invitation.delivery;

import com.YemomBizGrow.common.email.EmailDelivery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Standard invitation delivery path: dispatches via the configured EmailDelivery provider.
 */
@Component
@ConditionalOnProperty(name = "app.security.invitation.expose-dev-token", havingValue = "false", matchIfMissing = true)
public class LoggingInvitationDelivery implements InvitationDelivery {

    private final EmailDelivery emailDelivery;

    public LoggingInvitationDelivery(EmailDelivery emailDelivery) {
        this.emailDelivery = emailDelivery;
    }

    @Override
    public void deliver(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        emailDelivery.sendInvitationEmail(recipientEmail, rawToken, organizationName, acceptUrl);
    }
}

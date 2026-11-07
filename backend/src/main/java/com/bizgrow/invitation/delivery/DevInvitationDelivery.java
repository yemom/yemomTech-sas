package com.YemomBizGrow.invitation.delivery;

import com.YemomBizGrow.common.email.EmailDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT-only delivery path, active only when
 * {@code app.security.invitation.expose-dev-token=true}.
 */
@Component
@ConditionalOnProperty(name = "app.security.invitation.expose-dev-token", havingValue = "true")
public class DevInvitationDelivery implements InvitationDelivery {

    private static final Logger log = LoggerFactory.getLogger(DevInvitationDelivery.class);
    private final EmailDelivery emailDelivery;

    public DevInvitationDelivery(EmailDelivery emailDelivery) {
        this.emailDelivery = emailDelivery;
    }

    @Override
    public void deliver(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        log.info("[DEV] Invitation issued to {} for '{}' — raw token returned in the API response "
                + "(dev-only flag enabled; never enable outside dev/test).", recipientEmail, organizationName);
        emailDelivery.sendInvitationEmail(recipientEmail, rawToken, organizationName, acceptUrl);
    }
}

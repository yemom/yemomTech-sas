package com.bizgrow.invitation.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT-only delivery path, active only when
 * {@code app.security.invitation.expose-dev-token=true} (set in application-dev.yml
 * and application-test.yml; StartupValidator forbids it in production profiles).
 *
 * <p>Instead of SMTP, the developer completes the invite flow by copying
 * {@code devAcceptToken} from the API response into the accept form. The token
 * still is not written to logs — the response is the single dev surface.</p>
 */
@Component
@ConditionalOnProperty(name = "app.security.invitation.expose-dev-token", havingValue = "true")
public class DevInvitationDelivery implements InvitationDelivery {

    private static final Logger log = LoggerFactory.getLogger(DevInvitationDelivery.class);

    @Override
    public void deliver(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        log.info("[DEV] Invitation issued to {} for '{}' — raw token returned in the API response "
                + "(dev-only flag enabled; never enable outside dev/test).", recipientEmail, organizationName);
    }
}

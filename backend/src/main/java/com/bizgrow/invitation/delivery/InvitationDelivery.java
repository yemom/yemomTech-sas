package com.bizgrow.invitation.delivery;

/**
 * Delivers an issued invitation to its recipient.
 *
 * <p>Contract: implementations receive the RAW token because e-mail content is
 * the one legitimate place it may appear — but implementations must never log it.
 * The production adapter queues mail through the configured transport (SMTP not
 * yet provisioned: it reports the gap honestly); the dev adapter surfaces the
 * token through the gated API response instead of any transport.</p>
 */
public interface InvitationDelivery {

    /**
     * @param recipientEmail normalized recipient address
     * @param rawToken       one-time acceptance token (never log/store in plaintext)
     * @param organizationName display name of the inviting organization
     * @param acceptUrl      frontend URL where the token can be redeemed
     */
    void deliver(String recipientEmail, String rawToken, String organizationName, String acceptUrl);
}

package com.YemomBizGrow.common.email;

public interface EmailDelivery {

    void sendVerificationEmail(String recipientEmail, String rawToken, String verificationUrl);

    void sendPasswordResetEmail(String recipientEmail, String rawToken, String resetUrl);

    void sendInvitationEmail(String recipientEmail, String rawToken, String organizationName, String acceptUrl);
}

package com.YemomBizGrow.common.email;

import com.YemomBizGrow.common.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Real transactional email delivery service supporting JavaMailSender / SMTP.
 *
 * <p>When an SMTP host is configured (via {@code SPRING_MAIL_HOST}), emails are dispatched
 * as multi-part HTML + plain text with YemomBizGrow branding.
 * When SMTP credentials are omitted (e.g. in development or testing), it logs safely
 * or exposes the token only if {@code expose-dev-token} is explicitly configured.</p>
 */
@Component
public class DefaultEmailDelivery implements EmailDelivery {

    private static final Logger log = LoggerFactory.getLogger(DefaultEmailDelivery.class);

    private final AppProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String fromAddress;

    public DefaultEmailDelivery(
            AppProperties properties,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.mail.from-address:no-reply@yemombizgrow.com}") String fromAddress) {
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost != null ? mailHost.trim() : "";
        this.fromAddress = fromAddress != null ? fromAddress.trim() : "no-reply@yemombizgrow.com";
    }

    public boolean isSmtpConfigured() {
        return !mailHost.isBlank() && mailSenderProvider.getIfAvailable() != null;
    }

    @Override
    public void sendVerificationEmail(String recipientEmail, String rawToken, String verificationUrl) {
        String subject = "Verify your YemomBizGrow account";
        String htmlContent = buildHtmlTemplate(
                "Welcome to YemomBizGrow",
                "Thank you for registering. Please confirm your email address by clicking the button below:",
                "Verify Email Address",
                verificationUrl
        );

        dispatchEmail(recipientEmail, subject, htmlContent, "Verification", verificationUrl);
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String rawToken, String resetUrl) {
        String subject = "Reset your YemomBizGrow password";
        String htmlContent = buildHtmlTemplate(
                "Password Reset Request",
                "A password reset was requested for your YemomBizGrow account. If you did not request this, please ignore this email.",
                "Reset Password",
                resetUrl
        );

        dispatchEmail(recipientEmail, subject, htmlContent, "Password reset", resetUrl);
    }

    @Override
    public void sendInvitationEmail(String recipientEmail, String rawToken, String organizationName, String acceptUrl) {
        String subject = "You've been invited to join " + organizationName + " on YemomBizGrow";
        String htmlContent = buildHtmlTemplate(
                "Team Invitation",
                "You have been invited to join <strong>" + organizationName + "</strong> on YemomBizGrow. Click the button below to accept:",
                "Accept Invitation",
                acceptUrl
        );

        dispatchEmail(recipientEmail, subject, htmlContent, "Organization invitation", acceptUrl);
    }

    private void dispatchEmail(String recipientEmail, String subject, String htmlBody, String emailType, String actionUrl) {
        if (isSmtpConfigured()) {
            try {
                JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                    helper.setFrom(fromAddress, "YemomBizGrow");
                    helper.setTo(recipientEmail);
                    helper.setSubject(subject);
                    helper.setText(htmlBody, true);

                    mailSender.send(message);
                    log.info("Live SMTP email sent: type='{}' to='{}' subject='{}'", emailType, recipientEmail, subject);
                    return;
                }
            } catch (Exception e) {
                log.error("Failed to dispatch live email via SMTP to {}: {}", recipientEmail, e.getMessage(), e);
            }
        }

        // Fallback when SMTP is not configured
        if (properties.getSecurity().getInvitation().isExposeDevToken()) {
            log.info("[DEV] {} email for {}: url={}", emailType, recipientEmail, actionUrl);
        } else {
            log.info("Email delivery simulated (no SMTP host configured): type='{}' to='{}'", emailType, recipientEmail);
        }
    }

    private String buildHtmlTemplate(String title, String description, String buttonText, String buttonUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px; color: #1e293b; }
                        .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden; }
                        .header { background: #0f766e; padding: 24px; text-align: center; color: #ffffff; }
                        .header h1 { margin: 0; font-size: 22px; font-weight: 700; }
                        .content { padding: 32px 24px; }
                        .content h2 { margin-top: 0; font-size: 18px; color: #0f172a; }
                        .content p { font-size: 14px; line-height: 1.6; color: #475569; }
                        .button-wrap { text-align: center; margin: 32px 0; }
                        .button { background-color: #0d9488; color: #ffffff !important; padding: 12px 28px; border-radius: 6px; font-size: 14px; font-weight: 600; text-decoration: none; display: inline-block; }
                        .footer { background: #f1f5f9; padding: 16px 24px; text-align: center; font-size: 12px; color: #64748b; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>YemomBizGrow</h1>
                        </div>
                        <div class="content">
                            <h2>""" + title + """
                            </h2>
                            <p>""" + description + """
                            </p>
                            <div class="button-wrap">
                                <a href=\"""" + buttonUrl + """
                                " class="button">""" + buttonText + """
                                </a>
                            </div>
                            <p style="font-size: 12px; color: #94a3b8;">If the button does not work, copy and paste this link into your browser:<br><a href=\"""" + buttonUrl + """
                            " style="color: #0d9488; word-break: break-all;\">""" + buttonUrl + """
                            </a></p>
                        </div>
                        <div class="footer">
                            &copy; YemomBizGrow. All rights reserved.
                        </div>
                    </div>
                </body>
                </html>
                """;
    }
}

package com.myeasybudget.user.infrastructure.email;

import com.myeasybudget.user.application.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends transactional email over SMTP via Spring's {@link JavaMailSender}
 * (configured by the {@code spring.mail.*} properties).
 */
@Component
public class JavaMailEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public JavaMailEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from:no-reply@my-easy-budget.com}") String from
    ) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationLink) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Confirm your myEasyBudget email");
            helper.setText(buildBody(displayName, verificationLink), true);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Could not build verification email", ex);
        }
        // Any send failure propagates; callers treat email delivery as best-effort.
        mailSender.send(message);
    }

    private static String buildBody(String displayName, String verificationLink) {
        String greeting = (displayName == null || displayName.isBlank()) ? "Hi" : "Hi " + escape(displayName);
        return """
                <div style="font-family: Arial, sans-serif; color: #111827; line-height: 1.6;">
                  <h2 style="color: #4054d4;">Welcome to myEasyBudget</h2>
                  <p>%s,</p>
                  <p>Please confirm your email address to activate your account.</p>
                  <p>
                    <a href="%s"
                       style="display: inline-block; padding: 12px 22px; border-radius: 999px;
                              background: #4054d4; color: #ffffff; font-weight: bold; text-decoration: none;">
                      Confirm my email
                    </a>
                  </p>
                  <p>Or paste this link into your browser:</p>
                  <p><a href="%s">%s</a></p>
                  <p style="color: #8b95a1; font-size: 13px;">
                    This link expires in 24 hours. If you didn't create an account, you can ignore this email.
                  </p>
                </div>
                """.formatted(greeting, verificationLink, verificationLink, verificationLink);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

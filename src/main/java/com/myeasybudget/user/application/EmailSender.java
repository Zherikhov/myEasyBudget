package com.myeasybudget.user.application;

/** Abstraction over outbound transactional email, so the domain does not depend on JavaMail. */
public interface EmailSender {

    /**
     * Send the "confirm your email" message.
     *
     * @param toEmail          recipient address
     * @param displayName      recipient's display name, may be {@code null}
     * @param verificationLink absolute URL the user clicks to confirm ownership
     */
    void sendVerificationEmail(String toEmail, String displayName, String verificationLink);
}

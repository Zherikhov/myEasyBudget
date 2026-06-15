package com.myeasybudget.user.application;

import com.myeasybudget.user.infrastructure.persistence.AppUserEntity;
import com.myeasybudget.user.infrastructure.persistence.AuthIdentityEntity;
import com.myeasybudget.user.infrastructure.persistence.AuthIdentityRepository;
import com.myeasybudget.user.infrastructure.persistence.AuthProvider;
import com.myeasybudget.user.infrastructure.persistence.EmailVerificationTokenEntity;
import com.myeasybudget.user.infrastructure.persistence.EmailVerificationTokenRepository;
import com.myeasybudget.user.infrastructure.persistence.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the email-verification lifecycle for LOCAL accounts: issuing single-use tokens,
 * delivering the confirmation email, and consuming a token to mark the address verified.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final int TOKEN_BYTES = 32;

    private final EmailVerificationTokenRepository tokenRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final EmailSender emailSender;
    private final JwtTokenService jwtTokenService;
    private final Clock clock;
    private final Duration tokenExpiration;
    private final String frontendBaseUrl;
    private final boolean logVerificationLink;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            AuthIdentityRepository authIdentityRepository,
            EmailSender emailSender,
            JwtTokenService jwtTokenService,
            @Value("${app.security.email-verification.token-expiration:PT24H}") Duration tokenExpiration,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${app.mail.log-verification-link:false}") boolean logVerificationLink
    ) {
        this(tokenRepository, authIdentityRepository, emailSender, jwtTokenService,
                Clock.systemUTC(), tokenExpiration, frontendBaseUrl, logVerificationLink);
    }

    EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            AuthIdentityRepository authIdentityRepository,
            EmailSender emailSender,
            JwtTokenService jwtTokenService,
            Clock clock,
            Duration tokenExpiration,
            String frontendBaseUrl,
            boolean logVerificationLink
    ) {
        this.tokenRepository = tokenRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.emailSender = emailSender;
        this.jwtTokenService = jwtTokenService;
        this.clock = clock;
        this.tokenExpiration = tokenExpiration;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.logVerificationLink = logVerificationLink;
    }

    /**
     * Issue a fresh token for the user and send the confirmation email. Must run inside the
     * caller's transaction; the email is dispatched only after that transaction commits, so a
     * rolled-back registration never produces a "verify your account" email.
     */
    public void issueAndSend(AppUserEntity user, String displayName) {
        tokenRepository.deleteUnconsumedByUserId(user.getId());

        String rawToken = generateRawToken();
        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(OffsetDateTime.now(clock).plus(tokenExpiration));
        tokenRepository.save(token);

        String link = frontendBaseUrl + "/verify-email?token=" + rawToken;
        String email = user.getEmail();
        if (logVerificationLink) {
            // Dev-only convenience (off by default): SMTP is often not configured locally,
            // so surface the link in the logs. Never enable this in production.
            log.info("Email verification link for {}: {}", email, link);
        }
        sendAfterCommit(email, displayName, link);
    }

    /**
     * Confirm ownership of an email by consuming a valid token and return an authenticated
     * session, so the user is signed in immediately after clicking the link.
     */
    @Transactional
    public AuthResult verify(String rawToken) {
        EmailVerificationTokenEntity token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);

        if (token.isConsumed() || token.isExpired(OffsetDateTime.now(clock))) {
            throw new InvalidVerificationTokenException();
        }

        AppUserEntity user = token.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidVerificationTokenException();
        }

        token.setConsumedAt(OffsetDateTime.now(clock));

        AuthIdentityEntity identity = authIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.LOCAL, user.getEmailNormalized())
                .orElseThrow(InvalidVerificationTokenException::new);
        identity.setProviderEmailVerified(true);

        JwtToken jwt = jwtTokenService.createToken(user);
        return new AuthResult(jwt.value(), jwt.expiresAt(), UserSummary.from(user));
    }

    /**
     * Re-send a verification email. Intentionally silent about whether the account exists or is
     * already verified, so it cannot be used to enumerate registered addresses.
     */
    @Transactional
    public void resend(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        authIdentityRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, normalizedEmail)
                .filter(identity -> !Boolean.TRUE.equals(identity.getProviderEmailVerified()))
                .ifPresent(identity -> {
                    AppUserEntity user = identity.getUser();
                    if (user.getDeletedAt() == null && user.getStatus() == UserStatus.ACTIVE) {
                        issueAndSend(user, user.getDisplayName());
                    }
                });
    }

    private void sendAfterCommit(String email, String displayName, String link) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trySend(email, displayName, link);
                }
            });
        } else {
            trySend(email, displayName, link);
        }
    }

    private void trySend(String email, String displayName, String link) {
        try {
            emailSender.sendVerificationEmail(email, displayName, link);
        } catch (RuntimeException ex) {
            // Best-effort: a failed send must not fail the request. The user can ask for a resend.
            log.warn("Failed to send verification email to {}", email, ex);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

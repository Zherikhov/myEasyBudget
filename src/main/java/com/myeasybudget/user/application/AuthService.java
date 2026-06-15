package com.myeasybudget.user.application;

import com.myeasybudget.user.infrastructure.persistence.AppUserEntity;
import com.myeasybudget.user.infrastructure.persistence.AppUserRepository;
import com.myeasybudget.user.infrastructure.persistence.AuthIdentityEntity;
import com.myeasybudget.user.infrastructure.persistence.AuthIdentityRepository;
import com.myeasybudget.user.infrastructure.persistence.AuthProvider;
import com.myeasybudget.user.infrastructure.persistence.UserStatus;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /**
     * A well-formed BCrypt hash used only to spend a comparable amount of CPU when the
     * account does not exist, so login response times do not reveal whether an email is
     * registered (user-enumeration via timing).
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AppUserRepository appUserRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            AppUserRepository appUserRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            EmailVerificationService emailVerificationService
    ) {
        this.appUserRepository = appUserRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * Create the account and send a verification email. The user is <em>not</em> signed in: the
     * email must be confirmed before {@link #login(LoginCommand)} will succeed.
     */
    @Transactional
    public RegistrationResult register(RegisterCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        if (appUserRepository.existsByEmailNormalizedAndDeletedAtIsNull(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        AppUserEntity user = new AppUserEntity();
        user.setEmail(command.email().trim());
        user.setEmailNormalized(normalizedEmail);
        user.setDisplayName(blankToNull(command.displayName()));
        user.setDefaultCurrencyCode(command.defaultCurrencyCode().trim().toUpperCase(Locale.ROOT));
        user.setLocale(command.locale().trim());
        user.setTimezone(command.timezone().trim());
        user.setStatus(UserStatus.ACTIVE);

        AuthIdentityEntity identity = new AuthIdentityEntity();
        identity.setUser(user);
        identity.setProvider(AuthProvider.LOCAL);
        identity.setProviderUserId(normalizedEmail);
        identity.setProviderEmail(normalizedEmail);
        identity.setProviderEmailVerified(false);
        identity.setPasswordHash(passwordEncoder.encode(command.password()));

        try {
            AppUserEntity savedUser = appUserRepository.save(user);
            authIdentityRepository.save(identity);
            appUserRepository.flush();
            emailVerificationService.issueAndSend(savedUser, savedUser.getDisplayName());
            return new RegistrationResult(savedUser.getEmail());
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent registration; the partial unique
            // indexes on email enforce the invariant the pre-check could not.
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public AuthResult login(LoginCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        AuthIdentityEntity identity = authIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.LOCAL, normalizedEmail)
                .orElse(null);

        if (identity == null || identity.getPasswordHash() == null) {
            // Spend comparable time so a missing account is indistinguishable from a wrong password.
            passwordEncoder.matches(command.password(), DUMMY_BCRYPT_HASH);
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(command.password(), identity.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        AppUserEntity user = identity.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        // Credentials are valid; the only thing standing between the user and a session is
        // confirming their email. Reported distinctly so the UI can offer a resend.
        if (!Boolean.TRUE.equals(identity.getProviderEmailVerified())) {
            throw new EmailNotVerifiedException();
        }

        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public UserSummary findActiveUser(UUID userId) {
        AppUserEntity user = appUserRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(UserNotFoundException::new);
        return UserSummary.from(user);
    }

    private AuthResult issueToken(AppUserEntity user) {
        JwtToken token = jwtTokenService.createToken(user);
        return new AuthResult(token.value(), token.expiresAt(), UserSummary.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

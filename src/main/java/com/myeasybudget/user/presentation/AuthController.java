package com.myeasybudget.user.presentation;

import com.myeasybudget.user.application.AuthResult;
import com.myeasybudget.user.application.AuthService;
import com.myeasybudget.user.application.EmailVerificationService;
import com.myeasybudget.user.application.RegistrationResult;
import com.myeasybudget.user.application.UserSummary;
import com.myeasybudget.user.security.AuthenticatedUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegisterRequest request) {
        RegistrationResult result = authService.register(request.toCommand());
        return new RegistrationResponse(result.email(), true);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.toCommand());
        return AuthResponse.from(result);
    }

    @PostMapping("/verify-email")
    public AuthResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthResult result = emailVerificationService.verify(request.token());
        return AuthResponse.from(result);
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resend(request.email());
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return authService.findActiveUser(principal.userId());
    }

    public record AuthResponse(
            String tokenType,
            String accessToken,
            Instant expiresAt,
            UserSummary user
    ) {

        static AuthResponse from(AuthResult result) {
            return new AuthResponse("Bearer", result.accessToken(), result.expiresAt(), result.user());
        }
    }

    public record RegistrationResponse(
            String email,
            boolean verificationRequired
    ) {
    }
}

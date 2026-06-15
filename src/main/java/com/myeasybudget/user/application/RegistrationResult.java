package com.myeasybudget.user.application;

/** Outcome of a registration: the account exists but must verify its email before signing in. */
public record RegistrationResult(
        String email
) {
}

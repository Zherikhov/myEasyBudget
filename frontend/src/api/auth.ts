import { apiRequest } from "../lib/apiClient";

export type UserStatus = "ACTIVE" | "SUSPENDED" | "DELETED";

/** Mirrors the backend UserSummary record. */
export interface User {
  id: string;
  email: string;
  displayName: string | null;
  defaultCurrencyCode: string;
  locale: string;
  timezone: string;
  status: UserStatus;
}

/** Mirrors the backend AuthController.AuthResponse record. */
export interface AuthResponse {
  tokenType: string;
  accessToken: string;
  expiresAt: string;
  user: User;
}

/** Mirrors the backend AuthController.RegistrationResponse record. */
export interface RegistrationResponse {
  email: string;
  verificationRequired: boolean;
}

export interface RegisterPayload {
  email: string;
  password: string;
  displayName?: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

/** Account is created but not signed in: the email must be verified first. */
export function register(payload: RegisterPayload): Promise<RegistrationResponse> {
  return apiRequest<RegistrationResponse>("/auth/register", { method: "POST", body: payload });
}

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/auth/login", { method: "POST", body: payload });
}

/** Confirm the email-verification token and receive an authenticated session. */
export function verifyEmail(token: string): Promise<AuthResponse> {
  return apiRequest<AuthResponse>("/auth/verify-email", { method: "POST", body: { token } });
}

/** Request another verification email. Always resolves (the backend is enumeration-safe). */
export function resendVerification(email: string): Promise<void> {
  return apiRequest<void>("/auth/resend-verification", { method: "POST", body: { email } });
}

export function fetchCurrentUser(token: string): Promise<User> {
  return apiRequest<User>("/auth/me", { token });
}

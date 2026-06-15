import { ArrowRight, Eye, EyeOff, Loader2, LockKeyhole, Mail, MailCheck, User as UserIcon } from "lucide-react";
import { FormEvent, useState } from "react";
import { ApiError, useAuth } from "../auth/AuthContext";

type AuthMode = "login" | "register";

// Mirror the backend Bean Validation constraints so we fail fast client-side
// (RegisterRequest: password @Size(min = 8, max = 72)).
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

function AuthPanel() {
  const { login, register, resendVerification } = useAuth();

  const [mode, setMode] = useState<AuthMode>("login");
  const [showPassword, setShowPassword] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Email shown on the post-registration "check your inbox" screen.
  const [pendingEmail, setPendingEmail] = useState<string | null>(null);
  // Set when login is blocked because the address hasn't been confirmed yet.
  const [needsVerification, setNeedsVerification] = useState(false);

  // Shared "resend verification email" affordance.
  const [resending, setResending] = useState(false);
  const [resendNotice, setResendNotice] = useState<string | null>(null);

  const isLogin = mode === "login";

  function switchMode(next: AuthMode) {
    if (next === mode) {
      return;
    }
    setMode(next);
    setError(null);
    setNeedsVerification(false);
    setResendNotice(null);
    setPassword("");
  }

  function validate(): string | null {
    if (!isLogin && password.length < PASSWORD_MIN) {
      return `Password must be at least ${PASSWORD_MIN} characters.`;
    }
    if (password.length > PASSWORD_MAX) {
      return `Password must be no longer than ${PASSWORD_MAX} characters.`;
    }
    if (!isLogin && !acceptedTerms) {
      return "Please accept the terms of service to continue.";
    }
    return null;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setError(null);
    setNeedsVerification(false);
    setResendNotice(null);
    setSubmitting(true);
    try {
      const trimmedEmail = email.trim();
      if (isLogin) {
        await login({ email: trimmedEmail, password });
        // On success the AuthProvider swaps this view out for the dashboard.
      } else {
        const trimmedName = displayName.trim();
        const result = await register({
          email: trimmedEmail,
          password,
          displayName: trimmedName === "" ? undefined : trimmedName,
        });
        setPendingEmail(result.email);
      }
    } catch (caught) {
      if (caught instanceof ApiError) {
        if (caught.code === "EMAIL_NOT_VERIFIED") {
          setNeedsVerification(true);
        }
        setError(caught.fieldErrors.length > 0 ? caught.fieldErrors.join("\n") : caught.message);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend(targetEmail: string) {
    if (resending || targetEmail.trim() === "") {
      return;
    }
    setResending(true);
    setResendNotice(null);
    try {
      await resendVerification(targetEmail.trim());
      setResendNotice("If that address is registered and unverified, a new link is on its way.");
    } catch {
      setResendNotice("Couldn't send the email right now. Please try again in a moment.");
    } finally {
      setResending(false);
    }
  }

  // ---- Post-registration: ask the user to confirm their email ----
  if (pendingEmail) {
    return (
      <section className="auth-card" aria-label="Verify your email">
        <div className="notice-icon">
          <MailCheck size={30} aria-hidden="true" />
        </div>
        <div className="heading">
          <h2>Confirm your email</h2>
          <p>
            We've sent a verification link to <strong>{pendingEmail}</strong>. Click it to
            activate your account, then sign in.
          </p>
        </div>

        {resendNotice && (
          <p className="form-info" role="status">
            {resendNotice}
          </p>
        )}

        <div className="stacked-actions">
          <button
            type="button"
            className="submit-button"
            onClick={() => handleResend(pendingEmail)}
            disabled={resending}
          >
            {resending ? (
              <>
                <Loader2 size={19} className="spin" aria-hidden="true" />
                Sending…
              </>
            ) : (
              "Resend verification email"
            )}
          </button>
          <button
            type="button"
            className="ghost-button block"
            onClick={() => {
              setPendingEmail(null);
              setMode("login");
              setResendNotice(null);
            }}
          >
            Back to sign in
          </button>
        </div>
      </section>
    );
  }

  // ---- Default: sign in / sign up form ----
  return (
    <section className="auth-card" aria-label="Authentication">
      <div className="tabs" role="tablist" aria-label="Authentication mode">
        <button
          className={isLogin ? "active" : ""}
          type="button"
          role="tab"
          aria-selected={isLogin}
          onClick={() => switchMode("login")}
        >
          Sign in
        </button>
        <button
          className={!isLogin ? "active" : ""}
          type="button"
          role="tab"
          aria-selected={!isLogin}
          onClick={() => switchMode("register")}
        >
          Sign up
        </button>
      </div>

      <div className="heading">
        <h2>{isLogin ? "Sign in to your account" : "Create an account"}</h2>
        <p>
          {isLogin
            ? "Pick up where you left off with your budget, accounts, and financial goals."
            : "Start budgeting and tracking your expenses in one place."}
        </p>
      </div>

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        {!isLogin && (
          <label className="field">
            <span>Name</span>
            <div className="input-wrap">
              <UserIcon size={18} aria-hidden="true" />
              <input
                type="text"
                placeholder="Your name"
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                disabled={submitting}
              />
            </div>
          </label>
        )}

        <label className="field">
          <span>Email</span>
          <div className="input-wrap">
            <Mail size={18} aria-hidden="true" />
            <input
              type="email"
              placeholder="name@example.com"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              disabled={submitting}
              required
            />
          </div>
        </label>

        <label className="field">
          <span>Password</span>
          <div className="input-wrap">
            <LockKeyhole size={18} aria-hidden="true" />
            <input
              type={showPassword ? "text" : "password"}
              placeholder={isLogin ? "Enter your password" : "At least 8 characters"}
              autoComplete={isLogin ? "current-password" : "new-password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              disabled={submitting}
              minLength={isLogin ? undefined : PASSWORD_MIN}
              maxLength={PASSWORD_MAX}
              required
            />
            <button
              className="icon-button"
              type="button"
              aria-label={showPassword ? "Hide password" : "Show password"}
              onClick={() => setShowPassword((visible) => !visible)}
              disabled={submitting}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>
        </label>

        {!isLogin && (
          <label className="checkbox terms">
            <input
              type="checkbox"
              checked={acceptedTerms}
              onChange={(event) => setAcceptedTerms(event.target.checked)}
              disabled={submitting}
            />
            <span>I accept the terms of service and privacy policy.</span>
          </label>
        )}

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        {needsVerification && (
          <button
            type="button"
            className="ghost-button block"
            onClick={() => handleResend(email)}
            disabled={resending}
          >
            {resending ? (
              <>
                <Loader2 size={18} className="spin" aria-hidden="true" />
                Sending…
              </>
            ) : (
              "Resend verification email"
            )}
          </button>
        )}

        {resendNotice && (
          <p className="form-info" role="status">
            {resendNotice}
          </p>
        )}

        <button className="submit-button" type="submit" disabled={submitting}>
          {submitting ? (
            <>
              <Loader2 size={19} className="spin" aria-hidden="true" />
              Please wait…
            </>
          ) : (
            <>
              {isLogin ? "Sign in" : "Sign up"}
              <ArrowRight size={19} aria-hidden="true" />
            </>
          )}
        </button>
      </form>
    </section>
  );
}

export default AuthPanel;

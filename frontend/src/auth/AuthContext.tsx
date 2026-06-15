import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from "react";
import {
  AuthResponse,
  fetchCurrentUser,
  login as loginRequest,
  LoginPayload,
  register as registerRequest,
  RegisterPayload,
  RegistrationResponse,
  resendVerification as resendVerificationRequest,
  User,
  verifyEmail as verifyEmailRequest,
} from "../api/auth";
import { ApiError } from "../lib/apiClient";
import { clearToken, readToken, writeToken } from "./tokenStorage";

interface AuthContextValue {
  user: User | null;
  /** True while restoring a persisted session on first load. */
  initializing: boolean;
  login: (payload: LoginPayload) => Promise<void>;
  /** Creates the account and triggers a verification email; does NOT sign in. */
  register: (payload: RegisterPayload) => Promise<RegistrationResponse>;
  /** Confirms an email token and signs the user in. */
  verifyEmail: (token: string) => Promise<void>;
  resendVerification: (email: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(() => readToken());
  const [initializing, setInitializing] = useState(true);

  // On boot, revalidate any persisted token against the backend. A stale or
  // expired token yields 401, so we drop it and fall back to the landing page.
  useEffect(() => {
    let active = true;

    if (!token) {
      setInitializing(false);
      return;
    }

    fetchCurrentUser(token)
      .then((current) => {
        if (active) {
          setUser(current);
        }
      })
      .catch(() => {
        if (active) {
          clearToken();
          setToken(null);
          setUser(null);
        }
      })
      .finally(() => {
        if (active) {
          setInitializing(false);
        }
      });

    return () => {
      active = false;
    };
  }, [token]);

  const applySession = useCallback((response: AuthResponse) => {
    writeToken(response.accessToken);
    setToken(response.accessToken);
    setUser(response.user);
  }, []);

  const login = useCallback(
    async (payload: LoginPayload) => {
      applySession(await loginRequest(payload));
    },
    [applySession],
  );

  const register = useCallback(
    // Registration intentionally does not sign in: the user must verify their email first.
    (payload: RegisterPayload) => registerRequest(payload),
    [],
  );

  const verifyEmail = useCallback(
    async (token: string) => {
      applySession(await verifyEmailRequest(token));
    },
    [applySession],
  );

  const resendVerification = useCallback(
    (email: string) => resendVerificationRequest(email),
    [],
  );

  const logout = useCallback(() => {
    clearToken();
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, initializing, login, register, verifyEmail, resendVerification, logout }),
    [user, initializing, login, register, verifyEmail, resendVerification, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

export { ApiError };

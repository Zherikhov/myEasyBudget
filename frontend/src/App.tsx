import { Loader2 } from "lucide-react";
import { useCallback, useState } from "react";
import { useAuth } from "./auth/AuthContext";
import Dashboard from "./components/Dashboard";
import LandingPage from "./components/LandingPage";
import VerifyEmailPage from "./components/VerifyEmailPage";

const VERIFY_PATH = "/verify-email";

function App() {
  const { user, initializing } = useAuth();

  // Minimal client-side routing: the only deep link is the email verification page.
  const [path, setPath] = useState(() => window.location.pathname);

  const goHome = useCallback(() => {
    window.history.replaceState({}, "", "/");
    setPath("/");
  }, []);

  if (initializing) {
    return (
      <div className="app-loading" role="status" aria-live="polite">
        <Loader2 size={28} className="spin" aria-hidden="true" />
        <span>Loading…</span>
      </div>
    );
  }

  if (path === VERIFY_PATH) {
    const token = new URLSearchParams(window.location.search).get("token");
    return <VerifyEmailPage token={token} onDone={goHome} />;
  }

  return user ? <Dashboard user={user} /> : <LandingPage />;
}

export default App;

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, LOGGED_OUT_EVENT, tokenStore, unwrap, type Schemas } from "./api/client";

type Auth = {
  /** undefined while loading, null when nobody is logged in. */
  me: Schemas["Me"] | null | undefined;
  /** Text to show when the account could not be loaded. */
  error?: string;
  signIn: (tokens: Schemas["Tokens"]) => void;
  signOut: () => void;
  reload: () => void;
};

const AuthContext = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Schemas["Me"] | null | undefined>(tokenStore.access() ? undefined : null);
  const [error, setError] = useState<string>();

  const reload = useCallback(() => {
    if (!tokenStore.access()) {
      setMe(null);
      return;
    }
    setError(undefined);
    unwrap(api.GET("/me"))
      .then(setMe)
      .catch((e: Error) => {
        // Without a token the 401 handler has already logged the user out.
        if (tokenStore.access()) setError(e.message);
        else setMe(null);
      });
  }, []);

  useEffect(reload, [reload]);

  useEffect(() => {
    const onLoggedOut = () => setMe(null);
    window.addEventListener(LOGGED_OUT_EVENT, onLoggedOut);
    return () => window.removeEventListener(LOGGED_OUT_EVENT, onLoggedOut);
  }, []);

  const signIn = useCallback(
    (tokens: Schemas["Tokens"]) => {
      tokenStore.save(tokens);
      setMe(undefined);
      reload();
    },
    [reload],
  );

  const signOut = useCallback(async () => {
    const refreshToken = tokenStore.refresh();
    if (refreshToken) {
      try {
        await unwrap(api.POST("/auth/logout", { body: { refreshToken } }));
      } catch {
        // Leave the device even if the server is gone.
      }
    }
    tokenStore.clear();
    setMe(null);
  }, []);

  return <AuthContext.Provider value={{ me, error, signIn, signOut, reload }}>{children}</AuthContext.Provider>;
}

export function useAuth(): Auth {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("useAuth outside AuthProvider");
  return auth;
}

import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, unwrap } from "../api/client";

/** Opened from the invitation email: /invite?token=... */
export function InvitePage() {
  const [params] = useSearchParams();
  const token = params.get("token");
  const navigate = useNavigate();
  const [error, setError] = useState<string>();
  const sent = useRef(false);

  useEffect(() => {
    // The token works once, so it must not be sent twice (React runs effects twice in development).
    if (!token || sent.current) return;
    sent.current = true;
    unwrap(api.POST("/shares/accept", { body: { token } }))
      .then((share) => navigate(`/plans/${share.planId}`, { replace: true }))
      .catch((e: Error) => setError(e.message));
  }, [token, navigate]);

  return (
    <main>
      <h1>Приглашение</h1>
      {!token && <p className="error">В ссылке нет кода приглашения. Откройте ссылку из письма целиком.</p>}
      {token && !error && <p>Принимаем приглашение…</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <Link to="/">К планам</Link>
    </main>
  );
}

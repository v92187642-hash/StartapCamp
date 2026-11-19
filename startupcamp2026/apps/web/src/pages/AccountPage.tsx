import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth";

/** The account: what it is, and the way to delete it with all its data. */
export function AccountPage() {
  const { me, signOut } = useAuth();
  const [confirming, setConfirming] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function deleteAccount(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      await unwrap(api.POST("/me/delete", { body: { password } }));
      await signOut();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  async function logoutEverywhere() {
    setBusy(true);
    setError(undefined);
    try {
      await unwrap(api.POST("/auth/logout-all"));
    } catch {
      // This device still leaves; other devices stay only if the server was unreachable.
    }
    await signOut();
  }

  return (
    <main>
      <Link to="/">← Все планы</Link>
      <h1>Аккаунт</h1>
      <p>
        Почта: <strong>{me?.email}</strong>
      </p>
      <p>Подписка: {me?.premium ? "активна" : "нет"}</p>
      <p>
        <button className="secondary" onClick={logoutEverywhere} disabled={busy}>
          Выйти на всех устройствах
        </button>
      </p>

      <h2>Удаление аккаунта</h2>
      <p className="hint">
        Будут удалены ваш аккаунт, все ваши планы с отметками и вопросами, приглашения и ваш доступ к чужим планам.
        Восстановить данные будет нельзя.
      </p>
      {!confirming ? (
        <button className="secondary" onClick={() => setConfirming(true)}>
          Удалить аккаунт и все данные
        </button>
      ) : (
        <form onSubmit={deleteAccount}>
          <label>
            Пароль
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <div className="entry-actions">
            <button disabled={busy}>Да, удалить навсегда</button>
            <button type="button" className="secondary" onClick={() => setConfirming(false)} disabled={busy}>
              Отмена
            </button>
          </div>
        </form>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}

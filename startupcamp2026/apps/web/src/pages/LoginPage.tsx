import { useState, type FormEvent } from "react";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth";

const MIN_PASSWORD_LENGTH = 8;

/** Login with an email and a password, and registration of a new account. */
export function LoginPage() {
  const { signIn } = useAuth();
  const [registering, setRegistering] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeated, setRepeated] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (registering && password !== repeated) {
      setError("Пароли не совпадают");
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      const body = { email: email.trim(), password };
      signIn(await unwrap(api.POST(registering ? "/auth/register" : "/auth/login", { body })));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function switchMode() {
    setRegistering(!registering);
    setRepeated("");
    setError(undefined);
  }

  return (
    <main>
      <h1>Отметка</h1>
      <p className="lead">Дневник выполнения назначений врача</p>

      <h2>{registering ? "Регистрация" : "Вход"}</h2>
      <form onSubmit={submit}>
        <label>
          Электронная почта
          <input
            type="email"
            required
            autoFocus
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </label>
        <label>
          Пароль
          <input
            type="password"
            required
            minLength={registering ? MIN_PASSWORD_LENGTH : undefined}
            autoComplete={registering ? "new-password" : "current-password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        {registering && (
          <>
            <p className="hint">Не короче {MIN_PASSWORD_LENGTH} символов.</p>
            <label>
              Пароль ещё раз
              <input
                type="password"
                required
                autoComplete="new-password"
                value={repeated}
                onChange={(e) => setRepeated(e.target.value)}
              />
            </label>
          </>
        )}
        <button disabled={busy}>{registering ? "Зарегистрироваться" : "Войти"}</button>
        <button type="button" className="link" onClick={switchMode}>
          {registering ? "У меня уже есть аккаунт" : "Создать аккаунт"}
        </button>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}

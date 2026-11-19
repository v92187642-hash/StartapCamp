import { useState } from "react";
import { api, unwrap } from "../api/client";
import { useAuth } from "../auth";

/**
 * Version of the consent text below. Change it whenever the text changes: the
 * server stores which version each user accepted.
 *
 * The text is a draft. A lawyer must review it before launch (milestone M5).
 */
export const CONSENT_TEXT_VERSION = "2026-10";

/** Shown once, after the first login: nothing is stored before consent. */
export function ConsentPage() {
  const { reload, signOut } = useAuth();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function accept() {
    setBusy(true);
    setError(undefined);
    try {
      await unwrap(api.POST("/consents", { body: { kind: "personal_data", textVersion: CONSENT_TEXT_VERSION } }));
      reload();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <main>
      <h1>Согласие на обработку данных</h1>
      <p>Чтобы вести дневник, нам нужно ваше согласие на обработку персональных данных.</p>
      <ul>
        <li>Мы храним адрес вашей почты, ваши планы и отметки о выполнении назначений.</li>
        <li>Мы не храним названия лекарств, дозировки и диагнозы. Пожалуйста, не вводите их.</li>
        <li>Ваш дневник видят только вы и те, кого вы сами пригласили.</li>
        <li>Сервис не даёт медицинских советов. По всем вопросам лечения обращайтесь к врачу.</li>
        <li>Вы можете отозвать согласие и попросить удалить данные.</li>
      </ul>
      <button onClick={accept} disabled={busy}>
        Даю согласие
      </button>
      <button className="link" onClick={signOut}>
        Выйти
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}

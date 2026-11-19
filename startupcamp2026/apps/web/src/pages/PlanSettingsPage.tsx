import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { useLoad } from "../lib/useLoad";
import { EVERY_DAY, ItemFields, type ItemDraft } from "./ItemFields";

const DAY_NAMES = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];

/** Owner only: the plan's items and who may watch the plan. */
export function PlanSettingsPage() {
  const planId = useParams().planId!;
  const path = { params: { path: { planId } } };
  const plan = useLoad(() => unwrap(api.GET("/plans/{planId}", path)), [planId]);
  const shares = useLoad(() => unwrap(api.GET("/plans/{planId}/shares", path)), [planId]);

  const [item, setItem] = useState<ItemDraft>({ timeOfDay: "12:00", daysOfWeek: EVERY_DAY });
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState<string>();
  const [error, setError] = useState<string>();

  async function run(action: () => Promise<void>) {
    setError(undefined);
    setMessage(undefined);
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  function addItem(event: FormEvent) {
    event.preventDefault();
    if (item.daysOfWeek.length === 0) {
      setError("Выберите хотя бы один день недели");
      return;
    }
    run(async () => {
      const created = await unwrap(api.POST("/plans/{planId}/items", { ...path, body: item }));
      setMessage(`Добавлено: ${created.label}`);
      plan.reload();
    });
  }

  function invite(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      await unwrap(api.POST("/plans/{planId}/shares", { ...path, body: { email } }));
      setMessage(`Приглашение отправлено на ${email}. Ссылка действует 7 дней.`);
      setEmail("");
      shares.reload();
    });
  }

  function revoke(shareId: string) {
    run(async () => {
      await unwrap(api.DELETE("/shares/{shareId}", { params: { path: { shareId } } }));
      shares.reload();
    });
  }

  const loadError = plan.error ?? shares.error;
  return (
    <main>
      <Link to={`/plans/${planId}`}>← К дневнику</Link>
      <h1>{plan.data?.title ?? "План"}</h1>
      {loadError && (
        <p className="error" role="alert">
          {loadError}
        </p>
      )}
      {message && <p className="ok">{message}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <h2>Назначения</h2>
      <ul className="plain">
        {plan.data?.items?.map((it) => (
          <li key={it.id}>
            <strong>{it.label}</strong> — {it.timeOfDay},{" "}
            {it.daysOfWeek.length === 7 ? "каждый день" : it.daysOfWeek.map((d) => DAY_NAMES[d - 1]).join(", ")}
          </li>
        ))}
      </ul>
      <form onSubmit={addItem}>
        <h3>Добавить назначение</h3>
        <ItemFields value={item} onChange={setItem} />
        <button>Добавить</button>
      </form>

      <h2>Доступ близких</h2>
      <p className="hint">Близкий человек увидит ваш дневник, но не сможет ничего в нём менять.</p>
      <ul className="plain">
        {shares.data?.map((share) => (
          <li key={share.id}>
            {share.invitedEmail} — {share.status === "accepted" ? "видит дневник" : "приглашение отправлено"}{" "}
            <button className="link" onClick={() => revoke(share.id)}>
              {share.status === "accepted" ? "Закрыть доступ" : "Отменить"}
            </button>
          </li>
        ))}
      </ul>
      <form onSubmit={invite}>
        <label>
          Почта близкого человека
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <button>Пригласить</button>
      </form>
    </main>
  );
}

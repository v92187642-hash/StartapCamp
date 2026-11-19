import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { addDays, dateInZone } from "../lib/time";
import { EVERY_DAY, ItemFields, type ItemDraft } from "./ItemFields";

export function NewPlanPage() {
  const navigate = useNavigate();
  // The plan lives in the time zone of the device it was created on.
  const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  const today = dateInZone(new Date(), timezone);

  const [title, setTitle] = useState("");
  const [startsOn, setStartsOn] = useState(today);
  const [endsOn, setEndsOn] = useState(addDays(today, 13));
  const [items, setItems] = useState<ItemDraft[]>([{ timeOfDay: "08:00", daysOfWeek: EVERY_DAY }]);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function create(event: FormEvent) {
    event.preventDefault();
    if (items.some((item) => item.daysOfWeek.length === 0)) {
      setError("Выберите хотя бы один день недели для каждого назначения");
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      const plan = await unwrap(
        api.POST("/plans", { body: { title: title.trim() || undefined, startsOn, endsOn, timezone, items } }),
      );
      navigate(`/plans/${plan.id}`);
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }

  return (
    <main>
      <Link to="/">← Все планы</Link>
      <h1>Новый план</h1>
      <form onSubmit={create}>
        <label>
          Название (необязательно)
          <input value={title} maxLength={100} placeholder="План 1" onChange={(e) => setTitle(e.target.value)} />
        </label>
        <p className="hint">Не указывайте диагноз и названия лекарств.</p>
        <div className="row">
          <label>
            Начало
            <input type="date" required value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
          </label>
          <label>
            Окончание
            <input type="date" required min={startsOn} value={endsOn} onChange={(e) => setEndsOn(e.target.value)} />
          </label>
        </div>

        <h2>Назначения</h2>
        <p className="hint">
          Назначения называются по номерам, как в листе врача: «Назначение 1», «Назначение 2». Укажите, когда
          выполнять каждое.
        </p>
        {items.map((item, index) => (
          <section key={index} className="item">
            <h3>
              Назначение {index + 1}
              {items.length > 1 && index === items.length - 1 && (
                <button type="button" className="link" onClick={() => setItems(items.slice(0, -1))}>
                  Убрать
                </button>
              )}
            </h3>
            <ItemFields value={item} onChange={(value) => setItems(items.map((it, i) => (i === index ? value : it)))} />
          </section>
        ))}
        {items.length < 20 && (
          <button
            type="button"
            className="secondary"
            onClick={() => setItems([...items, { timeOfDay: "20:00", daysOfWeek: EVERY_DAY }])}
          >
            Добавить назначение
          </button>
        )}
        <button disabled={busy}>Создать план</button>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </main>
  );
}

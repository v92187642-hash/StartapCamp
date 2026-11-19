import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, unwrap, type Schemas } from "../api/client";
import { entryState } from "../lib/entries";
import { addDays, dateInZone, formatDay, timeInZone, zonedToInstant } from "../lib/time";
import { useLoad } from "../lib/useLoad";

type Entry = Schemas["Entry"];

/** The day view: every entry of one day, in one of three states. */
export function PlanPage() {
  const planId = useParams().planId!;
  const plan = useLoad(() => unwrap(api.GET("/plans/{planId}", { params: { path: { planId } } })), [planId]);
  const [date, setDate] = useState<string>();

  // Start on today's date in the plan's time zone, once the plan is known.
  const timezone = plan.data?.timezone;
  useEffect(() => {
    if (timezone) setDate((current) => current ?? dateInZone(new Date(), timezone));
  }, [timezone]);

  if (plan.error) {
    return (
      <main>
        <Link to="/">← Все планы</Link>
        <p className="error" role="alert">
          {plan.error}
        </p>
      </main>
    );
  }
  if (!plan.data || !date) return <main>Загрузка…</main>;

  const isOwner = plan.data.role === "owner";
  return (
    <main>
      <Link to="/">← Все планы</Link>
      <h1>{plan.data.title}</h1>
      {!isOwner && <p className="tag">Вы наблюдаете. Отмечать может только владелец плана.</p>}

      <nav className="day-nav" aria-label="Выбор дня">
        <button className="secondary" onClick={() => setDate(addDays(date, -1))} aria-label="Предыдущий день">
          ←
        </button>
        <strong>{formatDay(date)}</strong>
        <button className="secondary" onClick={() => setDate(addDays(date, 1))} aria-label="Следующий день">
          →
        </button>
      </nav>

      <Day planId={planId} date={date} timezone={plan.data.timezone} isOwner={isOwner} />

      <p>
        <Link to={`/plans/${planId}/questions`} className="button secondary">
          Вопросы врачу и отчёт
        </Link>
      </p>

      {isOwner && (
        <p>
          <Link to={`/plans/${planId}/settings`} className="button secondary">
            Назначения и доступ близких
          </Link>
        </p>
      )}
    </main>
  );
}

function Day(props: { planId: string; date: string; timezone: string; isOwner: boolean }) {
  const { planId, date, timezone, isOwner } = props;
  const entries = useLoad(
    () => unwrap(api.GET("/plans/{planId}/entries", { params: { path: { planId }, query: { date } } })),
    [planId, date],
  );
  const [error, setError] = useState<string>();

  async function change(action: Promise<unknown>) {
    setError(undefined);
    try {
      await action;
    } catch (e) {
      setError((e as Error).message);
    }
    entries.reload();
  }

  if (entries.error) {
    return (
      <p className="error" role="alert">
        {entries.error}
      </p>
    );
  }
  if (!entries.data) return <p>Загрузка…</p>;
  if (entries.data.length === 0) return <p>На этот день назначений нет.</p>;

  return (
    <>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <ul className="entries">
        {entries.data.map((entry) => (
          <EntryRow
            key={entry.id}
            entry={entry}
            date={date}
            timezone={timezone}
            isOwner={isOwner}
            onMark={(markedAt) =>
              change(
                unwrap(
                  api.PUT("/entries/{entryId}/mark", {
                    params: { path: { entryId: entry.id } },
                    body: markedAt ? { markedAt } : undefined,
                  }),
                ),
              )
            }
            onUndo={() => change(unwrap(api.DELETE("/entries/{entryId}/mark", { params: { path: { entryId: entry.id } } })))}
          />
        ))}
      </ul>
    </>
  );
}

const STATE_TEXT = { planned: "Запланировано", marked: "Отмечено", "no-mark": "Нет отметки" };

function EntryRow(props: {
  entry: Entry;
  date: string;
  timezone: string;
  isOwner: boolean;
  onMark: (markedAt?: string) => void;
  onUndo: () => void;
}) {
  const { entry, date, timezone, isOwner, onMark, onUndo } = props;
  const state = entryState(entry, new Date());
  const [correcting, setCorrecting] = useState(false);
  const [time, setTime] = useState("");

  function startCorrecting() {
    setTime(timeInZone(entry.markedAt!, timezone));
    setCorrecting(true);
  }

  function saveCorrection() {
    onMark(zonedToInstant(date, time, timezone).toISOString());
    setCorrecting(false);
  }

  return (
    <li className={`entry entry-${state}`}>
      <div className="entry-main">
        <span className="entry-time">{timeInZone(entry.scheduledAt, timezone)}</span>
        <span className="entry-label">{entry.label}</span>
        <span className="entry-state">
          {STATE_TEXT[state]}
          {entry.markedAt && ` в ${timeInZone(entry.markedAt, timezone)}`}
          {entry.correctedAt && (state === "marked" ? " (исправлено)" : " (отметка снята)")}
        </span>
      </div>
      {/* A missed entry offers a question for the doctor, never advice. */}
      {state === "no-mark" && (
        <Link
          to={`/plans/${entry.planId}/questions?about=${encodeURIComponent(
            `${entry.label}, ${formatDay(date)}, ${timeInZone(entry.scheduledAt, timezone)}: нет отметки. `,
          )}`}
        >
          Записать вопрос врачу
        </Link>
      )}
      {isOwner && !correcting && (
        <div className="entry-actions">
          {state !== "marked" && <button onClick={() => onMark()}>Отметить</button>}
          {state === "marked" && (
            <>
              <button className="secondary" onClick={startCorrecting}>
                Изменить время
              </button>
              <button className="secondary" onClick={onUndo}>
                Снять отметку
              </button>
            </>
          )}
        </div>
      )}
      {correcting && (
        <div className="entry-actions">
          <label>
            Время выполнения
            <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
          </label>
          <button onClick={saveCorrection} disabled={!time}>
            Сохранить
          </button>
          <button className="secondary" onClick={() => setCorrecting(false)}>
            Отмена
          </button>
        </div>
      )}
    </li>
  );
}

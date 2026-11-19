import { useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError, unwrap } from "../api/client";
import { formatDate } from "../lib/time";
import { useLoad } from "../lib/useLoad";

/**
 * Questions to take to the doctor, and the PDF report for the visit. The
 * product only keeps the list: it never answers a question or gives advice.
 */
export function QuestionsPage() {
  const planId = useParams().planId!;
  const [params] = useSearchParams();
  const path = { params: { path: { planId } } };
  const plan = useLoad(() => unwrap(api.GET("/plans/{planId}", path)), [planId]);
  const questions = useLoad(() => unwrap(api.GET("/plans/{planId}/questions", path)), [planId]);

  // A missed entry leads here with a draft: "Назначение 1, 9 октября, 08:00: нет отметки."
  const [text, setText] = useState(params.get("about") ?? "");
  const [error, setError] = useState<string>();
  const [downloading, setDownloading] = useState(false);

  async function run(action: () => Promise<unknown>) {
    setError(undefined);
    try {
      await action();
      questions.reload();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  function add(event: FormEvent) {
    event.preventDefault();
    run(async () => {
      await unwrap(api.POST("/plans/{planId}/questions", { ...path, body: { text } }));
      setText("");
    });
  }

  async function downloadReport() {
    setError(undefined);
    setDownloading(true);
    try {
      const pdf = await unwrap(api.GET("/plans/{planId}/report", { ...path, parseAs: "blob" }));
      // Hand the file to the browser as a download.
      const link = document.createElement("a");
      link.href = URL.createObjectURL(pdf as unknown as Blob);
      link.download = "otmetka-report.pdf";
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (e) {
      // A failed blob request has no parsed body, so the server's text is not available here.
      setError(
        e instanceof ApiError && e.status === 403
          ? "Отчёт для врача доступен по подписке"
          : (e as Error).message,
      );
    } finally {
      setDownloading(false);
    }
  }

  const isOwner = plan.data?.role === "owner";
  const loadError = plan.error ?? questions.error;
  return (
    <main>
      <Link to={`/plans/${planId}`}>← К дневнику</Link>
      <h1>Вопросы врачу</h1>
      <p className="hint">
        Запишите, о чём хотите спросить на приёме. Отметка не отвечает на вопросы и не даёт медицинских советов.
      </p>
      {loadError && (
        <p className="error" role="alert">
          {loadError}
        </p>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {questions.data?.length === 0 && <p>Пока вопросов нет.</p>}
      <ul className="plain">
        {questions.data?.map((question) => (
          <li key={question.id} className={`question ${question.askedAt ? "question-asked" : ""}`}>
            <span className="question-text">{question.text}</span>
            <span className="tag">
              Записан {formatDate(question.createdAt.slice(0, 10))}
              {question.askedAt ? ", задан" : ""}
            </span>
            <span className="entry-actions">
              {isOwner && !question.askedAt && (
                <button
                  className="secondary"
                  onClick={() =>
                    run(() =>
                      unwrap(api.PUT("/questions/{questionId}/asked", { params: { path: { questionId: question.id } } })),
                    )
                  }
                >
                  Задан
                </button>
              )}
              {isOwner && question.askedAt && (
                <button
                  className="secondary"
                  onClick={() =>
                    run(() =>
                      unwrap(
                        api.DELETE("/questions/{questionId}/asked", { params: { path: { questionId: question.id } } }),
                      ),
                    )
                  }
                >
                  Ещё не задан
                </button>
              )}
              {(isOwner || question.mine) && (
                <button
                  className="link"
                  onClick={() =>
                    run(() =>
                      unwrap(api.DELETE("/questions/{questionId}", { params: { path: { questionId: question.id } } })),
                    )
                  }
                >
                  Удалить
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>

      <form onSubmit={add}>
        <label>
          Новый вопрос
          <textarea required maxLength={500} rows={3} value={text} onChange={(e) => setText(e.target.value)} />
        </label>
        <button>Записать вопрос</button>
      </form>

      <h2>Отчёт для врача</h2>
      <p className="hint">Файл PDF: отметки по дням и список вопросов. Его можно распечатать или показать на приёме.</p>
      <button className="secondary" onClick={downloadReport} disabled={downloading}>
        {downloading ? "Готовим отчёт…" : "Скачать отчёт (PDF)"}
      </button>
    </main>
  );
}

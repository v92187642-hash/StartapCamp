import { Link } from "react-router-dom";
import { api, unwrap } from "../api/client";
import { formatDate } from "../lib/time";
import { useLoad } from "../lib/useLoad";

export function PlansPage() {
  const plans = useLoad(() => unwrap(api.GET("/plans")), []);

  return (
    <main>
      <h1>Планы</h1>
      {plans.error && (
        <p className="error" role="alert">
          {plans.error}
        </p>
      )}
      {plans.data?.length === 0 && <p>Пока нет ни одного плана. Создайте план по назначениям врача.</p>}
      <ul className="cards">
        {plans.data?.map((plan) => (
          <li key={plan.id}>
            <Link to={`/plans/${plan.id}`} className="card">
              <strong>{plan.title}</strong>
              <span>
                {formatDate(plan.startsOn)} — {formatDate(plan.endsOn)}
              </span>
              {plan.role === "viewer" && <span className="tag">Вы наблюдаете</span>}
            </Link>
          </li>
        ))}
      </ul>
      <Link to="/plans/new" className="button">
        Создать план
      </Link>
    </main>
  );
}

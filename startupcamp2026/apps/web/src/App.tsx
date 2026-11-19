import { Link, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth";
import { AccountPage } from "./pages/AccountPage";
import { ConsentPage } from "./pages/ConsentPage";
import { InvitePage } from "./pages/InvitePage";
import { LoginPage } from "./pages/LoginPage";
import { NewPlanPage } from "./pages/NewPlanPage";
import { PlanPage } from "./pages/PlanPage";
import { PlanSettingsPage } from "./pages/PlanSettingsPage";
import { PlansPage } from "./pages/PlansPage";
import { QuestionsPage } from "./pages/QuestionsPage";

/**
 * Three gates before any page: logged in, account loaded, consent given.
 * The address stays the same while the gates are shown, so an invitation link
 * opened by a new user still leads to the invitation after login.
 */
export function App() {
  const { me, error, signOut, reload } = useAuth();

  if (me === null) return <LoginPage />;
  if (me === undefined) {
    return (
      <main>
        {error ? (
          <>
            <p className="error" role="alert">
              {error}
            </p>
            <button onClick={reload}>Повторить</button>
          </>
        ) : (
          "Загрузка…"
        )}
      </main>
    );
  }
  if (!me.consentAccepted) return <ConsentPage />;

  return (
    <>
      <header>
        <Link to="/" className="brand">
          Отметка
        </Link>
        <span className="account">
          <Link to="/account">{me.email}</Link>
          <button className="link" onClick={signOut}>
            Выйти
          </button>
        </span>
      </header>
      <Routes>
        <Route path="/" element={<PlansPage />} />
        <Route path="/plans/new" element={<NewPlanPage />} />
        <Route path="/plans/:planId" element={<PlanPage />} />
        <Route path="/plans/:planId/settings" element={<PlanSettingsPage />} />
        <Route path="/plans/:planId/questions" element={<QuestionsPage />} />
        <Route path="/invite" element={<InvitePage />} />
        <Route path="/account" element={<AccountPage />} />
        <Route path="*" element={<main>Страница не найдена. <Link to="/">К планам</Link></main>} />
      </Routes>
    </>
  );
}

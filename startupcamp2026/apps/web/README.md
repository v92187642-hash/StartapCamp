# web

React, Vite, TypeScript. A deliberately small client: day view, marking,
sharing with a relative.

Owner: Claude (nobody on the team does frontend). Keep it simple enough that a
backend developer can change a screen.

## Run it alone

Needs Node 24 and `core` on http://localhost:8080 (start everything with the
compose file, or only `core` from its folder).

```
npm install
npm run dev
```

Open http://localhost:5173. The page says «Сервер доступен» when it reaches
`core`.

The browser always calls `/api/...`. In development Vite forwards that to
`core` (`vite.config.ts`); in Docker nginx does (`nginx.conf`). So `core` needs
no CORS setup.

## Test and build

```
npm test
npm run build
```

## The API client

`src/api/schema.d.ts` is generated from `api/openapi.yaml` by `npm run
generate`, which runs by itself before `dev`, `test` and `build`. The file is
not committed. Requests go through the typed client in `src/api/client.ts`:

```ts
const { data, error } = await api.GET("/plans/{planId}/entries", {
  params: { path: { planId }, query: { date: "2026-10-09" } },
});
```

A path, parameter or field that is not in the contract does not compile.
`client.ts` also adds the access token to every request and, when the server
answers 401, gets a new one with the refresh token and repeats the request.

## Pages

| Address | File | What it is |
|---|---|---|
| (gate) | `pages/LoginPage.tsx` | Email and password; registration |
| (gate) | `pages/ConsentPage.tsx` | Consent, once; its text has a version |
| `/` | `pages/PlansPage.tsx` | Own plans and plans one watches |
| `/plans/new` | `pages/NewPlanPage.tsx` | Dates and items (time and weekdays) |
| `/plans/:id` | `pages/PlanPage.tsx` | Day view: three states, mark, undo, correct the time |
| `/plans/:id/settings` | `pages/PlanSettingsPage.tsx` | Owner: add items, invite and remove relatives |
| `/plans/:id/questions` | `pages/QuestionsPage.tsx` | Questions for the doctor; report download |
| `/invite?token=` | `pages/InvitePage.tsx` | The link from the invitation email |
| `/account` | `pages/AccountPage.tsx` | Email, subscription, account deletion |

`App.tsx` shows login and consent before any address, without changing the
address, so an invitation link opened by a new user still works after login.

How a page is put together: `useLoad(() => unwrap(api.GET(...)), [deps])` for
data, `unwrap(api.POST(...))` inside an event handler for changes, and the
thrown error's `message` is Russian text ready to show. Times are always
shown in the plan's time zone (`lib/time.ts`), not the browser's.

## Rules for texts

Russian, plain, large. Never advice: a missed entry says «Нет отметки» and
offers «Записать вопрос врачу». Three entry states differ by words as well as
colour.

## Status

All M1, M3 and M4 screens exist. The tests cover time and state logic only;
the pages were checked by hand-written browser scenarios that are not in the
repository.

# State

Last updated: 2026-10-10 (GitLab CI via services, no host ports)

Update this file at the end of every work session. Keep it short: it describes
the present, not the history.

## Read this first

On 2026-10-09 the project owner asked Claude to build every stage it could
while the owner was away. That replaced the earlier "scaffold level 2" limit
(see `docs/decisions.md`). So `core`, `notifier`, `web` and `android` now
contain working code for M1 to M4 and parts of M5, written by Claude and not
yet read by the team. It is committed on `main` and pushed to GitLab:
`git@gitlab.com:saanchoo79/startupcamp2026.git`.

## In progress: first deploy; invitations blocked on email

The owner is deploying to a server reached only through Dokploy (no shell on
the host). The site address set in Dokploy is
`develop.chillvision.tamelaos.fun` (port 80 of `web`, Let's Encrypt).

Where it stands:

- Login no longer needs email (passwords, see below), so the site can be used
  on the server as it is. What still needs mail there is the invitation to a
  relative, so sharing does not work on the server yet.
- The hosting provider blocks outgoing mail. Measured from a container on the
  server: DNS works, `gitlab.com:443` is open, and the Yandex mail server is
  unreachable on 465 and 587 even by IPv4 address. So SMTP cannot be used
  there, with any mail provider.
- Two ways out, both open: ask the hosting provider's support to open 465 or
  587 (then SMTP works unchanged); or send through a mail service's HTTP API
  on port 443, which needs a new `Mailer` implementation in
  `services/core/.../common/MailConfig.java` next to the log and SMTP ones.
- The owner has an account at a mail-sending service that requires a verified
  sender domain. Its name and API are not known yet. Waiting for: the service
  name or a link to its API documentation, the verified domain, the sender
  address. An `API_KEY` the owner pasted earlier may belong to it.
- Compose files no longer publish host ports. Dokploy should map `web:80`.
  Bundled stack: `infra/docker-compose.yml`. Managed Postgres/Redis/RabbitMQ:
  `infra/docker-compose.external.yml`. `docker-compose.prod.yml` is still an
  overlay for mail, push and restart, without a Caddy proxy.
- The backup scripts and `set-premium.sh` need a shell on the host. On Dokploy
  they have to be replaced by its scheduled jobs and container terminal.

To get the six test accounts on the server, set `SEED_TEST_ACCOUNTS=true`
in Dokploy for the `core` service (the plain compose file already defaults
to `true`; `docker-compose.prod.yml` to `false`). Once created they stay,
whatever the setting says later. The owner will use them there for a long
time; before real users arrive, delete each from its account page, because
their passwords are public.

Next step for a new session: ask the owner for the mail service's name and
API documentation, then write the HTTP `Mailer`.

## Mail: switched off everywhere, on purpose

On 2026-10-09 the owner decided not to use the personal Yandex mailbox and to
wait for a proper mail service. `infra/.env` on the owner's machine no longer
has mail settings, so invitation links are written to the `core` log there.
Until mail is set up the owner works with the six test accounts. The
mailbox's app password was pasted into a chat earlier and should still be
revoked in Yandex.

For a phone there is `apps/android/otmetka-debug.apk` (ignored by git), built
for the server: `https://develop.chillvision.tamelaos.fun/api/`.

## What exists

- Contracts: `api/openapi.yaml` and `api/asyncapi.yaml`, covering everything
  below.
- `services/core`: registration and login by email and password, six test
  accounts behind a setting, tokens, consent, account deletion;
  plans and items; entries created ahead; day view; mark, correct, undo with
  history; invitations and read-only access; questions for the doctor; push
  token registration; premium gate; doctor report; five events.
- `services/notifier`: consumes the five events; schedule and mark state;
  alert poller; push providers (Firebase, RuStore, log) behind one interface;
  PDF rendering.
- `apps/web`: login and registration, consent, plans, new plan, day view with mark, undo and
  time correction, plan settings (items, relatives), invitation link,
  questions, report download, account page with deletion.
- `apps/android`: login and registration, consent, plans, new plan, day view,
  mark, undo and time correction, plan settings (items, relatives),
  questions, report download, account with deletion, offline marks with
  background sync, local reminders with a "mark" action,
  notification permission, battery-settings guide, large type.
- `infra`: compose for local work, a second file for a server, backup,
  restore and restore-test scripts, a script that sets the premium flag.
- Drafts for M5: `docs/deploy.md`, `docs/legal/`, `docs/rustore-listing.md`.
- CI workflow for `core`, `notifier`, `web`, and a `migrations` job
  (`infra/scripts/check-migrations.sh`: empty Postgres, roles, Flyway and
  Alembic twice). GitLab sidecars, no host ports, no Testcontainers in CI.
  Android is not in CI (SDK, licenses, generated client; emulator would
  need KVM). See `docs/ci.md`.

## Verified on 2026-10-09

- Tests: `core` 39, `notifier` 33, `web` 7, `android` 6 unit tests. All pass.
- Outbox against the real broker: with RabbitMQ stopped, a mark and a plan
  change succeeded and waited in `core.outbox`; after RabbitMQ started again
  both reached `notifier` within seconds and nothing was dead-lettered. The
  mark request took 0.03 s with the broker up and 0.01 s with it down.
- Password login against the running stack: each of the five test accounts
  logged in through the web client's `/api` and read its plans; a new address
  registered, was refused a second registration and logged in. In a headless
  browser: wrong password, login as a test account, registration with mismatched and
  then matching passwords leading to the consent page, and a taken address.
  A plan made by a test account was still there after `core` restarted with
  `SEED_TEST_ACCOUNTS=false` and again with `true`.
- The Android app on an Android 14 emulator against the local stack, as
  `testaccount2`: login; a new plan with two items (time and date dialogs,
  a weekday switched off) that opened its day view; an item added in plan
  settings; an invitation sent and cancelled; a missed entry led to a
  prefilled question, which was saved, marked as asked and deleted; the
  report was saved through the system file dialog and is a PDF; a mark, a
  time correction that reached `core` as `corrected`, a future time refused,
  an undo; the account screen; logout; registration of a new account,
  consent, and its deletion, after which its row was gone.
- Fresh start: with empty volumes, the one compose command built everything
  and all six containers became healthy.
- M1 in two real browser sessions (Playwright, headless Chromium): patient
  logs in, consents, creates a plan, invites a relative; the relative opens
  the emailed link, logs in and sees the same day read-only; a mark and an
  undo by the patient appear for the relative after a refresh.
- M3 and M4 in the browser against the running stack: an unmarked entry
  produced one alert in `notifier` after the grace period (push written to
  the log, delivery row stored); the missed entry led to a prefilled question
  for the doctor; a free account was refused the invitation and the report; a
  premium account downloaded the PDF and its text was checked.
- M2 on an Android 14 emulator: login, consent, plan list, day view; a
  reminder fired at the exact minute with the app in the background and again
  with its process killed; «Отметить» on the notification marked the entry on
  the server; a tap on the notification opened the plan; a mark made with no
  network showed «Ожидает отправки» and reached the server later with the
  time of the tap; undo; alarms were set again after an app update; the
  battery guide opened the system battery screen.
- Account deletion against the running stack: rows gone in `core`, and the
  plan's schedule and the user's device gone in `notifier`.
- Backup, restore into a throwaway database with equal row counts, and a real
  restore that rolled the running database back.
- Server settings (`docker-compose.prod.yml`) started locally with
  `DOMAIN=localhost`: only the proxy reachable, HTTPS answered, a login code
  was delivered to a test SMTP server.

## Not verified

- The reminder, offline-mark and battery-guide checks listed for M2 above
  were made with the earlier email-code login and not repeated since; so
  were the two-session M1 run and the server-settings run.
- On Android: a relative watching a shared plan, a refusal for a free
  account (report, invitation), rotating the phone in a form, and the forms
  on a small screen or with a Russian system language.
- Nothing has run on a real phone, including the APK built for the server. Xiaomi, Huawei and Samsung battery
  behaviour, which the battery guide is about, cannot be seen on an emulator.
  The phone restart path was exercised only through the app-update broadcast.
- No push has ever been sent to a real device. The Firebase and RuStore
  providers are written from documentation and tested against a fake HTTP
  server only.
- Email was sent through one real server (Yandex, port 465, to a Yandex
  address). STARTTLS on 587 and delivery to Mail.ru or Gmail are untried.
- CI has never had a green run. `.gitlab-ci.yml` is written for a project
   Runner tagged `otmetka` (GitLab services, see `docs/ci.md`): `core`,
   `notifier`, `web`, `migrations`. Socket/`extra_hosts` from the first
   attempt are unused. `.github/workflows/ci.yml` is unused while the
   remote is GitLab.
- A real server, domain and certificate.

## Milestones

| Milestone | State |
|---|---|
| M0 | Built. CI not yet run. |
| M1 | Built; its "done when" was met in two browser sessions. |
| M2 | Built; its "done when" needs a real device, met only on an emulator. |
| M3 | Server side and web built. A relative's phone cannot receive an alert yet: the Android app has no push SDK (needs a Firebase or RuStore project). |
| M4 | Report and premium gate built; "done when" met. No payment integration. |
| M5 | Backups done. Deploy, legal and store listing are drafts. Pharmacy links not started. |

## Next action

1. Read the code before building on it. Start with each part's `README.md`.
2. Finish the first deploy: see "In progress" above.
3. Run the Android app on real phones, at least one Xiaomi. Check the forms
   with a Russian system language and a small screen.
4. Answer the open decisions below.
5. Create a RuStore Push (and, if wanted, Firebase) project. Then add the
   push SDK to the Android app: obtain the token, call `POST /devices`, show
   the incoming alert.

## Open decisions

Resolve with the project owner, then move the answer to `docs/decisions.md`.
Until then the code follows the "Now" column.

| Decision | Now | Recommendation |
|---|---|---|
| Who must be premium to share a plan | The caller, so the patient who invites. The pitch says the relative pays, and a relative cannot be invited by a free patient. | Let a plan be shared when either side is premium: the patient may send one invitation for free, and the relative's access works only while the patient or the relative is premium. |
| Plan title is free text | Allowed, with a hint not to name a diagnosis or drug | Ask the lawyer (question 2 in `docs/legal/questions-for-lawyer.md`); switching to generated titles is a small change |
| Item labels are generated, not typed | «Назначение N» only | Keep until the lawyer answers; it is the strict reading of the "no drug names" limit |
| Payment provider | None | Decide between RuStore Pay and СБП when a merchant account exists |
| Alert grace period | 30 minutes, one alert per entry, none if overdue by more than 12 hours | Keep; revisit after real use |
| Hosting provider in Russia | None chosen | Any provider with Docker; see `docs/deploy.md` |
| Final product name | «Отметка» | Keep for the pitch. Before launch check the trademark register, the domain and RuStore search; the word also means a school grade, which may read as being judged. «Отмечено» is the suggested alternative if a change is needed. |

## Known issues

- A message that fails in `notifier` goes to the dead-letter queue and stays
  there until someone moves it back by hand.
- A `plan.changed` message lists every future entry of the plan; for the
  largest allowed plan (20 items, 366 days) that is several hundred kilobytes
  on every change.
- An expired invitation, a used refresh token and an old `processed_events`
  row are never cleaned up.
- Registration does not confirm the address. Anyone can register an address
  that is not theirs. Registrations from one IP are capped at ten per hour.
- No password reset and no password change. A forgotten password means a
  lost account.
- Ten wrong passwords lock an email out of login for 15 minutes, so a
  stranger who knows an address can keep its owner out.
- An account made by the old email-code login has no password and cannot log
  in or register again. One such account is in the local database.
- Access tokens live 15 minutes after logout; refresh tokens are revoked by
  «Выйти» and «Выйти на всех устройствах». Tokens in the web client are still
  in localStorage (Android uses Bearer).
- Nothing checks `core` against `api/openapi.yaml` automatically.
- Android: no app icon; no instrumented tests; the offline database is
  rebuilt (and unsent marks lost) on a schema change; the day view does not
  show that a mark was corrected; an invitation link cannot be opened in the
  app.
- The web client has no tests of its pages, only of its time and state logic;
  the browser scenarios used for verification are not in the repository.

## Environment notes

- An Android emulator does run on the owner's machine (KVM is available),
  headless. From inside it the address `10.0.2.2` did not reach the host in
  Claude's sandbox; `adb reverse tcp:8080 tcp:8080` with
  `-PapiBaseUrl=http://127.0.0.1:8080/` did.
- Docker Hub is sometimes unreachable from the owner's machine (IPv6 route).
  Retrying the pull works.
- The owner's machine sits behind a local proxy and its network treats mail
  ports inconsistently (465 and 587 each worked at one time and not at
  another). Do not use it to judge what a mail server accepts. `curl` to a
  local-network address needs `--noproxy '*'`.
- The owner's machine has JDK 17; the `core` Gradle build downloads JDK 21 by
  itself.
- A shell that has `INTERNAL_SECRET`, `JWT_SECRET` and the like exported
  changes how locally started services behave; the tests pin their own values.

# Отметка

A diary for following a doctor's prescriptions, shared between a patient and a
relative. Working name.

- Current status: `docs/state.md`
- Roadmap: `docs/plan.md`
- How it fits together: `docs/architecture.md`
- Why it is built this way: `docs/decisions.md`
- Product scope and legal limits: `docs/product.md`

## Run

Needs Docker. Copy `infra/.env.example` to `infra/.env`, fill the secrets,
then from the repository root:

```
docker compose -f infra/docker-compose.yml up --build
```

Compose does not publish ports. Point the platform proxy (Dokploy) at service
`web`, container port 80. Register with any email and a password.

Test accounts exist only if `SEED_TEST_ACCOUNTS=true`:
`testaccount1@otmetka.test` … `testaccount6@otmetka.test`, passwords
`otmetka-test-1` … `otmetka-test-6`. Leave that off on a reachable server.
Details: `infra/README.md`.

Each part's `README.md` says how to run and test it alone.

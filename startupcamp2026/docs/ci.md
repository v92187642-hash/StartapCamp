# GitLab CI

The repository is on GitLab. Push and merge-request pipelines are defined in
`.gitlab-ci.yml`. They run on a Runner on the owner's machine, not on
GitLab.com shared runners.

Jobs: `core` (`./gradlew build`), `notifier` (`uv run pytest`), `web`
(`npm test` and `npm run build`), `migrations` (empty Postgres, `init-roles.sh`,
Flyway and Alembic twice).

Postgres and Redis are GitLab **services**: extra containers on the same
private network as the job. The job talks to `postgres:5432` and `redis:6379`
inside that network. Nothing is published on the runner host. Tests set
`OTMETKA_TEST_DATABASE=service` and do not start Testcontainers. The Docker
socket is not required.

Locally, `./gradlew test` and `uv run pytest` still use Testcontainers.

## Register the Runner

On the machine that has Docker (the same one you develop on is fine):

1. In the GitLab project: **Settings → CI/CD → Runners → Create project
   runner**.
2. Operating system: Linux. Tags: **`otmetka`**. Pause untagged jobs, or
   jobs without the tag will sit forever.
3. Install and register:

```
curl -L https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh | sudo bash
sudo apt-get install gitlab-runner
sudo gitlab-runner register --url https://gitlab.com --token <token-from-the-ui>
```

Executor: `docker`. Default image: `docker:27` (unused; each job sets its
own).

4. Edit `/etc/gitlab-runner/config.toml`. Privileged mode and the host
   Docker socket are not needed. You can drop `extra_hosts` and the socket
   volume if you added them for the earlier Testcontainers setup:

```
concurrent = 2

[[runners]]
  name = "otmetka"
  url = "https://gitlab.com"
  token = "<kept by register>"
  executor = "docker"
  [runners.docker]
    image = "docker:27"
    privileged = false
    volumes = ["/cache"]
```

5. `sudo gitlab-runner restart`. The runner should show as online in GitLab.
6. Push a commit. **Build → Pipelines** should run four jobs tagged
   `otmetka`.

`concurrent = 2` is enough: `web` does not need a database; `core`,
`notifier` and `migrations` each get their own Postgres sidecar. Raise it
only if the machine has RAM to spare (Java + several Postgres images).

`migrations` runs `infra/scripts/check-migrations.sh`. That is not the
same as `core`/`notifier` tests: those talk to a database the app already
migrated. The extra job starts from an empty sidecar, applies
`init-roles.sh`, runs Flyway as role `core` and Alembic as role `notifier`
a second time (must be a no-op), checks the table lists, and fails if one
role can read the other's schema.

## Why there is no Android job

Unit tests (`./gradlew testDebugUnitTest`) do not need an emulator. What
they do need is a JDK 17 image plus the Android SDK for `compileSdk` 36
(cmdline-tools, platform, build-tools), accepted licenses, and a network
call to OpenAPI Generator because the Retrofit client is produced from
`api/openapi.yaml` at build time and is not in git. `apiBaseUrl` is a
required Gradle property. The first SDK install is large and slow; on a
Runner that already runs Java tests and Python jobs in parallel it fights
for RAM.

Instrumented tests (Compose UI, Room against a device, notification
actions) need an emulator: KVM on the host, nested virtualisation or a
system image in the job, several minutes per boot. That is the expensive
part, and the current Runner is a docker executor, not a privileged KVM
job.

Until someone wants to maintain SDK licenses and an emulator image on
that machine, Android stays a local `./gradlew` command.

## GitHub workflow

`.github/workflows/ci.yml` is the same jobs for GitHub Actions, still
using Testcontainers on the GitHub-hosted VM. The canonical remote is
GitLab, so that file is unused until a GitHub remote exists.

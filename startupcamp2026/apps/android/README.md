# android

Kotlin, Jetpack Compose, Gradle, min SDK 26. The main client: plans, day
view, marking, local reminders, offline marks, relatives, questions for the
doctor, the report, the account.

Owner: Java developers (Kotlin is new to them; it runs on the same JVM and
build tool).

## Run

Open this folder (`apps/android`) in Android Studio and press Run. Start the
backend first: `docker compose -f infra/docker-compose.yml up` from the
repository root. The screen says «Сервер доступен» when the app reaches
`core`.

From the command line, with the Android SDK installed and `ANDROID_HOME` set:

```
./gradlew assembleDebug     # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug      # build and install on a connected device
```

### Where the app looks for `core`

| Running on | Address | How |
|---|---|---|
| Emulator | `http://10.0.2.2:8080/` | The default (`gradle.properties`) |
| Phone on the same Wi-Fi | `http://<ip of your machine>:8080/` | `./gradlew installDebug -PapiBaseUrl=http://192.168.1.10:8080/` |

Plain HTTP is allowed in debug builds only (`app/src/debug/AndroidManifest.xml`).

If the emulator cannot reach `10.0.2.2`, forward the port through adb instead:

```
adb reverse tcp:8080 tcp:8080
./gradlew installDebug -PapiBaseUrl=http://127.0.0.1:8080/
```

Register in the app, or log in as a test account: `testaccount1@otmetka.test` with
the password `otmetka-test-1` (see the root `README.md`).

## The API client

Everything under `ru.otmetka.android.api` is generated from
`api/openapi.yaml` on every build, into `app/build/generated/openapi`. It is
not committed and must not be edited; change the contract instead. If Android
Studio shows the package in red, build the project once.

## Where things are

| Part | File |
|---|---|
| Long-lived objects | `OtmetkaApp.kt` |
| Networking, token on every request, refresh on 401 | `data/ApiFactory.kt`, `data/TokenStore.kt` |
| Login and consent | `data/SessionRepository.kt`, `ui/LoginScreen.kt`, `ui/ConsentScreen.kt` |
| Offline copy | `data/db/` (Room) |
| Plans and entries, marks made offline | `data/DiaryRepository.kt` |
| Relatives; questions and the report (online only) | `data/SharingRepository.kt`, `data/QuestionsRepository.kt` |
| Background sync | `data/SyncWorker.kt` (WorkManager) |
| Reminders | `reminders/ReminderScheduler.kt`, `ReminderReceiver.kt`, `BootReceiver.kt` |
| Which screen is shown | `ui/AppViewModel.kt`, `MainActivity.kt` |
| Screens | `ui/PlansScreen.kt`, `ui/NewPlanScreen.kt`, `ui/DayScreen.kt`, `ui/PlanSettingsScreen.kt`, `ui/QuestionsScreen.kt`, `ui/AccountScreen.kt`, `ui/BatteryGuideScreen.kt` |
| Time, date and weekday inputs shared by forms | `ui/Fields.kt` |
| Large type, big buttons | `ui/Theme.kt`, `ui/Common.kt` |
| Logic that needs no phone, with tests | `domain/EntryLogic.kt` |
| Texts (Russian) | `app/src/main/res/values/strings.xml` |

How it fits together: a screen reads only the phone's database, through a
ViewModel that exposes a `StateFlow`. A mark is written to the database at
once with a "pending" flag; `SyncWorker` sends it when there is a network,
with the time of the tap. After every sync, mark and reminder the alarms are
set again for the nearest unmarked entries of the user's own plans.

Only the diary works that way. The forms (new plan, plan settings,
questions, account) talk to `core` directly through a repository and show
the server's message when a request fails; they need a network.

For a new screen: add a case to `Screen` in `AppViewModel.kt` and to the
`when` in `MainActivity.kt`, write a ViewModel and a composable. Texts go
into `strings.xml`, never into code. Request failures are logged under the
tag `Otmetka`.

## Test

```
./gradlew testDebugUnitTest
```

Six tests of `domain/EntryLogic.kt`. Nothing tests the screens or the
database automatically.

## Status

M2 is built. On 2026-10-09 it was run on an Android 14 emulator: login,
consent, plans, day view, mark, undo, a reminder with the app closed, the
«Отметить» action, an offline mark that synced later, alarms set again after
an update. It has never run on a real phone; the battery guide in particular
describes Xiaomi, Huawei and Samsung settings from their documentation and
must be checked on those phones.

Later the same day the app got everything the web client has: registration
and password login, creating a plan, adding items, inviting a relative,
questions for the doctor, the PDF report, correcting the time of a mark,
account deletion. All of it was walked through on the emulator against the
local backend.

Not built: receiving alerts about a relative (needs a Firebase or RuStore
Push project: add the SDK, obtain the token, call `POST /devices`), and
opening an invitation link, which a relative does on the web. There is no
app icon and no release signing.

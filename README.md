# EchoFree

EchoFree is a text-first social feed. Readers can browse short posts without like or follower counts, switch to Explore for a randomized order, and publish up to 280 characters with an optional display name and up to five tags. It is a prototype for readers and writers who want discovery without popularity metrics.

## Run on Android

Use Android Studio, JDK 17 and Android SDK Platform 35. Open the `android` folder, let Gradle sync, and select an Android emulator with API 26 or later. The debug app connects to `http://10.0.2.2:3000/`, the emulator address for the host machine. Start one of the two backends below before opening the app.

For a disposable local walkthrough, run this from the repository root with Node.js:

```sh
node scripts/fixture-api.mjs
```

The fixture listens on host loopback. It starts with synthetic posts and stores new posts in memory until it stops.

For persistent posts, start a local MongoDB instance and configure the real API:

```sh
cd backend
npm ci
```

Copy `.env.example` to `.env`, set `MONGO_URI` for your local database, and run `npm start`. Use port 3000 with the default emulator configuration. The API does not implement accounts, authentication, moderation, or offline delivery. Keep it in a local development environment.

On Windows, build the debug APK and run Android unit tests with:

```powershell
cd android
./gradlew.bat assembleDebug testDebugUnitTest
```

Use `./gradlew` with the same tasks on macOS or Linux. The output is `android/app/build/outputs/apk/debug/app-debug.apk` from the repository root. The checked-in debug APK and emulator evidence are described in the [native screen index](docs/verification/SCREEN-INDEX.md).

## Browser demo

Open the [phone view](https://echofree-android-demo.vercel.app/?view=phone) to present the app by itself, or use the [portfolio view](https://echofree-android-demo.vercel.app/) for context. Both support Feed, Explore, Refresh and Publish. The preview uses synthetic data and simulated publishing. Posts added there are local to the open tab and reset on reload.

## API and ranking

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `/health` | None | `{ status: "ok" }` |
| GET | `/posts` | Optional `limit`, `offset` and comma-separated `tags` | `{ posts, total }` |
| POST | `/posts` | `{ content, tags, authorName }` | Created post, HTTP 201 |

Both post responses use epoch milliseconds for `createdAt`. The server ranks a recent candidate pool using freshness and topic diversity. It prefers a cap per topic and backfills when there are too few alternatives. Android reranks the returned posts with 45% freshness, 25% topic diversity and 30% random noise by default. Explore uses random ordering within the posts the API returned. The Android app loads up to 80 posts and can pull to refresh.

Run the backend ranking and controller tests with `npm test` from `backend`. See [verification](docs/verification/SCREEN-INDEX.md) for the native steps and test limits.

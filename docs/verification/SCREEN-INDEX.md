# EchoFree verification

These images were captured from the EchoFree debug APK on an Android 15 (API 35) emulator at 1080 × 2340. The walkthrough used a local HTTP fixture with synthetic posts and controlled responses.

| Evidence | Screen or state | How to reach it |
| --- | --- | --- |
| [01](native/01-feed-error.png) | Feed error | Launch while the fixture returns HTTP 503 for the feed. |
| [02](native/02-feed-empty.png) | Empty feed | Restore the fixture, then tap **Try again** with no posts. |
| [03](native/03-feed.png) | Populated feed | Seed three synthetic posts and pull down to refresh. |
| [04](native/04-explore.png) | Explore | Select **Explore** to shuffle the same posts. |
| [05](native/05-compose.png) | New post | Tap **Write a post**. Publish starts disabled. The close button returns to the feed. |
| [06](native/06-compose-draft.png) | Draft | Enter synthetic text, then dismiss the keyboard. |
| [07](native/07-compose-error.png) | Publish error | Return HTTP 503 for publish. The composer stays open and retains the draft. |
| [08](native/08-published.png) | Published post | Restore publishing and retry. The app returns to the feed and shows the new anonymous post. |

## Reproduce

Build the APK with JDK 17 and Android SDK Platform 35. From the repository root on Windows:

```powershell
./android/gradlew.bat -p android assembleDebug testDebugUnitTest
node scripts/verify-native.mjs emulator-5554
```

Set `ANDROID_HOME` to the installed SDK path and pass the serial shown by `adb devices`. Port 3000 must be free because the script starts its own fixture. The script refuses an existing EchoFree install unless you pass `--reset-synthetic` for a disposable test install. That flag clears EchoFree data on the selected emulator.

For a manual walkthrough, start `node scripts/fixture-api.mjs` and install the [tested debug APK](../../artifacts/EchoFree-debug.apk) on the emulator. The fixture binds to `127.0.0.1:3000` on the host. Check the [APK SHA-256](../../artifacts/EchoFree-debug.apk.sha256) before installing.

## Results and limits

- Android debug build and 13 JVM tests passed. The APK signature verified with APK Signature Scheme v2.
- The emulator walkthrough checked retry, pull to refresh, Explore, close, draft validation, publish rejection, and one successful anonymous post after retry.
- All 15 backend Jest tests passed, including the API timestamp contract and malformed inputs. The native test used the HTTP fixture, and the API tests substituted persistence. A live MongoDB service and physical devices remain unverified.
- The [public phone demo](https://echofree-android-demo.vercel.app/?view=phone) uses synthetic data and simulated publishing. Headless Edge verified the responsive layout and interactions at 320, 390, 700 and 1280 pixels.
- The debug APK uses the emulator host address. No production API or release signing configuration is supplied. If a response is lost after the server stores a post, the app cannot determine whether publication succeeded.

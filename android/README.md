# K.N.O.W. — Android app

A single-Activity WebView shell around the real web app
(`https://teach.bash-ke.workers.dev`, the same Cloudflare Worker `../frontend`
deploys to). This makes the app visually identical to the website by
construction — it *is* the website, running inside an Android app frame —
rather than a hand-recreated native UI that can drift from the site's actual
CSS.

(An earlier version of this app was a native Jetpack Compose recreation of
each page. It's been replaced with this WebView approach for exact visual
parity; see git history if the native version is ever wanted back as a
starting point.)

## How it works

`MainActivity.kt` is the entire app:
- Loads `https://teach.bash-ke.workers.dev/` in a `WebView` with JS and DOM
  storage enabled (the web app's `Session` handling in `js/api.js` uses
  `localStorage`).
- `shouldOverrideUrlLoading` keeps navigation to the app's own hosts (the
  Cloudflare Worker frontend and the Render backend) inside the WebView;
  anything else — like the GitHub APK download link on the login/settings
  pages — opens in the system browser instead.
- `onShowFileChooser` backs `<input type="file">` (stock item photos, post
  photos, institution/portal icon uploads) via the system file picker.
- `setDownloadListener` sends binary downloads (e.g. tapping "Download
  Android app preview" from inside the app) to the system browser/Download
  Manager rather than trying to render them.
- The Android back button navigates WebView history before exiting the app.
- Third-party cookies are explicitly allowed (`CookieManager.setAcceptThirdPartyCookies`),
  matching the frontend's cross-origin (Worker → Render) fetches.

## First-time setup

1. Open this `android/` folder directly in Android Studio (Koala or newer) —
   **not** the repo root. Android Studio will detect there's no Gradle
   wrapper jar and offer to regenerate it automatically; accept that.
   (It wasn't committed because generating it requires downloading Gradle,
   which this environment's network doesn't have access to.)
2. Let Gradle sync — this module is intentionally light on dependencies
   (`androidx.core`, `androidx.activity` only) since there's no UI framework
   or networking layer to configure — sync should be quick.
3. Run on an emulator (API 26+) or a physical device with internet access.

## CI

`.github/workflows/android-apk.yml` builds the debug APK on every push to
`android/**` and republishes it to the `android-latest` GitHub release —
the stable link the website's download buttons point to.

## Changing the app

Since the app just points at a URL, most changes belong in `../frontend`
(HTML/CSS/JS), not here — edit the web app and it shows up in the Android
app automatically on next load, no rebuild needed. Reasons to touch this
module specifically: changing the loaded URL, the app icon/name, permissions,
or the file-picker/download/cookie handling in `MainActivity.kt`.

# K.N.O.W. — Android app (Kotlin, Jetpack Compose)

Native Android client for the same backend the web app (`../frontend`) talks
to: `https://teach-backend-roza.onrender.com`. Unlike the web app, this uses
the backend's Bearer-token auth path rather than cookies, so there's no CSRF
handling needed here.

## First-time setup

1. Open this `android/` folder directly in Android Studio (Koala or newer) —
   **not** the repo root. Android Studio will detect there's no Gradle
   wrapper jar and offer to regenerate it automatically; accept that.
   (It wasn't committed because generating it requires downloading Gradle,
   which this environment's network doesn't have access to.)
2. Let Gradle sync — it'll pull the AGP/Kotlin/Compose versions pinned in
   `build.gradle.kts` / `app/build.gradle.kts`.
3. Run on an emulator (API 26+) or a physical device with internet access.

## Structure

```
app/src/main/java/com/knowapp/android/
  KnowApplication.kt        — builds the one AppContainer on startup
  AppContainer.kt           — manual DI: session store, API service, repositories
  MainActivity.kt           — single Activity, hosts Compose navigation
  data/
    SessionStore.kt         — EncryptedSharedPreferences-backed session (token + role/institution)
    model/Models.kt         — API response models (kotlinx.serialization)
    network/                — Retrofit ApiService + OkHttp/auth-header setup
    repository/             — AuthRepository, TransactionRepository
  ui/
    theme/                  — Compose MaterialTheme, matches the web app's teal brand
    navigation/              — NavHost + routes
    login/, home/, transactions/  — one screen + ViewModel per feature
```

## Visual fidelity to the web app

`ui/theme/` (Color.kt, Shape.kt, Type.kt, Theme.kt) is a direct port of
`frontend/css/styles.css`'s `:root` design tokens — brand teal, card radius
(18dp), small radius (12dp), pill shape (999px), the income/expense green
`--in`/orange `--out` colors, and light/dark surface tokens. `ui/components/`
(AppCard, Badge, KpiRow, AmountText) mirror the web app's `.card`, `.badge`,
`.kpi-row`, and `.txn-amount` styling so screens use the same visual
language instead of generic Material defaults. Fonts (Plus Jakarta Sans /
Space Grotesk) default to the system font for now — see the TODO block at
the top of `ui/theme/Type.kt` for the one-minute Android Studio step to add
the real ones via the Resource Manager font picker.

## What's built so far

- **Login** — `POST /api/auth/login` (form-encoded, matches the backend's
  `OAuth2PasswordRequestForm`), session persisted locally, auto-navigates to
  Home if already signed in.
- **Home** — shows the signed-in user's name/role as a pill badge, links to
  Transactions, sign out (calls `POST /api/auth/logout` then clears local
  session).
- **Transactions** — `GET /api/transactions`, scoped server-side to the
  user's institution automatically (same as the web app), rendered as
  category-badged cards with green/orange amounts.

## Deliberately not yet built

Stock, Audits, Posts/News, Market Analysis (super admin), Institutions/
Accounts management (super admin) — same shape as the above (repository +
ViewModel + screen, styled with the same `ui/theme`/`ui/components` tokens),
not yet wired up. Add them the same way as
Transactions: a model in `data/model`, an endpoint in `ApiService`, a
repository, a ViewModel, a screen, a route in `KnowNavGraph.kt`.

## Notes

- Render's free tier cold-starts after inactivity — OkHttp timeouts are set
  to 45s to accommodate a first request waking the backend up.
- No Hilt/kapt — dependencies are wired manually via `AppContainer` for a
  simpler first build. Worth revisiting if the screen count grows a lot.
- `applicationId` is `com.knowapp.android` — a placeholder; rename it
  (in `app/build.gradle.kts` and the `java/com/knowapp/android` package
  path) before a real Play Store listing.

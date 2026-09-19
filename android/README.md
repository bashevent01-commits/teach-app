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
- **Stock** — `GET /api/stock`, category badge + unit price/quantity as a
  KPI row per item. Hidden from teacher-type staff on Home (server still
  enforces this — see `require_stock_access` — this is just matching UX).
- **News** — `GET /api/posts`, post cards with title/body and an optional
  photo (Coil `AsyncImage`, same `image_path`-resolves-to-full-URL logic as
  `Api.posts.imageUrl` in `frontend/js/api.js`).
- **Audits** — `GET/POST /api/audits`, `POST /api/audits/{id}/finalize`,
  `GET /api/audits/{id}/transactions`. Only staff can submit (server-enforced
  in `create_audit`) — the "+" FAB is hidden otherwise. institution_admin/
  super_admin must pass `institution_id` (server 400s without it); Finalize
  is only shown when the viewer is the original submitter, matching the
  server's actual permission check rather than just hiding on status.
- **Institutions** (super admin) — `GET/POST /api/institutions`, region
  picker uses the same 47-county list as `frontend/js/ui.js`'s
  `KENYA_COUNTIES` (ported to `data/KenyaCounties.kt`).
- **Accounts** (institution_admin + super admin) — `GET/POST /api/users`,
  deactivate/reactivate. Institution picker in the create form only shows
  for super_admin — an institution_admin's new accounts are silently pinned
  to their own institution server-side, so the field would be misleading.
- **Market** (super admin) — `GET /api/market-analysis/categories` +
  `/{id}` detail with a hand-rolled Canvas trend chart (no external charting
  library — one line didn't justify the dependency/version risk) and the
  regional breakdown table. Category management (add/delete) via
  `/api/product-categories` in a settings-icon dialog on the list screen.

## Deliberately not yet built

Nothing — every screen in the web app (`frontend/*.html`) now has an Android
equivalent. Two knowingly-simplified spots, if you want to close the gap
further:
- Real fonts aren't wired in yet (see `ui/theme/Type.kt`'s TODO).
- The Market trend chart is a basic filled-line Canvas draw, not a full
  interactive chart (tooltips, zoom) — fine for at-a-glance trend reading,
  less capable than Chart.js on the web page.

To add a new screen beyond what the web app has: a model in `data/model`, an
endpoint in `ApiService`, a
repository, a ViewModel, a screen, a route in `KnowNavGraph.kt`.

## Notes

- Render's free tier cold-starts after inactivity — OkHttp timeouts are set
  to 45s to accommodate a first request waking the backend up.
- No Hilt/kapt — dependencies are wired manually via `AppContainer` for a
  simpler first build. Worth revisiting if the screen count grows a lot.
- `applicationId` is `com.knowapp.android` — a placeholder; rename it
  (in `app/build.gradle.kts` and the `java/com/knowapp/android` package
  path) before a real Play Store listing.

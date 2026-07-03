# PROJECT KNOWLEDGE BASE — DebtBro

**Generated:** 2026-05-19
**Commit:** `efb066f`
**Branch:** `fix-google-signin-and-export-crash-15964286147323028907`

## OVERVIEW

DebtBro — Android (Kotlin/Jetpack Compose) app for tracking personal debts with AI-powered "roast" reminders, Firebase sync, expense splitting, and exportable shame cards. Single-module Gradle project.

## STRUCTURE

```
app/src/main/java/com/dhanuk/debtbro/
├── DebtBroApp.kt          # Application class: init Ads, OneSignal, WorkManager
├── MainActivity.kt         # Single Activity, edge-to-edge, theme from DataStore
├── data/
│   ├── ads/AdManager.kt    # Interstitial + Rewarded Ad lifecycle
│   ├── datastore/          # DataStore Preferences (all user prefs)
│   ├── db/                 # Room DB: DebtEntity, PaymentEntity, SplitEntity + DAOs
│   ├── firebase/           # AuthManager, FirebaseRepository, RealTimeSyncManager, SyncManager
│   ├── network/            # GeminiApiService (Retrofit)
│   └── repository/         # DebtRepository, PaymentRepository, SplitRepository, AiRepository
├── di/                     # Hilt modules: DatabaseModule, DataStoreModule, NetworkModule, RepositoryModule
├── presentation/
│   ├── components/         # Reusable composables: DebtCard, ConfettiOverlay, GoogleSignInCard, etc.
│   ├── navigation/         # NavGraph (bottom tabs: Home/Debts/Split/Stats) + Screen sealed class
│   ├── screens/            # 8 screens: onboarding, dashboard, debtlist, debtdetail, adddebt, analytics, settings, split
│   └── theme/              # Dark-first theme: PrimaryGreen(#00E5A0), DangerRed, Amber
├── util/
│   ├── CanvasExporter.kt   # Custom Canvas-based image export
│   ├── CsvExporter.kt      # CSV export utility
│   ├── HtmlExporter.kt     # WebView-rendered HTML template export (4 themes)
│   ├── CurrencyFormatter.kt
│   ├── DateUtils.kt
│   ├── LocalizedString.kt  # 18-language i18n (inline strings, ~800 lines)
│   └── ShareUtils.kt
└── worker/
    ├── DebtReminderWorker.kt   # Daily notification
    └── WeeklySummaryWorker.kt  # Weekly summary
```

## WHERE TO LOOK

| Task | Location |
|------|----------|
| Add a screen | `presentation/screens/` — create subdir w/ `*Screen.kt` + `*ViewModel.kt`, register in `NavGraph.kt` + `Screen.kt` |
| Add a DB entity | `data/db/entity/` + `data/db/dao/` + `DebtBroDB.kt` |
| Add a network API | `data/network/` + `di/NetworkModule.kt` |
| Change AI prompts | `data/repository/AiRepository.kt` — system prompts for 3 roast levels (MILD/SPICY/MEDIUM) |
| Change i18n strings | `util/LocalizedString.kt` — `get(code)` returns per-language string map |
| Add export format | `util/` — follow HtmlExporter or CsvExporter patterns |
| Add a pref | `data/datastore/AppPreferences.kt` — add key to `Keys` object + Flow + setter |

## CONVENTIONS

- **Architecture**: MVVM + Repository + Hilt DI. Screens own `@HiltViewModel`, injected repos. Data flows: Room → Repository → ViewModel → Composable.
- **Naming**: `*Screen.kt` (composable) + `*ViewModel.kt` per feature. `*Entity.kt` for Room models. `*Dao.kt` for DAOs.
- **DI**: Hilt `@Singleton` repositories, `@Module @InstallIn(SingletonComponent::class)` for bindings. `@AndroidEntryPoint` Activity, `@HiltAndroidApp` Application.
- **State**: `StateFlow` + `collectAsStateWithLifecycle()` in composables. `combine()` for multi-source state.
- **Error handling**: `runCatching {}` in repos. `Result<T>` return types. No empty catch blocks — but some catch `(_: Exception)` exist.
- **Theme**: Dark-first with PrimaryGreen(#00E5A0). Entire app background = `Color(0xFF0D0D0D)`. Cards = `0xFF1E1E1E` / `0xFF222222`.
- **DB sync**: `isSynced` flag on entities. Firestore push on create/update, pull on full sync. Real-time listener via Firestore snapshots.

## ANTI-PATTERNS (THIS PROJECT)

- **Firebase config**: Must recreate `google-services.json` from CI secrets on each build. Not checked into repo.
- **AdMob test IDs**: Hardcoded fallbacks in `AdManager.kt` for development (ca-app-pub-3940256099942544/*). Real IDs from `BuildConfig`.
- **Gemini API key**: Falls back to `BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE` (CI secret `GEMINI_API_KEY_2_5_FLASH_LITE`) if the user hasn't set one in Settings. User-pasted keys are stored encrypted via `SecureStorage` (EncryptedSharedPreferences); a legacy plaintext DataStore slot is migrated on first read. Key in `local.properties` at build time as `GEMINI_API_KEY` (legacy) or `GEMINI_API_KEY_2_5_FLASH_LITE`.
- **No tests**: Project has zero test files. (`test/` dir doesn't exist.)
- **AI rate limiting**: `AiRepository` has a 1s cooldown mutex. Daily regeneration cap = 5 free.
- **Hardcoded `LocalizedString`: i18n is a giant object with inline string maps, not Android `strings.xml` resources. 18 languages.

## UNIQUE STYLES

- **"Roast" feature**: AI generates WhatsApp-style nag messages in Hinglish/mixed languages. Three levels: MILD, MEDIUM, SPICY. Uses Gemini (gemini-2.5-flash-lite by default, with gemini-2.5-flash + gemini-flash-lite-latest as fallbacks).
- **Export cards**: 4 HTML templates in `assets/html_templates/` rendered via WebView → Bitmap → share sheet. Templates: cyberpunk_debt, elegant_minimal, insta_vibe, wall_of_shame.
- **Bottom sheet for AddDebt**: Not a full screen — `AddDebtBottomSheet` shown as modal from any tab.
- **Dark-first**: Light theme exists but app defaults to dark. No color inversion issues expected.
- **Deep links**: `debtbro://debt/{debtId}` supported for direct navigation.

## COMMANDS

```bash
# Build release APK
./gradlew assembleRelease --stacktrace

# Build debug APK  
./gradlew assembleDebug
```

## NOTES

- Requires JDK 17, Android SDK 35.
- CI builds signed APK on every push to any branch (`build.yml`). Creates GitHub Release only on `main`.
- Secrets in CI: `KEYSTORE_BASE64`, `GOOGLE_SERVICES_JSON`, `GEMINI_API_KEY_2_5_FLASH_LITE`, AdMob/OneSignal keys, keystore passwords.
- `keystore.jks` decoded from CI secret. Never commit `.jks` or `google-services.json`.
- Gradle version catalog at `gradle/libs.versions.toml` — single source of truth for dependency versions.
- Room DB version = 1 (no migrations yet). Schema export disabled.

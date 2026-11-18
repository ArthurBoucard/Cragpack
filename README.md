# Cragpack

Collaborative, local-first trip planner for climbers. Kotlin Multiplatform + Compose Multiplatform, Android and iOS.

The product spec is in [docs/](docs/) (start with [docs/00-handoff.md](docs/00-handoff.md)); the design reference is in [design/](design/).

## Setup

**Android (Windows, macOS or Linux)**
1. Install [Android Studio](https://developer.android.com/studio) (it brings the JDK and Android SDK) and the *Kotlin Multiplatform* plugin.
2. Open this folder and let Gradle sync.
3. Run the `androidApp` configuration on an emulator or phone.

**iOS (needs a Mac)**
1. Install Xcode, a JDK 21, and `brew install xcodegen`.
2. `cd iosApp && xcodegen` creates `iosApp.xcodeproj` (generated, not committed).
3. Open it in Xcode, set your signing team, run. Xcode builds the Kotlin framework itself.

Without a Mac, CI's `ios` job builds the iOS app on every push.

**Checks:** `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`

## Structure

```
androidApp/     Android shell: MainActivity only
iosApp/         iOS shell: XcodeGen spec + one Swift file
designsystem/   tokens (generated), fonts, Lucide icons, Ct* components, Catalog screen
shared/         the app: domain, data, features, App() root
design/         handoff reference: screens, tokens JSON, grades JSON, icon SVGs
docs/           handoff spec: brief, decisions, screens, data model, tech spec, build plan
tools/          lucide-to-vector.mjs (SVG icons -> Compose vectors + LucideIcon enum)
```

Inside `shared/src/commonMain/kotlin/app/cragpack/`:

```
domain/      pure Kotlin: entities and rules (grades, trip timing, formatting). No framework imports.
data/        AppGraph (wiring), repositories over SQLDelight; sync with Supabase arrives in Phase 2
feature/x/   one file per screen: its ViewModel and UI state at the top, the composables below
```

The schema lives in `shared/src/commonMain/sqldelight/`. Until the first release there are no migrations:
after a schema change, clear the app's data (or reinstall).

Supabase keys go in `local.properties` (never committed): `supabase.url=…` and `supabase.key=…` (publishable key only).

## Rules

- **Dependencies point inward:** `feature -> domain <- data`. Screens read the local database only, never the network.
- **Only design-system pieces in screens.** Need a new colour, size or text style? Add it to `design/tokens/cragpack.tokens.json` first; `:designsystem:generateTokens` turns it into Kotlin on build.
- **Grades come from `design/data/grades.json`** via the generated `GradeTable`. Never hard-code a grade list.
- **New icon:** drop the Lucide SVG in `design/icons/svg/`, run `node tools/lucide-to-vector.mjs`.
- **Platform code** (`androidMain`/`iosMain`) only for what common code can't do: background downloads, share sheet, video compression.
- **Logic gets a test in `commonTest`**; it runs on the JVM and on iOS.
- Modules stay few. Split a feature into its own module when build time or team size calls for it, not before.

## Versions

Pinned in [gradle/libs.versions.toml](gradle/libs.versions.toml): Kotlin 2.4.21, Compose Multiplatform 1.12.2, AGP 9.5.0, Gradle 9.8.2.

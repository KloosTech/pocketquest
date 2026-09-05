# 46 — First Android build target

## What

A real, installable Android app: a new `:androidApp` module (desktop `:app`
untouched), a minimal top-level nav stack in `:ui`, and Splash/Settings
screens. Confirmed working end-to-end on a physical device: cold start →
Splash → Character Creation → Hub/run → real battle screen, sprites and
fog-of-war rendering correctly, Room persistence surviving app restarts.

## Navigation

`ui/commonMain/nav/Nav.kt`: `Screen` (`Splash`/`CharacterCreation`/`Hub`/
`Settings`/`InRun`) + `NavController`, a plain push/pop `List<Screen>`
stack. `setRoot` replaces only the ROOT entry, leaving anything pushed on
top (Settings) undisturbed — a domain-state change (a run starting) doesn't
yank the player off Settings mid-view.

`RunScreen`'s own internal branching (loot reveal / node choice / per-
`NodeType` content) deliberately stays outside the stack — domain-state-
driven, not "navigation": a run is inherently linear, there's no "back" to
give it.

`PlatformBackHandler` expect/actual (`ui/{common,android,desktop,ios}Main`)
— a real `androidx.activity.compose.BackHandler` on Android, a no-op
everywhere else (desktop has no back button; iOS's own edge-swipe isn't
wired up yet).

## Splash / Settings

`SplashScreen` replaces the old bare "Loading…" text while `RunApp` loads
`MetaState`/`RunState`. `SettingsScreen` is a stub ("Nothing to configure
yet") — no persisted-settings mechanism or audio system exists anywhere in
the codebase yet; this just claims the nav slot so real settings can land
later without another navigation change. Reachable via a "Settings" button
on `HubScreen`.

## Catalog bundling

Desktop's `Main.kt` deliberately reads `content/catalog.json` live off disk
(`:designer`'s hot-edit-without-rebuild workflow) — unchanged. An installed
Android app has no live filesystem to read from, so `:ui`'s own
`build.gradle.kts` copies `content/catalog.json` into
`composeResources/files/catalog.json` on every Gradle configuration pass (a
plain file copy, not a dedicated task — Compose Multiplatform's resource-
processing task names are per-target and shift across versions, more
fragile to hook than just always re-copying one small file). `:ui/commonMain
/assets/CatalogAsset.kt`'s `loadBundledCatalogJson()` reads it back via
`Res.readBytes`, the same mechanism `GameSpriteLoader` already used for
sprites.

`resolvePools`/`placeholderPools` (pool-fallback logic) moved from `:app`'s
private `Main.kt` functions into public functions alongside `ContentPools`
in `ui/commonMain/run/RunApp.kt` — both `:app` and `:androidApp` bootstrap
now call the one shared implementation instead of two copies drifting.

## `:androidApp` module

Plain `com.android.application` + Kotlin/Android + Compose Multiplatform —
Android is its only target, so no `androidTarget()`/KMP source-set split,
unlike every other module. `applicationId de.jackbeback.pocketquest`,
`PocketQuestApplication` (loads the bundled catalog, builds `pools`, opens
the Room DB via the Android `Room.databaseBuilder<PocketQuestDatabase>
(context, name)` overload, builds `MetaRepository`/`RunRepository` —
mirrors desktop `Main.kt`'s bootstrap almost line for line), `MainActivity`
(`ComponentActivity`, `setContent { RunApp(...) }` — the exact same call
desktop's `runDesktopRunApp` makes, just without the `Window`/`application{}`
desktop wrapper). No custom launcher icon, no native Android 12+
SplashScreen API — deferred, an in-app Compose loading screen is enough for
a first draft.

## Toolchain bump (found live, not planned)

Compose Multiplatform 1.10.0 (already the project's pinned version, used by
every module) ships Android artifacts (`compose-ui-android`, `compose-
runtime-saveable-android`, etc.) that require AGP ≥ 8.6.0. This was never
enforced before — `:ui` and every `core:*`/`:data` module are Android
*libraries*, and that floor is only checked when something actually
assembles an APK. `:androidApp` is the first `com.android.application`
module in the repo, so it's the first thing to hit it. Fixed by bumping
`agp` 8.5.2 → 8.7.3 in `gradle/libs.versions.toml` (kept `compileSdk` at 35
— didn't need to move to 36, since `androidx.activity:activity-compose` was
pinned to 1.9.3 rather than the newest 1.13.0, which alone would have
forced AGP ≥ 8.9.1 and compileSdk 36). A version-catalog-only fix; no other
module's `android {}` block needed to change.

## Deferred (not this pass)

Real settings values / any persistence mechanism for them, native
SplashScreen API + launcher icon/branding art, `:designer` authoring tools
for Event/Shop/EncounterPool content (unrelated old Pass 9), audio (none
exists), iOS packaging, Play Store metadata, release signing.

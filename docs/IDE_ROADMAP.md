# Sketchware Pro / NWS: professional IDE roadmap

This document is the working record of the effort to make the app a more professional, stable Android IDE. It is
updated with every step. **A feature is only listed as "tested" when automated tests cover it; the app's screens have
not been exercised on a device in this effort, so "compiles" and "tested" are kept apart.**

Status words used below:

- **Existing**: was already in the app; reused, not rebuilt.
- **Done**: implemented, with unit tests for its logic, compiled by CI. Not yet tried on a device unless stated.
- **Planned**: not started.

## 1. Audit: what the app already has

| Area | Already in the app | Where |
|---|---|---|
| View editor canvas | Pinch zoom, zoom bar (50-200 %, Fit/100 %) that can be moved, device frames, dot grid, selection highlight | `editor/view/ViewEditor`, `PhoneFrame`, `DotGridFrameLayout`, `SelectionOverlay` |
| Logic editor | Blocks, a code view and a split blocks/Java view, fit-to-screen | `editor/LogicEditorActivity`, `editor/logic/LogicViewMode`, `CanvasFit` |
| Properties | Attribute catalogue, injected attributes | `pro/sketchware/properties`, `ViewProperty` |
| Build | AAPT2, ECJ, Kotlin, D8, R8 with mapping, AAB export, signing, build history | `mod/jbk/build`, `mod/pranav/build`, `BuildHistory` |
| Libraries | Built-in library graph, local libraries, online download, repair, exclusion of built-in libraries | `BuiltInLibraries`, `dev/aldi/sayuti/editor/manage`, `ExcludeBuiltInLibrariesActivity` |
| Export | Source export for Android Studio, signed APK/AAB | `export/ExportProjectActivity` |
| Reliability | Auto-backup, crash recovery, project metadata health check | `settings/AutoBackup`, `CrashRecovery`, `ProjectHealthCheck` |
| Added earlier in this effort | Clickable compile log, project search, automatic snapshots, Git, Java completion and live diagnostics | see `git log` |

**Not in the app (audit result):** snap/guides/alignment, multi-selection and undo/redo in the view editor; a block
debugger; unused-block and loop detection; a navigation graph; REST/Room designers; a design system; a compatibility
checker; a build log explainer; a dependency inspector; a memory-aware compiler; a security scanner; a combined
health score; CI file generation; visual regression tests.

## 2. Architecture of the new work

- Analyses are **pure Java engines** under `pro.sketchware.analysis` (no Android types), so they are unit-testable on a
  plain JVM. They produce `Finding`s (id, category, severity, title, cause, solution, evidence) collected in an
  `AnalysisReport`, which also computes the health score.
- Android-facing code is a thin layer: loading project data into the engines' input model, and screens that show findings.
- Risky or new features are behind `FeatureFlag`s, switchable in App Settings.
- Existing project formats are never changed; analyses are read-only.

Health score: starts at 100 and loses 15 / 5 / 1 per error / warning / info finding; a rule repeated many times costs at
most twice its weight, so one noisy rule cannot empty the score; never below 0.

## 3. Plan, by dependency and risk

| # | Item | Depends on | Risk | Status |
|---|---|---|---|---|
| 1 | Findings model, report, health score, feature flags | - | low | **Done** |
| 2 | Build Doctor (explains build logs), shown in the compile log | 1 | low | **Done** |
| 3 | Android Compatibility Center (SDK, permissions, deprecated APIs, AndroidX, ABI, accessibility, RTL, dark mode, edge-to-edge) | 1 | low | **Done** (engine tested; screen in step 6) |
| 4 | Security Scanner (secrets, permissions, cleartext, known-vulnerable libraries) | 1 | low | **Done** (engine tested; screen in step 6) |
| 5 | Dependency Inspector (tree, duplicate classes, version conflicts) and checked exclusions | 1 | medium | **Done** (engine tested; the screen and the exclusion check are compiled by CI, not tried on a device) |
| 6 | Project analysis screen and Health Score | 2-5 | low | **Done** (screen compiled by CI, not tried on a device) |
| 7 | Release Manager (versioning, mapping retention, size analysis) | - | medium | **Done** for mapping retention, release history and size analysis (engines tested; screen and export hook compiled by CI, not tried on a device). Versioning already existed (auto version code, build history) and is unchanged |
| 8 | CI file generation for Android Studio export | - | low | **Done**, off by default (generator tested and its YAML parsed with a YAML parser; the export wiring is compiled by CI, **not verified on a device**) |
| 9 | Memory-aware compiler | build pipeline study | high | **Partly done**: thread limit for D8 and R8 on devices with little memory (policy tested; call sites compiled by CI; **effect not measured on a real 2 GB phone**) |
| 10 | View editor: undo/redo, multi-selection, smart snap and guides (pure geometry engine first) | - | high | Planned |
| 11 | Canvas: orientation, tablet and foldable previews, safe-area overlays | 10 | medium | Planned |
| 12 | Properties inspector additions, design system | 10 | high | Planned |
| 13 | Block debugger, unused-block and loop detection, sync checks | - | high | Planned |
| 14 | Navigation graph, REST / Firebase / Room designer | 13 | high | Planned |
| 15 | Visual regression tests for screens | 10 | medium | Planned |

Items 9-15 touch the core editors or the build pipeline; each will start with a study of the existing code, ship behind a
feature flag, and be added only with regression tests.

## 4. Changelog and limitations

### Step 1-2: findings model and Build Doctor

- Added `pro.sketchware.analysis` (`Finding`, `Severity`, `Category`, `AnalysisReport`) and `BuildDoctor`.
- Build Doctor recognises 18 failure types (out of memory, no space, duplicate classes, dex limit, wrong class version,
  minimum SDK too low for a library, AAPT2 missing resources/attributes, manifest errors, ECJ unresolved imports/symbols
  and syntax errors, Kotlin errors, R8 missing classes/failures, signing, download failures, missing classes) and gives
  cause, solution and the log line as evidence.
- Shown from the compile log's options menu ("Build Doctor"); can be switched off in App Settings.
- Limitations: it matches known message patterns, so an unrecognised failure gives no finding and the raw log remains the
  reference. The patterns come from the messages of these tools as documented and observed; new tool versions may word
  them differently. Not yet tried on a device.

### Steps 3-6: compatibility, security, dependencies and the Project health screen

- `CompatibilityAnalyzer`: SDK levels (minimum above target, old target, minimum too low for AndroidX, edge-to-edge on
  Android 15), permissions that changed in recent Android versions (storage, media, notifications, background location, SMS and
  call log, query-all-packages, coarse location), deprecated or restricted APIs in the project's own Java/Kotlin files with
  file and line, `PendingIntent` without a mutability flag (crashes when targeting API 31+), the old Support Library, 64-bit
  native libraries, and for screens: images without a description, touch targets under 48 dp, text contrast (WCAG), text
  under 12 sp, asymmetric left/right spacing (right-to-left), fixed light colours without dark-mode resources.
- `SecurityScanner`: secrets by well-known formats (private keys, AWS, GitHub, Slack, Stripe, Telegram, Google API keys, JWT)
  and hard-coded passwords; secrets are **never copied into the report** (hidden after four characters); `http://` addresses;
  disabled TLS checks; world-accessible files; WebView bridges and file access; MD5/SHA-1 and weak ciphers; high-risk and many
  permissions; a short built-in list of well-known vulnerable library versions.
- `DependencyInspector`: dependency tree (cycle-safe), duplicate classes across libraries, version conflicts, Support Library mixed
  with AndroidX, and the check of an exclusion (what would be missing, what becomes unused).
- `ProjectFactsLoader` reads the open project into these models; `ProjectAnalysisActivity` shows the health score, the
  three fixes to make first, findings by category, a detail dialog per finding and the dependency tree. Entry: project drawer >
  Build & Security > Project health. Can be switched off in App Settings.
- Limitations:
  - The checks on screens only see properties stored in the project. Anything set through injected attributes or code
    blocks is not seen, except `contentDescription`, which is looked for in the injected attributes.
  - "Which built-in libraries are active" is derived from the Library Manager switches (AppCompat/Material, Firebase,
    AdMob, Maps); libraries added only by components (Gson, Glide, OkHttp) are not in the tree.
  - Duplicate-class detection needs the library files to be extracted, which happens at the first build.
  - The vulnerable-library list is short and hand-written; a clean result is not a guarantee.
  - The date-dependent Google Play requirement (target API) is a constant in `CompatibilityAnalyzer` that needs updating
    when Google changes it.
  - The exclusion check runs when you press Save in the library picker of Exclude built-in libraries: it lists what would be missing (or
    left unused) and asks to confirm; it does not block, because a local library may legitimately replace an excluded one.
    It sees a replacement only through the packages of the project's local libraries.
  - The screens have been compiled by CI but not used on a device.

### Step 8: GitHub Actions in the Android Studio export

- `CiWorkflowGenerator` writes `.github/workflows/android.yml`: JDK 17, Gradle 8.14.3 (installed by the workflow because the
  export has no Gradle wrapper; the export's Android Gradle Plugin 8.12.0 needs Gradle 8.13+), `gradle assembleDebug`,
  optional lint and unit tests, and the debug APK kept as an artifact.
- Three switches in App Settings (all **off** by default): add the workflow to the export, run lint, run unit tests.
- Limitations: the app's archiver (`KB`) is in a precompiled library that could not be read, so it is **not verified** that it
  includes the hidden `.github` folder in the exported zip. If the file is missing from the zip, that is the cause.
  Only the debug build is run; a signed release build needs secrets that the app cannot create for you.
  The Gradle and Java versions are constants that must follow the plugin version the export writes.

### Step 7: Release Manager

- Already in the app and left alone: APK and AAB export, signing, automatic version code, build history, R8 mapping production.
- New: after a successful release export, `ReleaseArchive` keeps a record (version, type, time, size, SHA-256, path) and a copy
  of the R8 mapping of **that build** in `.sketch_nws/releases/<project id>/`, so crashes of released versions can be
  de-obfuscated later even after the next build overwrote `mapping.txt`. A mapping older than the build (for example when the
  shrinker was off) is not attached.
- `SizeAnalyzer` breaks an APK or AAB down into code, resources, assets and native libraries per architecture, lists the
  largest files and reports architectures almost no phone uses, large assets and multi-dex.
- The **Releases** screen (project drawer > Build & Security) lists releases with their size change against the previous
  release of the same type, shows details and runs the size analysis. Can be switched off in App Settings.
- Limitations: it only records exports made after this change; the size analysis reads the file at the path where it was exported,
  so a moved or deleted file cannot be analysed; there is no in-app sharing of the mapping, its path is shown.

### Step 9: memory-aware compiler (first part)

- Study result: `largeHeap` is already on; ECJ is single-threaded; AAPT2 is a native process; D8 and R8 run with their own thread
  pools, and the dex and shrinker steps are where the memory peak is. Libraries are already pre-dexed (`dexs`).
- `MemoryProfile` chooses from total RAM, the low-RAM flag, the app's heap limit, cores and a user switch: LOW (RAM at or below
  3 GB, low-RAM flag, or a heap of 256 MB or less: 1 thread, garbage collection requested before the heavy steps), NORMAL (2 threads)
  or HIGH (at least 6 GB and 6 cores: up to 4 threads).
- `BuildMemory` gives D8 and R8 a thread pool of that size (`D8.run(command, executor)` / `R8.run(command, executor)`, verified against
  the R8 jar's API). With the switch off the compilers behave exactly as before.
- Switches in App Settings: Memory-aware build (on by default), Always use low-memory build (off).
- Limitations: **the benefit has not been measured on a 2 GB device**; fewer threads lowers the peak but makes those steps slower.
  ECJ and AAPT2 are not changed. Nothing is cached differently. A full "will this build fit" estimate is not attempted.

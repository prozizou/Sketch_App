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
| 3 | Android Compatibility Center (SDK, permissions, deprecated APIs, AndroidX, ABI, accessibility, RTL, dark mode, edge-to-edge) | 1 | low | Planned |
| 4 | Security Scanner (secrets, permissions, cleartext, known-vulnerable libraries) | 1 | low | Planned |
| 5 | Dependency Inspector (tree, duplicate classes, version conflicts) and checked exclusions | 1 | medium | Planned |
| 6 | Project analysis screen and Health Score | 2-5 | low | Planned |
| 7 | Release Manager (versioning, mapping retention, size analysis) | - | medium | Planned |
| 8 | CI file generation for Android Studio export | - | low | Planned |
| 9 | Memory-aware compiler | build pipeline study | high | Planned |
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

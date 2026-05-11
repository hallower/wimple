# Changelog

All notable changes to Wimple are documented in this file.

Format inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
this project follows a single linear `versionCode` scheme rather than strict
SemVer. Dates use ISO 8601 (`YYYY-MM-DD`).

Korean version: [CHANGELOG.ko.md](CHANGELOG.ko.md)

## [1.0.53] — 2026-05-11 (current)

The largest release in the project's history. Headlined by on-device AI
classification of bank notifications, a new home-screen widget, a full
Material 3 UI pass, and several security hardenings.

### Added

#### Bank notification AI auto-classification (Beta)
- On-device classifier (Gemini Nano via ML Kit Prompt API) that extracts
  merchant, amount, and kind (expense / income / transfer) from Korean bank
  notifications and turns them into transaction drafts.
- Review queue screen with state badges (Ready / Ambiguous / Unparsed / Error),
  source tags (learned mapping vs. AI similarity), `[Confirm]` one-tap,
  `[Enter manually]`, dismiss, retry, and bulk confirm.
- Cascade pipeline: extract → merchant→accounts mapping lookup
  (`MerchantMappingDBHandler`) → AI similarity over recent transactions →
  account-pair suggestion. Includes few-shot grounding from a
  user-confirmed example pool (`ExtractionExampleDBHandler`).
- Manual-entry handoff that prefills title / amount / accounts, locks
  AI-filled fields against frequent-item dropdown cascade, and learns the
  user's final account pair back into the mapping store on submit.
- Regex amount fallback (`extractAmountFromText`) when the AI cannot return
  a structured amount — prefills the form from the original notification
  text instead of leaving an empty field.
- Beta label across all entry points (settings toggle, first-launch
  suggestion dialog, post-enable info dialog, activation toast) with a
  bullet noting the manual-input fallback.
- One-shot suggestion dialog replaces the old biometric onboarding nudge,
  offered when the device supports on-device AI.
- Dev-only AI classification log viewer, gated behind a 10-tap unlock on
  the "Open Source Licenses" row. Surfaces per-stage prompts / responses /
  candidates / mapping lookups for on-device debugging.

#### Toolbar notification cues
- Whooing forum new-notification toolbar icon with one-shot shake on the
  first appearance after launch.
- Bank-review pending-queue toolbar icon, also shake-animated on first
  appearance.
- Short companion bubble under each icon ("N bank notifications" /
  "New Whooing forum notifications") shown for 2.5 s, anchored under the
  icon and auto-dismissed.

#### Monthly summary home-screen widget
- New home-screen widget showing daily income / expense / net for the
  current month with a per-day chart and prev / next month navigation.

#### Other
- Korean user guide and developer wiki under `docs/`.
- Unit tests for bank notification parsing and the calculator utility.

### Changed

- Material 3 design system pass: color tokens, typography, list-item
  separation, transaction-input layout density, and key visibility on the
  numeric keypad (`=` key restored).
- AI classifier prompts rewritten for small-model accuracy: schema-first
  ordering, Korean kind labels (지출 / 수입 / 이체) with English-fallback
  parsing, discrete confidence (`high` / `medium` / `low`) instead of
  continuous score, few-shot examples now include both Title and Body
  matching the live notification format, `MAX_CANDIDATES` reduced
  30 → 10 to limit lost-in-the-middle.
- Notification capture is now decoupled from Whooing forwarding — the
  one-tap AI suggestion can run independently.
- Account-list selection now persists across adapter reloads in review
  sessions (multiple `GET_ALL_ACCOUNT_RECEIVED` per session would
  previously wipe the visual selection).
- Classification pass skips rows the user dismissed mid-pass to avoid
  spending model time on already-gone rows.

### Fixed

- Widget income / expense totals now match what whooing.com displays.
- Calculator `=` key was being clipped on some layouts.
- Layout geometry across list items unified with the rebalanced
  transaction-input screen.

### Security

- App data auto-backup disabled to keep auth tokens and other sensitive
  data out of Android's default backup pipeline.
- Removed legacy trust-all SSL configuration; HTTPS now uses standard
  certificate validation.
- Whooing app secret moved out of tracked source.
- Wimple auth storage extracted into its own module.
- Whooing API request bodies standardized on JSON / form-encoded payloads
  for robustness.

## [1.0.52] — 2026-05-01

### Changed

- Reverted R8 minification and resource shrinking introduced in 1.0.51
  after Play Store crash reports. Build now ships unminified.

## [1.0.51] — 2026-04-30

Large UI / theme / foldable release. Originally tried R8 minification +
resource shrinking; reverted in 1.0.52.

### Added

- Two-pane drawer pairing on sw600dp+ screens (foldables, large tablets).
- Two-pane settings layout with a Wimple inquiry link.
- FAB position persisted separately per fold state (folded vs. unfolded).
- FAB default rotation expanded to six screens.
- Unsupported-notification collection screen for manual review of bank
  alerts the parser couldn't handle.
- App selector for which apps' notifications should be read.
- Bank notification upload pipeline (per-notification send to Whooing).
- OSS license notice screen.
- Agreement notice on first launch.
- Biometric authentication information dialog.
- Local Claude Code settings under `.claude/` for development workflow.

### Changed

- Migrated `AppTheme` to `Theme.Material3.DayNight.NoActionBar`; light
  palette only (dark variant briefly attempted then reverted).
- Introduced Material 3 semantic color tokens.
- Bumped `appcompat` + `material` to versions that ship Material 3 themes.
- Modernized Kotlin / build scripts.
- Wimple lifecycle refactored: separated listener, fragment creation, FAB
  controller, biometric implementation, and command IDs from
  `WimpleActivity`.
- Updated bank notification uploader to match the Whooing client format.

### Fixed

- Two-pane not showing on foldables (initial implementation regression).
- Notification-sending option not preserved through logout.
- Wimple start / restart issues on certain launch paths.
- Notification upload + saving paths.
- Multiple guard fixes for detached `TwoPaneFragment` state.

### Security

- N/A (Whooing app secret extraction landed in 1.0.53.)

## [1.0.49] — 2025-10-10

### Changed
- Updated deprecated APIs to current AndroidX equivalents.

## [1.0.48] — 2025-10-10

### Changed
- `targetSdkVersion` bumped to current level.
- Gradle build scripts updated.
- Fixed build errors against the latest Kotlin compiler.

## [1.0.46] — 2023-06-29

### Added
- Project / build settings refresh.

### Changed
- Replaced deprecated preference APIs.

### Fixed
- Latest-items sorting issue.
- Transaction list now reads from the cached entries.
- Hangul cho-sung (초성) comparison for item name search.

## [1.0.45] — 2021-11-22

### Fixed
- Login blocking issue with corrected error handling.

## [1.0.44] — 2021-11-16

### Fixed
- Crash on a button click path.
- Multi-window issues — fragments are now recreated each time to keep
  state consistent.

## [1.0.43] — 2020-11-04

### Changed
- Raised alpha value on the list color tokens.
- Tweaked alpha on the transaction-insert page.

### Fixed
- Crash from a missing DB handler null-check.

## [1.0.41] — 2020-09-24

### Fixed
- Account expandable list rendering issue.
- Uppercase latest-item finding.
- English latest-item finding.

### Changed
- `targetSdkVersion` bumped to 28.

## [1.0.40] — 2020-04-21

### Added
- Biometric authentication on app start.
- Settings entry for biometric authentication toggle.
- Hangul initial-sound (초성) search across the latest-items list.

### Changed
- Removed unnecessary API calls during startup.

### Fixed
- Crashes during fragment replacement.
- `IllegalStateException` during fragment add.

## [1.0.39] — 2019-03-24

### Added
- Floating action button now shows a contextual icon per fragment.
- FAB customization setting.
- FAB icons resource set.

### Fixed
- Memo text pad display on initial load.
- Date parsing issue.
- Transaction list update display.

### Changed
- Updated AndroidX package versions.

## [1.0.38] — 2019-03-15

### Changed
- Replaced `ItemManager` and `EntryManager` with cleaner implementations.
- Replaced `RestAPIInvoker`.
- Locale follows device setting.

### Fixed
- Section selection and entry removal.
- Entry setting on resume.

## [1.0.37] — 2019-03-04

Project-wide modernization release.

### Changed

- Migrated from Android Support Library to AndroidX / Jetpack.
- Converted all Activities and Fragments to the new Kotlin-friendly
  patterns (splash, main, settings, payment notice, transaction list,
  income / expense summary, post-news, datepicker, insert).
- Replaced settings, payment notice, transaction list, and insert
  fragments.
- IME-based amount input support.
- Build script overhaul (CircleCI integration, lint options).
- Removed legacy SMS read / submit functionality.

### Fixed

- Startup crash.
- Refresh issue and crash when amount was empty.
- Settings display crash.
- Date filter issue.
- Various Kotlin lint warnings cleared.

## [1.0.35] — 2018-02-13

### Fixed
- Minus value on the mini calculator.
- Zero insert after calculation.

### Changed
- Code formatting pass.

## [1.0.34] — 2018-02-11

### Added
- Minimum-stored-SMS threshold for Whooing external input.

### Fixed
- Calculator operations.

## [1.0.33] — 2018-02-11

### Added
- Exception handling for invalid fragment state.

### Changed
- Rearranged list layouts.

## [1.0.32] — 2018-01-29

### Added
- IME (virtual keyboard) support for the amount display.

### Changed
- Project recompiled against the latest Android Studio toolchain.
- Date setting on transaction insert.
- Monthly item date setting.

### Fixed
- Null-pointer guard around Settings.
- Latest items not updating after insert.

## [1.0.31] — 2017-03-29

### Added
- List screen FAB.

### Changed
- List views for the finance status and income / expense status pages.
- Status fragments now refresh data every time they're shown.

### Fixed
- Build warnings cleared.

## [1.0.29] — 2017-01-21

### Fixed
- Monthly item insert path.

### Changed
- Removed legacy code paths.

## [1.0.28] — 2017-01-14

### Added
- Section title displayed on the list view.
- Update-notification initial timestamp.
- Negative-value input on the transaction insert form.

### Changed
- Rearranged list view items.

## [1.0.27] — 2017-01-13

### Fixed
- Crash on Android 7.0 (Nougat).

### Added
- Runtime permission request at startup.

## [1.0.26] — 2017-01-12

First versioned release tracked in this changelog.

### Fixed
- ListView duplicate rendering issue.
- Graph display.

### Changed
- Adjusted graph-related layouts.

---

Earlier history (pre-1.0.26) covers initial project setup, Android Studio
migration, and package-id changes; not enumerated here because the
project wasn't yet on a regular release cadence.

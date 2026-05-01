# Wimple — Whooing sIMPLE client for Android

A third-party Android client for [whooing.com](https://whooing.com), a Korean
personal accounting service. Wimple focuses on fast manual entry on the go and
automatic capture of bank/card push notifications.

- **Google Play**: <https://play.google.com/store/apps/details?id=com.blogspot.charlie0301>
- **사용 설명서 (Korean user guide)**: [USAGE.md](USAGE.md)
- **Feedback / inquiries**: <https://whooing.com/#forum/developer/ko/app_list/0/_/28698>

## Features

- **Quick transaction entry** — built-in calculator, recently-used items,
  monthly recurring items, memo support.
- **Transaction list** — view, edit, or delete past entries; jump to date.
- **Financial state (자산부채)** — balance-sheet charts with optional
  group-level aggregation.
- **Income / expense (비용수익)** — P&L charts with optional budget overlay.
- **Bank notification capture (은행 알림 자동 기록)** — listens to push
  notifications from selected bank or card apps and forwards parsed
  transactions to Whooing. Unparsed notifications are queued for manual
  review and resend.
- **News sharing** — share any web article into Wimple to post it on your
  Whooing BBS.
- **Biometric app lock** — optional fingerprint / face unlock at launch.
- **Two-pane layout** — split view on tablets and unfolded foldables.
- **Korean and English UI**.

## Building from source

Requirements: Android Studio (Hedgehog or newer recommended), JDK 11+, Android
SDK with `compileSdk 36`.

1. Clone the repository.
2. Copy `wimple/local.properties.example` to `wimple/local.properties` and
   fill in `WIMPLE_APP_SECRET` with a Whooing app secret you obtain by
   registering your own app at whooing.com. The secret is read at build time
   and never tracked in git.
3. Open the `wimple/` directory in Android Studio, or build from CLI:
   ```
   cd wimple
   ./gradlew :app:assembleDebug
   ```

The build expects `WIMPLE_APP_SECRET` in either `local.properties` or the
environment variable of the same name. Without it, the app will compile but
authentication against Whooing will fail.

## Permissions

- **Internet** — required, used only to talk to whooing.com.
- **Notification access** — optional, only requested when you enable bank
  notification capture. Wimple reads notifications only from the apps you
  explicitly select; everything else is ignored.

Wimple does not collect or transmit data to any server other than Whooing.
On-device data is cached locally only.

## Tech stack

Native Android app written in Kotlin and Java, using AndroidX, Material 3,
ViewBinding, MPAndroidChart, and Jersey 1.x for the Whooing REST client.

## License

MIT — see [license.txt](license.txt). Some icons are sourced under the
[graphicloads](http://graphicloads.com/license/) license.

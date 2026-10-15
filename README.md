# Focus Lock

A personal Android app for putting distracting apps out of reach until a focus session ends.

Instead of paying a monthly subscription for an app that helps me focus, I decided to build one myself. I wanted something simple: choose the distractions, set a deadline, and get back to what I meant to do.

Focus Lock runs locally. There are no accounts, ads, subscriptions, servers, or analytics.

## Features

- Block selected apps for 25, 60, or 120 minutes, or until a date and time you choose.
- Add more apps during a session. Existing blocks cannot be removed inside the app until expiry, and additions keep the same deadline.
- Use a plain, black, text-only home screen. Blocked apps disappear from its list until the timer expires.
- Enable session grayscale with an optional computer-assisted permission grant.
- Keep the session running when the dashboard closes.

The current build appears on the phone as **FocusLock 2** and uses `com.olaflex.focuslock.v2`. This identity keeps updates compatible with the existing installation.

## Install and Use

Requires Android 8.0 or newer. Build an APK using the instructions below, transfer it to your phone, and open it to install. Android may ask you to allow installation from the app opening the file.

1. Open FocusLock 2 and enable its Accessibility service.
2. If Android restricts this permission, open the app's system info page and look for **Allow restricted settings**, then return to Accessibility settings.
3. Select the apps you want to block. Keep essential apps available.
4. Choose a short test session before committing to a longer deadline.
5. Optionally choose **Use plain home screen** and select FocusLock 2 as your Home app.

During a session, **Add more apps to block** adds distractions immediately. There is no in-app cancellation or removal control while the timer runs.

Installing the APK on another Android phone creates a separate installation. Enable its permissions and choose its apps and deadline there; the APK does not contain your current blocklist or timer. Grayscale permission must also be granted separately on each phone.

## Build

The Android project is in [`FocusLock/`](FocusLock/). It uses Java 17, Android SDK 35, Android Gradle Plugin 8.7.3, and Gradle 8.9.

Open `FocusLock/` in Android Studio, let it install the required SDK components, and build the debug APK. From a terminal with Java 17 and the Android SDK configured:

```sh
cd FocusLock
./gradlew assembleDebug
```

The APK is written to `FocusLock/app/build/outputs/apk/debug/app-debug.apk`.

For packaging directly with SDK Build Tools 35.0.0:

```sh
./scripts/build-apk.sh
```

This produces `builds/FocusLock-2.apk` and runs the Java checks. See [development notes](docs/DEVELOPMENT.md) for prerequisites, signing, and verification.

## Permissions and Privacy

The Accessibility service checks the foreground package name, places a blocking screen over selected apps, and returns to Home. It checks again every 300 milliseconds while a session is active. Android grants window access for this check; the implementation uses the root package name and does not read node text or traverse screen content.

App selections and deadlines are stored in private SharedPreferences. Backup is disabled. The manifest requests no Internet permission, and there is no network client or telemetry service.

Optional grayscale setup and precise unlock timers are covered in [setup](docs/SETUP.md).

## Limits

On a normal personal phone, blocking depends on Accessibility remaining enabled and Android allowing the service to run. Uninstalling, force-stopping, revoking permissions, safe mode, and some manufacturer power controls can bypass it. Brief content flashes can occur before detection. Notification actions, split screen, and picture-in-picture need testing on each device.

The plain launcher hides blocked apps from its own list. It does not remove them from Settings, other launchers, recent apps, or notifications. Notifications and individual website URLs are not filtered; blocking a browser blocks the whole browser.

Within one boot, the timer uses elapsed time so changing the clock does not shorten it. After a reboot it falls back to the saved wall-clock deadline, so changing the clock across a reboot can bypass the timer.

An experimental device-owner path is present for package hiding and uninstall restrictions. It has compiled but has not been validated on a provisioned device. Normal installation does not enable it. See [architecture](docs/ARCHITECTURE.md).

## Project Layout

```text
FocusLock/                  Android Studio project
  app/src/main/             Java sources, manifest, and resources
  tests/                    Standalone Java checks
scripts/                    Build and isolated device checks
docs/                       Setup and implementation notes
```

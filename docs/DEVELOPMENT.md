# Development

## Toolchain

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0 for the SDK-only script
- Gradle 8.9 through the included wrapper, with Android Gradle Plugin 8.7.3
- Python 3 for SDK-only packaging and optional device checks
- Zsh for the SDK-only build script

Android Studio can build the project in `FocusLock/`. Set the SDK path through Android Studio, `ANDROID_HOME`, or the project's ignored `local.properties`.

```sh
cd FocusLock
./gradlew assembleDebug
```

## SDK-Only Build

From the repository root:

```sh
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
./scripts/build-apk.sh
```

The script uses an existing `JAVA_HOME`, or finds Java 17 through macOS's Java helper. On other systems, set `JAVA_HOME` explicitly and supply the SDK path. It compiles Java, runs standalone checks, packages resources with AAPT2, builds DEX with D8, aligns the APK, signs it, and verifies its signature.

Outputs are ignored under `builds/`; intermediate files are ignored under `.build/`. The default application ID is `com.olaflex.focuslock.v2`. Set `FOCUS_PACKAGE` to build an isolated test installation.

## Signing

The SDK-only script creates a local personal-testing key at `~/.local/share/focuslock/test-signing.jks` if it does not exist. Its password is the conventional test value `android`. Keep this key private and retain it for compatible updates. The Gradle debug build uses Android's usual debug signing instead; those APKs cannot update an installation signed with a different key.

Neither build path produces a production release configuration. For distribution, configure your own release key and signing process outside version control. Do not commit keystores, signing credentials, APKs, or local phone data.

## Java Checks

Run both standalone suites without the Android SDK:

```sh
./scripts/test.sh
```

The clock suite checks expiry, wall-clock changes within one boot, reboot fallback, and empty session state. The selection suite checks active additions, attempted removals and clears, editing after expiry, and input isolation.

## Device Checks

The optional scripts use Android Platform Tools through `adb` on `PATH`, or the `ADB` environment variable. Use a dedicated test phone with LinkedIn, Chrome, Calculator, and, for the additions test, an app named Access More installed. The UI scripts currently assume a 1080-pixel-wide screen and English app labels.

Build and install the separate verification package before each test; the scripts remove it when finished:

```sh
FOCUS_PACKAGE=com.olaflex.focuslock.verify ./scripts/build-apk.sh
adb install --no-incremental -r builds/com.olaflex.focuslock.verify.apk
python3 scripts/check-add-apps.py
```

Repeat the build/install step before running `scripts/check-home.py` or `scripts/device-check.py`. The blocking check temporarily enables the verification Accessibility service and restores the previous service list afterward. These scripts write fixture preferences only to the verification package. Never change them to target your active personal installation.

The tests cover adding apps without moving the deadline, launcher hiding/restoration, and repeated blocked launches followed by expiry. UIAutomator can temporarily suspend other Accessibility services, so the repeated-launch test uses activity diagnostics instead of UIAutomator.

Earlier device checks passed on a TECNO CM5: 12 repeated LinkedIn launches were redirected, blocked launcher entries returned after expiry, and active additions preserved the original deadline. This is limited device evidence, not a guarantee across Android versions or manufacturers. The device-owner path has not been exercised.

# Phone Setup

## Blocking

Install the APK, open FocusLock 2, and enable its Accessibility service using the app's permission button. Some Android versions require **App info > menu > Allow restricted settings** for sideloaded apps before this is available.

Choose apps and start a short session. Repeatedly open a blocked app, confirm it returns you Home, and check that unselected apps remain usable. Let the timer finish and confirm the blocked app opens again.

Check manufacturer battery settings if blocking stops in the background. Service behavior varies across phones; a successful install alone does not verify reliable blocking.

## Plain Home Screen

Before starting a session, tap **Use plain home screen** and select FocusLock 2 as the default Home app. You can also choose it in Android's default-app settings.

The home screen lists apps as text and omits blocked packages while a session is active. Apps return to the list after expiry. Other system surfaces can still show them.

## Grayscale

Automatic grayscale requires `WRITE_SECURE_SETTINGS`, which a normal installation cannot grant itself. With Android Platform Tools installed and USB debugging authorized on your own phone, run:

```sh
adb shell pm grant com.olaflex.focuslock.v2 android.permission.WRITE_SECURE_SETTINGS
```

Focus Lock saves the previous color-correction values when a session starts and restores them after expiry. Without this grant, app blocking and the plain home screen still work. The permission is specific to that phone and installation.

## Unlock Alarms

Where available, allow **Alarms & reminders** for FocusLock 2 in Android's special app access settings. This lets the deadline receiver restore grayscale at the scheduled time while the phone is idle. Without exact-alarm access, personal mode uses an inexact alarm, so restoration may be delayed until Android delivers it or the running service checks the session.

## Another Phone

Transfer the built APK and install it normally on another compatible Android phone. Enable Accessibility, choose the default Home app if desired, and configure a new session. The APK carries code, not the first phone's saved preferences, active timer, or permission grants. This project has no account sync or session-transfer feature.

## Managed Devices

The experimental device-owner code is separate from normal installation. It is not validated for everyday use. Do not assume that enabling Accessibility or granting grayscale permission makes Focus Lock uninstall-proof. Provisioning and recovery should be validated on a dedicated test device before relying on that path.

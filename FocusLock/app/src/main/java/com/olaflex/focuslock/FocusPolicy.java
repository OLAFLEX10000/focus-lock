package com.olaflex.focuslock;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

/** Applies OS policies only when this app has been explicitly provisioned as device owner. */
final class FocusPolicy {
  private final Context c;
  private final Store store;
  private final DevicePolicyManager dpm;
  private final ComponentName admin;

  FocusPolicy(Context context) {
    c = context;
    store = new Store(c);
    dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
    admin = new ComponentName(c, FocusAdmin.class);
  }

  boolean managed() {
    return dpm.isDeviceOwnerApp(c.getPackageName());
  }

  boolean exactTimerReady() {
    return Build.VERSION.SDK_INT < 31
        || ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
  }

  String status() {
    return managed()
        ? "Managed protection available"
        : "Personal mode: uninstall protection and system-wide app hiding require managed setup.";
  }

  void prepare(long duration) {
    if (managed() && !exactTimerReady())
      throw new IllegalStateException(
          "Enable precise timers in Android settings before starting a managed session.");
  }

  void sync() {
    boolean active = store.remaining() > 0;
    grayscale(active);
    if (managed()) {
      Set<String> hidden =
          new HashSet<>(store.p.getStringSet("policyHidden", Collections.emptySet()));
      if (active && store.p.getLong("managedAppliedEnd", 0) != store.p.getLong("wall", 0)) {
        if (!exactTimerReady()) {
          schedule(active);
          return;
        }
        // Persist intent first so a reboot or interruption can retry or undo each operation.
        Set<String> targets = store.apps();
        targets.remove(c.getPackageName());
        targets.remove("com.android.settings");
        hidden.addAll(targets);
        store.p.edit().putStringSet("policyHidden", hidden).commit();
        dpm.setUninstallBlocked(admin, c.getPackageName(), true);
        if (Build.VERSION.SDK_INT >= 30) {
          if (!store.p.contains("previousUserControl"))
            store
                .p
                .edit()
                .putStringSet(
                    "previousUserControl", new HashSet<>(dpm.getUserControlDisabledPackages(admin)))
                .commit();
          Set<String> disabled =
              new HashSet<>(store.p.getStringSet("previousUserControl", Collections.emptySet()));
          disabled.add(c.getPackageName());
          dpm.setUserControlDisabledPackages(admin, new ArrayList<>(disabled));
        }
        boolean allHidden = true;
        for (String pkg : targets) {
          if (!dpm.setApplicationHidden(admin, pkg, true)) {
            allHidden = false;
            android.util.Log.w("FocusLock", "Android refused to hide " + pkg);
          }
        }
        if (allHidden)
          store.p.edit().putLong("managedAppliedEnd", store.p.getLong("wall", 0)).commit();
      } else if (!active && (store.p.contains("managedAppliedEnd") || !hidden.isEmpty())) {
        Set<String> failed = new HashSet<>();
        for (String pkg : hidden) if (!dpm.setApplicationHidden(admin, pkg, false)) failed.add(pkg);
        store.p.edit().putStringSet("policyHidden", failed).remove("managedAppliedEnd").commit();
        dpm.setUninstallBlocked(admin, c.getPackageName(), false);
        if (Build.VERSION.SDK_INT >= 30 && store.p.contains("previousUserControl")) {
          dpm.setUserControlDisabledPackages(
              admin,
              new ArrayList<>(store.p.getStringSet("previousUserControl", Collections.emptySet())));
          store.p.edit().remove("previousUserControl").commit();
        }
      }
    }
    schedule(active);
  }

  private void grayscale(boolean active) {
    try {
      if (active) {
        if (!store.p.contains("grayPreviousEnabled"))
          store
              .p
              .edit()
              .putInt(
                  "grayPreviousEnabled",
                  Settings.Secure.getInt(
                      c.getContentResolver(), "accessibility_display_daltonizer_enabled", 0))
              .putInt(
                  "grayPreviousMode",
                  Settings.Secure.getInt(
                      c.getContentResolver(), "accessibility_display_daltonizer", -1))
              .commit();
        if (Settings.Secure.getInt(c.getContentResolver(), "accessibility_display_daltonizer", -1)
            != 0)
          Settings.Secure.putInt(c.getContentResolver(), "accessibility_display_daltonizer", 0);
        if (Settings.Secure.getInt(
                c.getContentResolver(), "accessibility_display_daltonizer_enabled", 0)
            != 1)
          Settings.Secure.putInt(
              c.getContentResolver(), "accessibility_display_daltonizer_enabled", 1);
      } else if (store.p.contains("grayPreviousEnabled")) {
        Settings.Secure.putInt(
            c.getContentResolver(),
            "accessibility_display_daltonizer",
            store.p.getInt("grayPreviousMode", -1));
        Settings.Secure.putInt(
            c.getContentResolver(),
            "accessibility_display_daltonizer_enabled",
            store.p.getInt("grayPreviousEnabled", 0));
        store.p.edit().remove("grayPreviousMode").remove("grayPreviousEnabled").commit();
      }
    } catch (SecurityException denied) {
      /* Personal installs need a one-time adb grant. */
    }
  }

  private void schedule(boolean active) {
    AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    PendingIntent intent =
        PendingIntent.getBroadcast(
            c,
            1,
            new Intent(c, FocusDeadlineReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    long end = store.p.getLong("elapsed", 0);
    if (!active) {
      alarms.cancel(intent);
      store.p.edit().remove("scheduledEnd").commit();
      return;
    }
    if (store.p.getLong("scheduledEnd", 0) == end
        && store.p.getBoolean("scheduledExact", false) == exactTimerReady()) return;
    long at = android.os.SystemClock.elapsedRealtime() + store.remaining();
    if (exactTimerReady())
      alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent);
    else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, intent);
    store
        .p
        .edit()
        .putLong("scheduledEnd", end)
        .putBoolean("scheduledExact", exactTimerReady())
        .commit();
  }

  void releaseManagement() {
    if (store.remaining() > 0)
      throw new IllegalStateException("Wait until the focus session ends.");
    sync();
    if (!store.p.getStringSet("policyHidden", Collections.emptySet()).isEmpty())
      throw new IllegalStateException("Some apps still need to be restored.");
    dpm.clearDeviceOwnerApp(c.getPackageName());
  }
}

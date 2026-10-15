package com.olaflex.focuslock;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;

final class Store {
  final Context c;
  final android.content.SharedPreferences p;

  Store(Context c) {
    this.c = c;
    p = c.getSharedPreferences("focus", 0);
  }

  int boot() {
    return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
  }

  long remaining() {
    return SessionClock.remaining(
        p.getLong("wall", 0),
        p.getLong("elapsed", 0),
        p.getInt("boot", -2),
        System.currentTimeMillis(),
        SystemClock.elapsedRealtime(),
        boot());
  }

  Set<String> apps() {
    return new HashSet<>(p.getStringSet("apps", Collections.emptySet()));
  }

  private static final Object SELECTION_LOCK = new Object();

  boolean selection(Set<String> requested) {
    synchronized (SELECTION_LOCK) {
      Set<String> next = SelectionRules.apply(apps(), requested, remaining() > 0);
      return p.edit().putStringSet("apps", next).remove("managedAppliedEnd").commit();
    }
  }

  boolean start(long duration) {
    if (remaining() > 0 || duration <= 0 || apps().isEmpty()) return false;
    new FocusPolicy(c).prepare(duration);
    return p.edit()
        .putLong("wall", System.currentTimeMillis() + duration)
        .putLong("elapsed", SystemClock.elapsedRealtime() + duration)
        .putInt("boot", boot())
        .commit();
  }
}

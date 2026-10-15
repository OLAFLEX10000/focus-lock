package com.olaflex.focuslock;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
  private Store store;
  private LinearLayout body;
  private TextView status;
  private final Handler handler = new Handler();
  private boolean activeRendered;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          boolean active = store.remaining() > 0;
          if (active != activeRendered) render();
          else update();
          handler.postDelayed(this, 1000);
        }
      };

  static String format(long ms) {
    long s = (ms + 999) / 1000;
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
  }

  int dp(int n) {
    return (int) (getResources().getDisplayMetrics().density * n);
  }

  TextView text(String value, int size) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(Color.rgb(238, 244, 235));
    t.setPadding(0, dp(10), 0, dp(10));
    body.addView(t);
    return t;
  }

  Button button(String value, Runnable action) {
    Button b = new Button(this);
    b.setText(value);
    b.setTextColor(Color.rgb(15, 35, 27));
    b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(183, 247, 118)));
    body.addView(b, new LinearLayout.LayoutParams(-1, dp(56)));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  boolean enabled() {
    AccessibilityManager m = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
    for (AccessibilityServiceInfo i :
        m.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
      android.content.pm.ServiceInfo s = i.getResolveInfo().serviceInfo;
      if (s.packageName.equals(getPackageName()) && s.name.equals(BlockService.class.getName()))
        return true;
    }
    return false;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(this);
    getWindow().setStatusBarColor(Color.rgb(15, 25, 22));
    render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    render();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  protected void onPause() {
    super.onPause();
    handler.removeCallbacks(tick);
  }

  void update() {
    status.setText(
        activeRendered
            ? format(store.remaining())
                + " remaining\n"
                + (enabled() ? "Blocking enabled" : "Blocking paused — enable accessibility")
            : (enabled() ? "● Ready to focus" : "○ Enable blocking permission first"));
  }

  void permission() {
    new AlertDialog.Builder(this)
        .setTitle("Enable app blocking")
        .setMessage(
            "FocusLock uses Accessibility to check the foreground app, cover blocked apps, and"
                + " return you home. Android grants screen access for this check; FocusLock uses"
                + " only the root package name, never screen text or messages. It sends no data"
                + " online. In the next screen, select FocusLock and enable it.")
        .setPositiveButton(
            "Open settings",
            (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
        .setNegativeButton("Cancel", null)
        .show();
  }

  void render() {
    activeRendered = store.remaining() > 0;
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(Color.rgb(15, 25, 22));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(24), dp(28), dp(24), dp(32));
    scroll.addView(body);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          body.setPadding(
              dp(24),
              dp(28) + insets.getSystemWindowInsetTop(),
              dp(24),
              dp(32) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    text("FOCUSLOCK", 16);
    text(new FocusPolicy(this).status(), 14);
    if (!activeRendered)
      button(
          "Use plain home screen", () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
    if (new FocusPolicy(this).managed() && !new FocusPolicy(this).exactTimerReady())
      button(
          "Enable precise unlock timer",
          () ->
              startActivity(
                  new Intent(
                      Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                      android.net.Uri.parse("package:" + getPackageName()))));
    if (!activeRendered && new FocusPolicy(this).managed())
      button(
          "Remove device management",
          () ->
              new AlertDialog.Builder(this)
                  .setTitle("Remove management?")
                  .setMessage(
                      "Removing management allows uninstalling FocusLock. Setting up management"
                          + " again may require a clean device setup.")
                  .setNegativeButton("Keep", null)
                  .setPositiveButton(
                      "Remove",
                      (d, w) -> {
                        new FocusPolicy(this).releaseManagement();
                        render();
                      })
                  .show());
    text(activeRendered ? "Keep your promise." : "Your time.\nYour attention.", 36);
    status = text("", 20);
    update();
    if (activeRendered) {
      text(
          "Selected apps stay blocked until "
              + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                  .format(new Date(store.p.getLong("wall", 0)))
              + ". There is no end-session button.",
          16);
      text(store.apps().size() + " apps locked", 24);
      button("Add more apps to block", () -> addApps());
      if (!enabled()) button("Restore blocking permission", () -> permission());
      button(
          "Return to home",
          () -> {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            startActivity(i);
          });
    } else {
      button(enabled() ? "Blocking permission settings" : "Enable blocking", () -> permission());
      text("1 · Choose apps to block", 22);
      Intent launch = new Intent(Intent.ACTION_MAIN);
      launch.addCategory(Intent.CATEGORY_LAUNCHER);
      List<ResolveInfo> apps = getPackageManager().queryIntentActivities(launch, 0);
      apps.sort(
          (a, b) ->
              a.loadLabel(getPackageManager())
                  .toString()
                  .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
      Set<String> selected = store.apps(), seen = new HashSet<>();
      Set<String> protectedApps =
          new HashSet<>(Arrays.asList(getPackageName(), "com.android.settings"));
      Intent home = new Intent(Intent.ACTION_MAIN);
      home.addCategory(Intent.CATEGORY_HOME);
      for (ResolveInfo r : getPackageManager().queryIntentActivities(home, 0))
        protectedApps.add(r.activityInfo.packageName);
      for (ResolveInfo r : apps) {
        String pkg = r.activityInfo.packageName;
        if (protectedApps.contains(pkg) || !seen.add(pkg)) continue;
        CheckBox box = new CheckBox(this);
        box.setText(r.loadLabel(getPackageManager()));
        box.setTextColor(Color.WHITE);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.rgb(183, 247, 118)));
        box.setChecked(selected.contains(pkg));
        box.setPadding(0, dp(5), 0, dp(5));
        body.addView(box);
        box.setOnCheckedChangeListener(
            (v, checked) -> {
              if (checked) selected.add(pkg);
              else selected.remove(pkg);
              store.selection(selected);
            });
      }
      text(
          "Include browsers if you want to block social media websites too. Keep calling and"
              + " essential apps available.",
          14);
      text("2 · Set your focus time", 22);
      LinearLayout presets = new LinearLayout(this);
      body.addView(presets);
      for (int minutes : new int[] {25, 60, 120}) {
        Button b = new Button(this);
        b.setText(minutes + " min");
        presets.addView(b, new LinearLayout.LayoutParams(0, dp(55), 1));
        b.setOnClickListener(v -> confirm(minutes * 60000L));
      }
      button("Choose exact unlock date and time", () -> pickTime());
    }
    text(
        "Permission-based blocking: disabling Accessibility, uninstalling, safe mode, or some phone"
            + " power controls can bypass it. Browser pages and notifications are not filtered;"
            + " select browsers to block access through them. All data stays on your phone.",
        13);
  }

  void addApps() {
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(dp(20), dp(12), dp(20), dp(12));
    TextView explanation = new TextView(this);
    explanation.setText(
        "Added apps are blocked immediately until the current deadline. You cannot remove them"
            + " during this session.");
    list.addView(explanation);
    Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> apps = getPackageManager().queryIntentActivities(launch, 0);
    apps.sort(
        (a, b) ->
            a.loadLabel(getPackageManager())
                .toString()
                .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
    Set<String> seen = new HashSet<>(),
        protectedApps = new HashSet<>(Arrays.asList(getPackageName(), "com.android.settings"));
    Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    for (ResolveInfo info : getPackageManager().queryIntentActivities(home, 0))
      protectedApps.add(info.activityInfo.packageName);
    for (ResolveInfo info : apps) {
      String pkg = info.activityInfo.packageName;
      if (protectedApps.contains(pkg) || !seen.add(pkg)) continue;
      CheckBox box = new CheckBox(this);
      box.setText(info.loadLabel(getPackageManager()));
      boolean locked = store.apps().contains(pkg);
      box.setChecked(locked);
      box.setEnabled(!locked);
      list.addView(box);
      box.setOnCheckedChangeListener(
          (v, checked) -> {
            if (!checked) return;
            if (store.remaining() == 0) {
              Toast.makeText(
                      this,
                      "Session finished. Close this list to edit your selection.",
                      Toast.LENGTH_SHORT)
                  .show();
              box.setChecked(false);
              return;
            }
            Set<String> requested = store.apps();
            requested.add(pkg);
            if (!store.selection(requested)) {
              box.setChecked(false);
              Toast.makeText(this, "Could not save app. Try again.", Toast.LENGTH_SHORT).show();
              return;
            }
            box.setEnabled(false);
            try {
              new FocusPolicy(this).sync();
            } catch (RuntimeException error) {
              android.util.Log.e("FocusLock", "Added-app policy sync failed", error);
            }
            Toast.makeText(this, "Added until your current unlock time", Toast.LENGTH_SHORT).show();
          });
    }
    ScrollView scroll = new ScrollView(this);
    scroll.addView(list);
    new AlertDialog.Builder(this)
        .setTitle("Add more blocked apps")
        .setView(scroll)
        .setPositiveButton("Done", (d, w) -> render())
        .setOnDismissListener(d -> render())
        .show();
  }

  void pickTime() {
    Calendar date = Calendar.getInstance();
    new DatePickerDialog(
            this,
            (v, y, m, d) -> {
              date.set(y, m, d);
              new TimePickerDialog(
                      this,
                      (t, h, min) -> {
                        date.set(Calendar.HOUR_OF_DAY, h);
                        date.set(Calendar.MINUTE, min);
                        date.set(Calendar.SECOND, 0);
                        date.set(Calendar.MILLISECOND, 0);
                        confirm(date.getTimeInMillis() - System.currentTimeMillis());
                      },
                      date.get(Calendar.HOUR_OF_DAY),
                      date.get(Calendar.MINUTE),
                      true)
                  .show();
            },
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH))
        .show();
  }

  void confirm(long duration) {
    if (!enabled()) {
      permission();
      return;
    }
    if (store.apps().isEmpty()) {
      Toast.makeText(this, "Select at least one app", Toast.LENGTH_SHORT).show();
      return;
    }
    if (duration <= 0) {
      Toast.makeText(this, "Choose a future time", Toast.LENGTH_SHORT).show();
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle("Commit to focus?")
        .setMessage(
            "Block "
                + store.apps().size()
                + " apps for "
                + format(duration)
                + "? You can add more apps, but cannot remove blocked apps or cancel the session"
                + " until the timer ends.")
        .setNegativeButton("Go back", null)
        .setPositiveButton(
            "Start focus",
            (d, w) -> {
              if (!enabled()) {
                permission();
                return;
              }
              try {
                if (store.start(duration)) {
                  new FocusPolicy(this).sync();
                  render();
                } else Toast.makeText(this, "Could not start session", Toast.LENGTH_SHORT).show();
              } catch (RuntimeException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
              }
            })
        .show();
  }
}

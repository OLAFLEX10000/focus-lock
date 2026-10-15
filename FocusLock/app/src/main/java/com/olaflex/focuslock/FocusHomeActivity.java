package com.olaflex.focuslock;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** A text-only home screen. Selected apps never appear while a session is active. */
public class FocusHomeActivity extends Activity {
  private final Handler handler = new Handler();
  private Store store;
  private TextView timer;
  private boolean renderedActive;
  private final Runnable tick =
      new Runnable() {
        public void run() {
          if ((store.remaining() > 0) != renderedActive) render();
          timer.setText(
              store.remaining() > 0
                  ? "Focus ends in " + MainActivity.format(store.remaining())
                  : "Choose what matters.");
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    store = new Store(this);
    render();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    render();
  }

  @Override
  public void onResume() {
    super.onResume();
    render();
    handler.removeCallbacks(tick);
    handler.post(tick);
  }

  @Override
  public void onPause() {
    handler.removeCallbacks(tick);
    super.onPause();
  }

  private void render() {
    renderedActive = store.remaining() > 0;
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(Color.BLACK);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(1);
    int pad = (int) (24 * getResources().getDisplayMetrics().density);
    body.setPadding(pad, pad * 2, pad, pad * 2);
    scroll.addView(body);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (v, i) -> {
          body.setPadding(
              pad, pad + i.getSystemWindowInsetTop(), pad, pad + i.getSystemWindowInsetBottom());
          return i;
        });
    TextView title = new TextView(this);
    title.setText("FOCUS");
    title.setTextColor(Color.WHITE);
    title.setTextSize(30);
    body.addView(title);
    timer = new TextView(this);
    timer.setTextColor(Color.LTGRAY);
    timer.setTextSize(16);
    timer.setPadding(0, pad, 0, pad);
    body.addView(timer);
    add(body, "Focus settings", () -> startActivity(new Intent(this, MainActivity.class)));
    Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> apps = getPackageManager().queryIntentActivities(query, 0);
    apps.sort(
        (a, b) ->
            a.loadLabel(getPackageManager())
                .toString()
                .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
    Set<String> seen = new HashSet<>(), blocked = store.apps();
    for (ResolveInfo app : apps) {
      String pkg = app.activityInfo.packageName;
      if (pkg.equals(getPackageName())
          || !seen.add(pkg)
          || (renderedActive && blocked.contains(pkg))) continue;
      add(
          body,
          app.loadLabel(getPackageManager()).toString(),
          () -> {
            // Recheck at the moment of launch, including if the list predates a session.
            if (store.remaining() > 0 && store.apps().contains(pkg)) return;
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) startActivity(launch);
          });
    }
  }

  private void add(LinearLayout body, String name, Runnable action) {
    TextView text = new TextView(this);
    text.setText(name);
    text.setTextSize(20);
    text.setTextColor(Color.WHITE);
    text.setGravity(Gravity.CENTER_VERTICAL);
    int h = (int) (52 * getResources().getDisplayMetrics().density);
    body.addView(text, new LinearLayout.LayoutParams(-1, h));
    text.setOnClickListener(v -> action.run());
  }
}

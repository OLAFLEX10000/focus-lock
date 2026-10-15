package com.olaflex.focuslock;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

public class BlockService extends AccessibilityService {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Store store;
  private TextView shield;
  private long lastHome;
  private long blockedAt;
  private long lastPolicy;
  private final Runnable check =
      new Runnable() {
        public void run() {
          long now = SystemClock.elapsedRealtime();
          if (now - lastPolicy >= 1000) {
            lastPolicy = now;
            try {
              new FocusPolicy(BlockService.this).sync();
            } catch (RuntimeException error) {
              android.util.Log.e("FocusLock", "Policy sync failed", error);
            }
          }
          if (store.remaining() == 0) hideShield();
          else {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
              CharSequence name = root.getPackageName();
              String pkg = name == null ? "" : name.toString();
              root.recycle();
              if (store.apps().contains(pkg)) block();
              else if (!pkg.equals(getPackageName())
                  && SystemClock.elapsedRealtime() - blockedAt > 500) hideShield();
            }
            // Even when an app supplies no root, retry while the shield protects the screen.
            if (shield != null) goHome();
          }
          handler.postDelayed(this, 300);
        }
      };

  @Override
  protected void onServiceConnected() {
    store = new Store(this);
    handler.removeCallbacks(check);
    handler.post(check);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (store == null) store = new Store(this);
    CharSequence pkg = event.getPackageName();
    if (store.remaining() > 0 && pkg != null && store.apps().contains(pkg.toString())) block();
  }

  private void block() {
    blockedAt = SystemClock.elapsedRealtime();
    if (shield == null) {
      TextView view = new TextView(this);
      view.setText("FocusLock\n\nThis app is locked until your focus time ends.");
      view.setTextColor(Color.rgb(183, 247, 118));
      view.setTextSize(25);
      view.setGravity(Gravity.CENTER);
      view.setBackgroundColor(Color.rgb(15, 25, 22));
      view.setOnClickListener(v -> goHome());
      WindowManager.LayoutParams params =
          new WindowManager.LayoutParams(
              -1,
              -1,
              WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                  | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
              PixelFormat.OPAQUE);
      try {
        ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(view, params);
        shield = view;
      } catch (RuntimeException error) {
        android.util.Log.e("FocusLock", "Could not show blocking screen", error);
      }
    }
    goHome();
  }

  private void goHome() {
    long now = SystemClock.elapsedRealtime();
    if (now - lastHome >= 250) {
      lastHome = now;
      performGlobalAction(GLOBAL_ACTION_HOME);
    }
  }

  private void hideShield() {
    if (shield != null) {
      ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(shield);
      shield = null;
    }
  }

  @Override
  public void onInterrupt() {
    hideShield();
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacks(check);
    hideShield();
    super.onDestroy();
  }
}

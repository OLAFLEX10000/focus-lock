package com.olaflex.focuslock;

import android.content.*;

public class FocusDeadlineReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))
      new Store(context).p.edit().remove("scheduledEnd").commit();
    try {
      new FocusPolicy(context).sync();
    } catch (RuntimeException error) {
      android.util.Log.e("FocusLock", "Deadline policy update failed", error);
    }
  }
}

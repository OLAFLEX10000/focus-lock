package com.olaflex.focuslock;

public final class SessionClock {
  public static long remaining(
      long endWall, long endElapsed, int savedBoot, long wall, long elapsed, int boot) {
    if (endWall <= 0) return 0;
    return Math.max(0, savedBoot == boot ? endElapsed - elapsed : endWall - wall);
  }

  public static long[] extend(
      long endWall, long endElapsed, int savedBoot, long wall, long elapsed, int boot, long extra) {
    long left = remaining(endWall, endElapsed, savedBoot, wall, elapsed, boot);
    if (left <= 0 || extra <= 0) {
      throw new IllegalArgumentException(
          "Only active sessions can be extended by a positive amount.");
    }
    long duration = Math.addExact(left, extra);
    // Preserve the saved wall deadline even if the device clock has moved backward.
    long nextWall = Math.max(Math.addExact(endWall, extra), Math.addExact(wall, duration));
    return new long[] {nextWall, Math.addExact(elapsed, duration)};
  }
}

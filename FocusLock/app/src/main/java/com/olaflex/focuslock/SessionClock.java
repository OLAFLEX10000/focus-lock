package com.olaflex.focuslock;

public final class SessionClock {
  public static long remaining(
      long endWall, long endElapsed, int savedBoot, long wall, long elapsed, int boot) {
    if (endWall <= 0) return 0;
    return Math.max(0, savedBoot == boot ? endElapsed - elapsed : endWall - wall);
  }
}

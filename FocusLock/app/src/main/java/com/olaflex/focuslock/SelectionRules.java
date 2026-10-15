package com.olaflex.focuslock;

import java.util.HashSet;
import java.util.Set;

/** Active commitments are monotonic: selection can grow, but cannot shrink. */
public final class SelectionRules {
  public static Set<String> apply(Set<String> current, Set<String> requested, boolean active) {
    Set<String> result = new HashSet<>(requested);
    if (active) result.addAll(current);
    return result;
  }
}

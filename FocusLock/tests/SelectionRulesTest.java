import com.olaflex.focuslock.SelectionRules;
import java.util.*;

public class SelectionRulesTest {
  static Set<String> set(String... values) {
    return new HashSet<>(Arrays.asList(values));
  }

  static void check(Set<String> result, Set<String> expected) {
    if (!result.equals(expected)) throw new AssertionError(result + " != " + expected);
  }

  public static void main(String[] args) {
    Set<String> current = set("linkedin", "youtube"), requested = set("telegram");
    check(SelectionRules.apply(current, requested, true), set("linkedin", "youtube", "telegram"));
    check(SelectionRules.apply(current, set(), true), current);
    check(SelectionRules.apply(current, set("linkedin"), true), current);
    check(SelectionRules.apply(current, requested, false), requested);
    check(SelectionRules.apply(current, set(), false), set());
    check(current, set("linkedin", "youtube"));
    check(requested, set("telegram"));
    System.out.println("7 selection checks passed");
  }
}

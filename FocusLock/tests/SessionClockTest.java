import com.olaflex.focuslock.SessionClock;

public class SessionClockTest {
  static void check(long actual, long expected) {
    if (actual != expected) throw new AssertionError(actual + " != " + expected);
  }

  public static void main(String[] a) {
    check(SessionClock.remaining(10000, 8000, 1, 5000, 3000, 1), 5000);
    check(SessionClock.remaining(10000, 8000, 1, 999999, 3000, 1), 5000);
    check(SessionClock.remaining(10000, 8000, 1, 0, 3000, 1), 5000);
    check(SessionClock.remaining(10000, 8000, 1, 7000, 100, 2), 3000);
    check(SessionClock.remaining(10000, 8000, 1, 11000, 100, 2), 0);
    check(SessionClock.remaining(10000, 8000, 1, 5000, 9000, 1), 0);
    check(SessionClock.remaining(0, 0, -2, 5000, 3000, 1), 0);
    System.out.println("7 session-clock checks passed");
  }
}

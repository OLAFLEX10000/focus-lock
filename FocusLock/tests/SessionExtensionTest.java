import com.olaflex.focuslock.SessionClock;

public class SessionExtensionTest {
  private static int checks;

  static void check(long actual, long expected) {
    if (actual != expected) throw new AssertionError(actual + " != " + expected);
    checks++;
  }

  static void rejects(Runnable request) {
    try {
      request.run();
      throw new AssertionError("Invalid extension accepted");
    } catch (IllegalArgumentException | ArithmeticException expected) {
      checks++;
    }
  }

  public static void main(String[] args) {
    long[] next = SessionClock.extend(10000, 8000, 1, 5000, 3000, 1, 2000);
    check(next[0], 12000);
    check(next[1], 10000);
    check(SessionClock.remaining(next[0], next[1], 1, 5000, 3000, 1), 7000);
    check(SessionClock.remaining(next[0], next[1], 1, 8000, 100, 2), 4000);

    long[] repeated = SessionClock.extend(next[0], next[1], 1, 6000, 4000, 1, 1000);
    check(repeated[0], 13000);
    check(repeated[1], 11000);

    long[] backward = SessionClock.extend(10000, 8000, 1, 1000, 3000, 1, 2000);
    check(backward[0], 12000);
    check(backward[1], 10000);
    long[] forward = SessionClock.extend(10000, 8000, 1, 100000, 3000, 1, 2000);
    check(forward[0], 107000);
    check(forward[1], 10000);

    long[] reboot = SessionClock.extend(10000, 8000, 1, 7000, 100, 2, 2000);
    check(reboot[0], 12000);
    check(reboot[1], 5100);
    check(SessionClock.remaining(reboot[0], reboot[1], 2, 7000, 100, 2), 5000);

    rejects(() -> SessionClock.extend(10000, 8000, 1, 5000, 3000, 1, 0));
    rejects(() -> SessionClock.extend(10000, 8000, 1, 5000, 3000, 1, -1));
    rejects(() -> SessionClock.extend(10000, 8000, 1, 5000, 8000, 1, 2000));
    rejects(() -> SessionClock.extend(10000, 8000, 1, 10000, 100, 2, 2000));
    rejects(() -> SessionClock.extend(0, 0, -2, 5000, 3000, 1, 2000));
    rejects(() -> SessionClock.extend(10000, 8000, 1, 5000, 3000, 1, Long.MAX_VALUE));
    rejects(() -> SessionClock.extend(Long.MAX_VALUE, 8000, 1, 5000, 3000, 1, 1));
    rejects(() -> SessionClock.extend(10000, Long.MAX_VALUE, 1, 5000, Long.MAX_VALUE - 5, 1, 10));
    System.out.println(checks + " session-extension checks passed");
  }
}

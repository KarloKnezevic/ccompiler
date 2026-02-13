package hr.fer.ppj.opt.rules.arith;

/**
 * Q16.16 float semantics used for safe constant folding.
 */
public final class Q16FloatSemantics {

  private Q16FloatSemantics() {
  }

  public static int toRaw(float value) {
    return Math.round(value * 65536.0f);
  }

  public static float toFloat(int raw) {
    return raw / 65536.0f;
  }

  public static int addRaw(int left, int right) {
    return left + right;
  }

  public static int subRaw(int left, int right) {
    return left - right;
  }

  public static int mulRaw(int left, int right) {
    long product = (long) left * (long) right;
    return (int) (product >> 16);
  }

  public static int divRaw(int left, int right) {
    if (right == 0) {
      return 0;
    }
    long numerator = ((long) left) << 16;
    return (int) (numerator / right);
  }

  public static boolean isRoundTripStable(int raw) {
    return toRaw(toFloat(raw)) == raw;
  }
}

package hr.fer.ppj.opt.rules.arith;

/**
 * Explicit int32 semantics used by optimizer rules.
 */
public final class Int32Semantics {

  private Int32Semantics() {
  }

  public static int negate(int value) {
    return -value;
  }

  public static int divide(int left, int right) {
    if (left == Integer.MIN_VALUE && right == -1) {
      return Integer.MIN_VALUE;
    }
    return left / right;
  }

  public static int modulo(int left, int right) {
    if (left == Integer.MIN_VALUE && right == -1) {
      return 0;
    }
    return left % right;
  }

  public static boolean isPowerOfTwo(int value) {
    return value > 0 && (value & (value - 1)) == 0;
  }

  public static int powerOfTwoShiftAmount(int value) {
    return Integer.numberOfTrailingZeros(value);
  }
}

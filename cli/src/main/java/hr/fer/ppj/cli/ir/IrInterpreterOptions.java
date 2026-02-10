package hr.fer.ppj.cli.ir;

/**
 * Runtime options for IR interpreter execution.
 *
 * @param stepLimit hard upper bound on executed instructions/terminators
 * @param trace whether to collect execution trace lines
 */
public record IrInterpreterOptions(int stepLimit, boolean trace) {

  public static final int DEFAULT_STEP_LIMIT = 2_000_000;

  public static IrInterpreterOptions defaults() {
    return new IrInterpreterOptions(DEFAULT_STEP_LIMIT, false);
  }

  public IrInterpreterOptions {
    if (stepLimit <= 0) {
      throw new IllegalArgumentException("stepLimit must be positive");
    }
  }
}

package hr.fer.ppj.cli.vm;

/**
 * Runtime options for bytecode VM execution.
 *
 * @param dispatchLimit hard upper bound on dispatched bytecode instructions (the VM's watchdog)
 * @param trace whether to collect a per-dispatch execution trace
 */
public record VmExecutionOptions(long dispatchLimit, boolean trace) {

  /**
   * Default dispatch ceiling. The VM's unit is one dispatched bytecode op, which is finer-grained
   * than the IR interpreter's one-IR-instruction step, so the ceiling is scaled up accordingly.
   */
  public static final long DEFAULT_DISPATCH_LIMIT = 16_000_000L;

  public static VmExecutionOptions defaults() {
    return new VmExecutionOptions(DEFAULT_DISPATCH_LIMIT, false);
  }

  public VmExecutionOptions {
    if (dispatchLimit <= 0) {
      throw new IllegalArgumentException("dispatchLimit must be positive");
    }
  }
}

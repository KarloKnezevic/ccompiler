package hr.fer.ppj.ir.lowering;

import java.util.Objects;

/**
 * Manages loop context for break/continue statements.
 *
 * <p>This context tracks:
 * <ul>
 *   <li>Exit label (for break statements)</li>
 *   <li>Continue label (for continue statements)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LoopContext {

  private final String exitLabel;
  private final String continueLabel;

  public LoopContext(String exitLabel, String continueLabel) {
    this.exitLabel = exitLabel; // Can be null if no exit label
    this.continueLabel = continueLabel; // Can be null if no continue label
  }

  public String exitLabel() {
    return exitLabel;
  }

  public String continueLabel() {
    return continueLabel;
  }

  public static LoopContext empty() {
    return new LoopContext(null, null);
  }
}

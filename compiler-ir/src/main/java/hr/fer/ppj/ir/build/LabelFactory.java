package hr.fer.ppj.ir.build;

/**
 * Factory for creating block labels with deterministic numbering.
 *
 * <p>Labels are created in order: L0, L1, L2, ...
 * This ensures deterministic IR output for golden tests.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LabelFactory {

  private int nextIndex = 0;

  /**
   * Creates a new label.
   *
   * @return a new label (L0, L1, L2, ...)
   */
  public String newLabel() {
    int index = nextIndex++;
    return "L" + index;
  }

  /**
   * Resets the factory (for reuse in tests or multiple passes).
   */
  public void reset() {
    nextIndex = 0;
  }

  /**
   * Returns the next index that will be used (for debugging).
   */
  public int getNextIndex() {
    return nextIndex;
  }
}


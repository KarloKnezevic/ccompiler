package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.types.IrType;

/**
 * Factory for creating temporary variables with deterministic numbering.
 *
 * <p>Temps are created in order: t0, t1, t2, ...
 * This ensures deterministic IR output for golden tests.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TempFactory {

  private int nextIndex = 0;

  /**
   * Creates a new temporary with the given type.
   *
   * @param type the type of the temporary
   * @return a new temporary (t0, t1, t2, ...)
   */
  public IrTemp newTemp(IrType type) {
    int index = nextIndex++;
    return new IrTemp(index, type);
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


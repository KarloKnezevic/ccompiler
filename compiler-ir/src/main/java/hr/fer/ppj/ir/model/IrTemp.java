package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Temporary variable: t0, t1, t2, ...
 *
 * <p>Temps are created in order and numbered deterministically.
 *
 * @param index the temp index (0, 1, 2, ...)
 * @param type the type of the temporary
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrTemp(int index, IrType type) implements IrValue {

  public IrTemp {
    if (index < 0) {
      throw new IllegalArgumentException("Temp index must be non-negative");
    }
    Objects.requireNonNull(type, "type must not be null");
  }

  /**
   * Returns the IR string representation: "t0", "t1", etc.
   */
  public String toIrString() {
    return "t" + index;
  }
}


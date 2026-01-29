package hr.fer.ppj.ir.types;

import java.util.Objects;

/**
 * Array type: array<T,N>.
 *
 * @param elementType the type of each element
 * @param size the array size (compile-time constant)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrArrayType(IrType elementType, int size) implements IrType {

  public IrArrayType {
    Objects.requireNonNull(elementType, "elementType must not be null");
    if (size < 0) {
      throw new IllegalArgumentException("Array size must be non-negative");
    }
  }

  @Override
  public String toIrString() {
    return "array<" + elementType.toIrString() + "," + size + ">";
  }
}


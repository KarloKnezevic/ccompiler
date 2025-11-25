package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Represents an array type whose elements share the same type.
 *
 * @param elementType element type (never {@link PrimitiveType#VOID})
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ArrayType(Type elementType) implements Type {

  public ArrayType {
    Objects.requireNonNull(elementType, "elementType must not be null");
    if (elementType.isVoid()) {
      throw new IllegalArgumentException("Array element type cannot be void");
    }
  }

  @Override
  public boolean isScalar() {
    return false;
  }
}


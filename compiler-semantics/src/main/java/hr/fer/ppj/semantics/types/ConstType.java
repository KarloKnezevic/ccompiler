package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Represents a const-qualified type.
 *
 * @param baseType underlying, non-void type
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ConstType(Type baseType) implements Type {

  public ConstType {
    Objects.requireNonNull(baseType, "baseType must not be null");
    if (baseType.isVoid()) {
      throw new IllegalArgumentException("Cannot apply const to void");
    }
  }

  @Override
  public boolean isScalar() {
    return baseType.isScalar();
  }

  @Override
  public boolean isVoid() {
    return baseType.isVoid();
  }
}


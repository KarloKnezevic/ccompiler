package hr.fer.ppj.semantics.types;

/**
 * Base marker interface representing a ppjC type used during semantic analysis.
 *
 * <p>Types are immutable value objects and can be freely shared between parse tree nodes.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Type permits PrimitiveType, ArrayType, FunctionType, ConstType {

  /**
   * Indicates whether the type represents the {@code void} type.
   *
   * @return {@code true} for {@link PrimitiveType#VOID}, {@code false} otherwise
   */
  default boolean isVoid() {
    return this == PrimitiveType.VOID;
  }

  /**
   * Indicates whether the type can be used in arithmetic/logical expressions
   * (i.e. {@code int} or {@code char}).
   */
  default boolean isScalar() {
    return this == PrimitiveType.INT || this == PrimitiveType.CHAR;
  }
}


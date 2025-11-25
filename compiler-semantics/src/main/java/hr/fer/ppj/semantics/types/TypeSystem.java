package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Helper utilities for reasoning about ppjC types.
 *
 * <p>The implementation closely follows the conversion and compatibility rules from the PPJ
 * laboratory specification.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeSystem {

  private TypeSystem() {}

  /**
    * Checks whether a value of {@code source} type can be implicitly assigned to a variable
    * of {@code target} type.
    *
    * @param source the source type
    * @param target the target type
    * @return {@code true} if assignment is legal
    */
  public static boolean canAssign(Type source, Type target) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");

    if (target instanceof ArrayType || target instanceof FunctionType) {
      // Arrays and functions cannot appear on the left-hand side except as references.
      return source.equals(target);
    }

    Type unqualifiedTarget = stripConst(target);
    Type unqualifiedSource = stripConst(source);

    if (unqualifiedTarget == PrimitiveType.INT) {
      return unqualifiedSource == PrimitiveType.INT || unqualifiedSource == PrimitiveType.CHAR;
    }

    if (unqualifiedTarget == PrimitiveType.CHAR) {
      return unqualifiedSource == PrimitiveType.CHAR;
    }

    if (unqualifiedTarget == PrimitiveType.VOID) {
      return false;
    }

    return false;
  }

  /**
   * Determines whether the provided type can be converted to {@code int} in boolean contexts.
   */
  public static boolean isIntConvertible(Type type) {
    return type == PrimitiveType.INT || type == PrimitiveType.CHAR;
  }

  /**
   * Returns the promoted result type of a binary arithmetic operation.
   *
   * @throws IllegalArgumentException if operands are not arithmetic
   */
  public static Type arithmeticResult(Type lhs, Type rhs) {
    if (!lhs.isScalar() || !rhs.isScalar()) {
      throw new IllegalArgumentException("Operands must be scalar types");
    }
    return PrimitiveType.INT; // all arithmetic promotes to int per specification
  }

  /**
   * Strips const qualifiers from the provided type.
   */
  public static Type stripConst(Type type) {
    Objects.requireNonNull(type, "type must not be null");
    return type instanceof ConstType constType ? constType.baseType() : type;
  }

  public static boolean isConst(Type type) {
    return type instanceof ConstType;
  }

  public static Type withConst(Type type) {
    if (type instanceof ConstType) {
      return type;
    }
    return new ConstType(stripConst(type));
  }

  /**
   * Checks whether two types are identical after removing const qualifiers.
   */
  public static boolean equalsIgnoringConst(Type left, Type right) {
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(right, "right must not be null");
    return stripConst(left).equals(stripConst(right));
  }

  /**
   * Determines whether an explicit cast from {@code source} to {@code target} is legal.
   *
   * <p>ppjC only supports casts between scalar types.
   */
  public static boolean canCast(Type source, Type target) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");
    if (target instanceof ArrayType || target instanceof FunctionType) {
      return false;
    }
    Type baseSource = stripConst(source);
    Type baseTarget = stripConst(target);
    if (baseTarget.isVoid()) {
      return false;
    }
    return baseSource.isScalar() && baseTarget.isScalar();
  }
}


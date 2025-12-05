package hr.fer.ppj.semantics.types;

/**
 * Type promotion and conversion utilities for PPJ-C.
 *
 * <p>This class implements C's type promotion rules, specifically:
 * <ul>
 *   <li><strong>Usual arithmetic conversions</strong>: Determines the result type of
 *       binary arithmetic operations</li>
 *   <li><strong>Scalar type checks</strong>: Determines if a type can be used in boolean
 *       contexts (control flow conditions)</li>
 * </ul>
 *
 * <p>All methods are static utility methods. This class cannot be instantiated.
 *
 * <p>C's usual arithmetic conversions:
 * <ol>
 *   <li>If either operand is {@code float}, the result is {@code float}</li>
 *   <li>Otherwise, both operands are promoted to {@code int}, and the result is {@code int}</li>
 * </ol>
 *
 * <p>Scalar types (can be used in boolean contexts):
 * <ul>
 *   <li>Arithmetic types: {@code int}, {@code char}, {@code float}</li>
 *   <li>Pointer types: Any pointer type</li>
 * </ul>
 *
 * <p>This class is used throughout semantic analysis to:
 * <ul>
 *   <li>Determine result types of arithmetic operations</li>
 *   <li>Validate control flow conditions (if, while, for)</li>
 *   <li>Check operand types for binary operators</li>
 * </ul>
 *
 * @see TypeSystem for the public API (delegates to this class)
 * @see Type for the base type interface
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypePromotion {
  
  private TypePromotion() {}
  
  /**
   * Determines whether the provided type can be converted to {@code int} in boolean contexts.
   *
   * <p>In C, scalar types can be used in boolean contexts (if conditions, while loops, for loops,
   * logical operators). This method checks if a type is scalar and can therefore be used in
   * these contexts.
   *
   * <p>Scalar types include:
   * <ul>
   *   <li>Arithmetic types: {@code int}, {@code char}, {@code float}</li>
   *   <li>Pointer types: Any pointer type (e.g., {@code int*}, {@code struct Node*})</li>
   * </ul>
   *
   * <p>Non-scalar types (cannot be used in boolean contexts):
   * <ul>
   *   <li>{@code void} - cannot be used in expressions</li>
   *   <li>Arrays - decay to pointers in most contexts</li>
   *   <li>Structs - cannot be used in arithmetic or boolean contexts</li>
   *   <li>Functions - cannot be used as values</li>
   * </ul>
   *
   * <p>This method is used during semantic analysis to validate:
   * <ul>
   *   <li>If statement conditions: {@code if (expr)}</li>
   *   <li>While loop conditions: {@code while (expr)}</li>
   *   <li>For loop conditions: {@code for (init; condition; update)}</li>
   *   <li>Logical operator operands: {@code expr1 && expr2}</li>
   * </ul>
   *
   * @param type the type to check
   * @return {@code true} if the type is scalar and can be used in boolean contexts,
   *         {@code false} otherwise (including if type is null)
   */
  public static boolean isIntConvertible(Type type) {
    if (type == null) {
      return false;
    }
    Type stripped = TypeSystem.stripConst(type);
    return stripped == PrimitiveType.INT 
        || stripped == PrimitiveType.CHAR
        || stripped == PrimitiveType.FLOAT
        || stripped instanceof PointerType;
  }
  
  /**
   * Returns the promoted result type of a binary arithmetic operation.
   *
   * <p>This method implements C's usual arithmetic conversions, which determine the result
   * type of binary arithmetic operations ({@code +}, {@code -}, {@code *}, {@code /}, {@code %}).
   *
   * <p>Conversion rules:
   * <ol>
   *   <li>If either operand is {@code float}, the result is {@code float}</li>
   *   <li>Otherwise, both operands are promoted to {@code int}, and the result is {@code int}</li>
   * </ol>
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code arithmeticResult(int, int)} → {@code int}</li>
   *   <li>{@code arithmeticResult(char, int)} → {@code int}</li>
   *   <li>{@code arithmeticResult(int, float)} → {@code float}</li>
   *   <li>{@code arithmeticResult(float, float)} → {@code float}</li>
   * </ul>
   *
   * <p>This method is used during semantic analysis to:
   * <ul>
   *   <li>Determine the result type of arithmetic expressions</li>
   *   <li>Validate operand types for arithmetic operators</li>
   *   <li>Annotate parse tree nodes with computed types</li>
   * </ul>
   *
   * <p>Note: Const qualification is stripped from both operands before checking.
   *
   * @param lhs the left-hand side operand type (must be scalar)
   * @param rhs the right-hand side operand type (must be scalar)
   * @return the promoted result type (either {@code int} or {@code float})
   * @throws IllegalArgumentException if either operand is not a scalar type
   */
  public static Type arithmeticResult(Type lhs, Type rhs) {
    if (!lhs.isScalar() || !rhs.isScalar()) {
      throw new IllegalArgumentException("Operands must be scalar types");
    }
    Type lhsStripped = TypeSystem.stripConst(lhs);
    Type rhsStripped = TypeSystem.stripConst(rhs);
    
    if (lhsStripped == PrimitiveType.FLOAT || rhsStripped == PrimitiveType.FLOAT) {
      return PrimitiveType.FLOAT;
    }
    
    return PrimitiveType.INT;
  }
}


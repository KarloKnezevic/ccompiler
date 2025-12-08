package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Core type system utilities for PPJ-C.
 *
 * <p>This class serves as the main entry point for type operations in the semantic analyzer.
 * It provides a unified API for type compatibility checking, type promotion, and const
 * qualification manipulation. The implementation delegates to specialized utility classes
 * for specific operations:
 * <ul>
 *   <li><strong>Type compatibility</strong>: Delegates to {@link TypeCompatibility} for
 *       assignment compatibility, cast validity, and type equality checks</li>
 *   <li><strong>Type promotion</strong>: Delegates to {@link TypePromotion} for arithmetic
 *       conversions and scalar type checks</li>
 * </ul>
 *
 * <p>This class provides direct operations for:
 * <ul>
 *   <li><strong>Const qualification</strong>: Stripping, checking, and applying const qualifiers</li>
 *   <li><strong>Type queries</strong>: Delegating to specialized classes for compatibility and promotion</li>
 * </ul>
 *
 * <p>All methods are static utility methods. This class cannot be instantiated.
 *
 * <p>Usage examples:
 * <pre>{@code
 * // Check if assignment is legal
 * if (TypeSystem.canAssign(sourceType, targetType)) {
 *     // Assignment is valid
 * }
 *
 * // Apply C's usual arithmetic conversions
 * Type resultType = TypeSystem.arithmeticResult(leftType, rightType);
 *
 * // Strip const qualification for comparison
 * Type baseType = TypeSystem.stripConst(constQualifiedType);
 * }</pre>
 *
 * @see TypeCompatibility for detailed assignment and cast compatibility rules
 * @see TypePromotion for arithmetic conversion rules
 * @see Type for the base type interface
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeSystem {

  private TypeSystem() {}

  /**
   * Checks whether a value of {@code source} type can be implicitly assigned to a variable
   * of {@code target} type.
   *
   * <p>This method implements C's assignment compatibility rules:
   * <ul>
   *   <li>Numeric types: char → int → float (with implicit conversions)</li>
   *   <li>Pointers: Same base type (ignoring const on pointed-to type), or int 0 → any pointer</li>
   *   <li>Arrays: Decay to pointers in assignment contexts</li>
   *   <li>Structs: Exact type match required</li>
   *   <li>Const: Non-const can be assigned to const, but not vice versa</li>
   * </ul>
   *
   * <p>This method is used during semantic analysis to validate:
   * <ul>
   *   <li>Variable initializations</li>
   *   <li>Assignment statements</li>
   *   <li>Function return values</li>
   *   <li>Function call arguments</li>
   * </ul>
   *
   * @param source the source type (the type of the value being assigned)
   * @param target the target type (the type of the variable being assigned to)
   * @return {@code true} if assignment is legal, {@code false} otherwise
   * @throws NullPointerException if either parameter is null
   * @see TypeCompatibility#canAssign for the implementation
   */
  public static boolean canAssign(Type source, Type target) {
    return TypeCompatibility.canAssign(source, target);
  }

  /**
   * Determines whether the provided type can be converted to {@code int} in boolean contexts.
   *
   * <p>In C, scalar types can be used in boolean contexts (if conditions, while loops, etc.).
   * This method checks if a type is scalar and can therefore be used in control flow conditions.
   *
   * <p>Scalar types include:
   * <ul>
   *   <li>Arithmetic types: int, char, float</li>
   *   <li>Pointer types: Any pointer type</li>
   * </ul>
   *
   * <p>Non-scalar types (arrays, structs, functions, void) cannot be used in boolean contexts.
   *
   * @param type the type to check
   * @return {@code true} if the type is scalar and can be used in boolean contexts, {@code false} otherwise
   * @see TypePromotion#isIntConvertible for the implementation
   */
  public static boolean isIntConvertible(Type type) {
    return TypePromotion.isIntConvertible(type);
  }

  /**
   * Returns the promoted result type of a binary arithmetic operation.
   *
   * <p>This method implements C's usual arithmetic conversions:
   * <ul>
   *   <li>If either operand is {@code float}, result is {@code float}</li>
   *   <li>Otherwise, both operands are promoted to {@code int}, result is {@code int}</li>
   * </ul>
   *
   * <p>This method is used during semantic analysis to determine the result type of:
   * <ul>
   *   <li>Arithmetic operations: {@code +}, {@code -}, {@code *}, {@code /}, {@code %}</li>
   *   <li>Relational comparisons: {@code <}, {@code >}, {@code <=}, {@code >=}</li>
   *   <li>Equality comparisons: {@code ==}, {@code !=}</li>
   * </ul>
   *
   * @param lhs the left-hand side operand type
   * @param rhs the right-hand side operand type
   * @return the promoted result type (either {@code int} or {@code float})
   * @throws IllegalArgumentException if either operand is not a scalar type
   * @see TypePromotion#arithmeticResult for the implementation
   */
  public static Type arithmeticResult(Type lhs, Type rhs) {
    return TypePromotion.arithmeticResult(lhs, rhs);
  }

  /**
   * Strips const qualifiers from the provided type.
   *
   * <p>This method removes all const qualification from a type, returning the underlying
   * base type. If the type is not const-qualified, it returns the type unchanged.
   *
   * <p>This method is used when comparing types for compatibility, as const qualification
   * is typically ignored for type compatibility checks (except in assignment contexts where
   * const prevents modification).
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code stripConst(const int)} → {@code int}</li>
   *   <li>{@code stripConst(int)} → {@code int}</li>
   *   <li>{@code stripConst(const int*)} → {@code int*}</li>
   * </ul>
   *
   * @param type the type to strip const qualification from
   * @return the type without const qualification, or the original type if not const-qualified
   * @throws NullPointerException if type is null
   */
  public static Type stripConst(Type type) {
    Objects.requireNonNull(type, "type must not be null");
    return type instanceof ConstType constType ? constType.baseType() : type;
  }

  /**
   * Checks whether a type is const-qualified.
   *
   * <p>This method checks if a type is wrapped with {@link ConstType}, indicating that
   * values of this type are immutable.
   *
   * <p>Note: This checks for const on the type itself (e.g., {@code const int}). For
   * const pointers (e.g., {@code int * const}), check {@link PointerType#isConst()}.
   *
   * @param type the type to check
   * @return {@code true} if the type is const-qualified, {@code false} otherwise
   */
  public static boolean isConst(Type type) {
    return type instanceof ConstType;
  }

  /**
   * Wraps a type with const qualifier if not already const.
   *
   * <p>This method applies const qualification to a type. If the type is already
   * const-qualified, it returns the type unchanged. Otherwise, it wraps the type
   * with {@link ConstType}.
   *
   * <p>This method is used during semantic analysis when processing const-qualified
   * declarations (e.g., {@code const int x;}).
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code withConst(int)} → {@code const int}</li>
   *   <li>{@code withConst(const int)} → {@code const int} (unchanged)</li>
   * </ul>
   *
   * @param type the type to qualify as const
   * @return the const-qualified type, or the original type if already const
   */
  public static Type withConst(Type type) {
    if (type instanceof ConstType) {
      return type;
    }
    return new ConstType(stripConst(type));
  }

  /**
   * Checks whether two types are identical after removing const qualifiers.
   *
   * <p>This method compares two types for structural equality, ignoring const qualification.
   * For struct types, it compares by tag name to handle forward declarations (where a struct
   * may be forward-declared with empty fields and later defined with full fields).
   *
   * <p>This method is used when:
   * <ul>
   *   <li>Comparing pointer base types for compatibility</li>
   *   <li>Checking struct type compatibility</li>
   *   <li>Validating function parameter types</li>
   * </ul>
   *
   * <p>Special handling for structs:
   * <ul>
   *   <li>If both structs are tagged and have the same tag, they are considered equal
   *       (enables forward declarations)</li>
   *   <li>Otherwise, structs are compared by structural equality (field names and types)</li>
   * </ul>
   *
   * @param left the first type to compare
   * @param right the second type to compare
   * @return {@code true} if the types are equal ignoring const, {@code false} otherwise
   * @see TypeCompatibility#equalsIgnoringConst for the implementation
   */
  public static boolean equalsIgnoringConst(Type left, Type right) {
    return TypeCompatibility.equalsIgnoringConst(left, right);
  }

  /**
   * Determines whether an explicit cast from {@code source} to {@code target} is legal.
   *
   * <p>This method validates explicit type casts in C. Supported casts include:
   * <ul>
   *   <li>Numeric types: char, int, float can be cast to each other</li>
   *   <li>Pointers: Can be cast to pointers (if compatible base types) or integers</li>
   *   <li>Integers: Can be cast to pointers</li>
   *   <li>Void: Cast to void is allowed (discards value)</li>
   *   <li>Structs: Can only be cast to the same struct type</li>
   * </ul>
   *
   * <p>This method is used during semantic analysis to validate cast expressions
   * like {@code (int) floatValue} or {@code (struct Node*) voidPtr}.
   *
   * <p>Note: Arrays and functions cannot be cast (they must be used as-is or decay to pointers).
   *
   * @param source the source type (the type being cast from)
   * @param target the target type (the type being cast to)
   * @return {@code true} if the cast is legal, {@code false} otherwise
   * @see TypeCompatibility#canCast for the implementation
   */
  public static boolean canCast(Type source, Type target) {
    return TypeCompatibility.canCast(source, target);
  }
}

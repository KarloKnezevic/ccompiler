package hr.fer.ppj.semantics.types;

/**
 * Base sealed interface representing a PPJ-C type used during semantic analysis.
 *
 * <p>Types are immutable value objects and can be freely shared between parse tree nodes.
 * The type system supports:
 * <ul>
 *   <li><strong>Primitive types</strong>: {@code void}, {@code char}, {@code int}, {@code float}</li>
 *   <li><strong>Composite types</strong>: Arrays, pointers, structs, functions</li>
 *   <li><strong>Qualified types</strong>: {@code const}-qualified types</li>
 * </ul>
 *
 * <p>The type hierarchy is implemented using a sealed interface pattern, ensuring that
 * only the permitted implementations can exist. This provides compile-time type safety
 * and enables exhaustive pattern matching in type-checking code.
 *
 * <p>Type objects are used throughout semantic analysis to:
 * <ul>
 *   <li>Annotate parse tree nodes with computed types</li>
 *   <li>Check type compatibility in assignments and function calls</li>
 *   <li>Determine l-value/r-value status of expressions</li>
 *   <li>Validate control flow conditions (scalar types only)</li>
 * </ul>
 *
 * @see PrimitiveType for primitive type enumeration
 * @see ArrayType for array type representation
 * @see PointerType for pointer type representation
 * @see StructType for struct type representation
 * @see FunctionType for function type representation
 * @see ConstType for const-qualified type representation
 * @see TypeSystem for type operations and compatibility checking
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Type permits PrimitiveType, ArrayType, FunctionType, ConstType, PointerType, StructType {

  /**
   * Indicates whether the type represents the {@code void} type.
   *
   * <p>The {@code void} type has special semantics in C:
   * <ul>
   *   <li>Can only be used as a function return type or in function parameter lists</li>
   *   <li>Cannot be used for variable declarations</li>
   *   <li>Cannot be used in expressions</li>
   * </ul>
   *
   * @return {@code true} for {@link PrimitiveType#VOID}, {@code false} otherwise
   */
  default boolean isVoid() {
    return this == PrimitiveType.VOID;
  }

  /**
   * Indicates whether the type is a scalar type that can be used in arithmetic/logical expressions.
   *
   * <p>Scalar types in C include:
   * <ul>
   *   <li>Arithmetic types: {@code int}, {@code char}, {@code float}</li>
   *   <li>Pointer types: Any pointer type (e.g., {@code int*}, {@code struct Node*})</li>
   * </ul>
   *
   * <p>Non-scalar types include:
   * <ul>
   *   <li>{@code void} - cannot be used in expressions</li>
   *   <li>Arrays - decay to pointers in most contexts</li>
   *   <li>Structs - cannot be used in arithmetic</li>
   *   <li>Functions - cannot be used as values</li>
   * </ul>
   *
   * <p>Scalar types can be used in:
   * <ul>
   *   <li>Arithmetic operations ({@code +}, {@code -}, {@code *}, etc.)</li>
   *   <li>Relational comparisons ({@code <}, {@code >}, etc.)</li>
   *   <li>Logical operations ({@code &&}, {@code ||})</li>
   *   <li>Control flow conditions ({@code if}, {@code while}, {@code for})</li>
   * </ul>
   *
   * @return {@code true} if the type is scalar (int, char, float, or pointer), {@code false} otherwise
   */
  default boolean isScalar() {
    return this == PrimitiveType.INT 
        || this == PrimitiveType.CHAR 
        || this == PrimitiveType.FLOAT
        || this instanceof PointerType;
  }
}


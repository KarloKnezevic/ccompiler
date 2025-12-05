package hr.fer.ppj.semantics.types;

/**
 * Enumeration of primitive types in PPJ-C.
 *
 * <p>Primitive types are the fundamental building blocks of the type system:
 * <ul>
 *   <li><strong>{@link #VOID}</strong>: Used exclusively for function return types and
 *       parameter lists. Cannot be used for variable declarations or in expressions.</li>
 *   <li><strong>{@link #CHAR}</strong>: 8-bit signed integer type. Promoted to {@code int}
 *       in arithmetic operations. Can be implicitly converted to {@code int} and {@code float}.</li>
 *   <li><strong>{@link #INT}</strong>: 32-bit signed integer type. The default type for
 *       integer literals and arithmetic operations. Can be implicitly converted to {@code float}.</li>
 *   <li><strong>{@link #FLOAT}</strong>: 32-bit floating-point type. Used for floating-point
 *       literals and arithmetic. In mixed arithmetic with {@code int} or {@code char},
 *       the result is {@code float} (C's usual arithmetic conversions).</li>
 * </ul>
 *
 * <p>Type promotion rules (C's usual arithmetic conversions):
 * <ul>
 *   <li>If either operand is {@code float}, result is {@code float}</li>
 *   <li>Otherwise, both operands are promoted to {@code int}, result is {@code int}</li>
 * </ul>
 *
 * <p>Assignment compatibility:
 * <ul>
 *   <li>{@code char} can be assigned to {@code int} or {@code float}</li>
 *   <li>{@code int} can be assigned to {@code float}</li>
 *   <li>{@code int} can be assigned to {@code char} (with potential truncation)</li>
 *   <li>{@code float} can be assigned to {@code int} or {@code char} (with truncation)</li>
 * </ul>
 *
 * @see Type for the base type interface
 * @see TypeSystem for type operations and compatibility checking
 * @see TypePromotion for arithmetic conversion rules
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public enum PrimitiveType implements Type {
  /** 32-bit signed integer type. Default for integer literals and arithmetic operations. */
  INT,
  
  /** 8-bit signed integer type. Promoted to {@code int} in arithmetic operations. */
  CHAR,
  
  /** 32-bit floating-point type. Used for floating-point literals and arithmetic. */
  FLOAT,
  
  /** 
   * Void type. Used exclusively for function return types and parameter lists.
   * Cannot be used for variable declarations or in expressions.
   */
  VOID
}


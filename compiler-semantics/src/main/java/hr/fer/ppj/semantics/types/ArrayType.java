package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Represents an array type whose elements share the same type.
 *
 * <p>Arrays in PPJ-C are fixed-size arrays with compile-time constant size:
 * <ul>
 *   <li>Declaration: {@code int arr[10];} - array of 10 integers</li>
 *   <li>Element type: Can be any non-void type (primitives, structs, pointers, other arrays)</li>
 *   <li>Size: Must be a compile-time constant (integer literal)</li>
 * </ul>
 *
 * <p>Array semantics:
 * <ul>
 *   <li><strong>Not scalar</strong>: Arrays themselves are not scalar types</li>
 *   <li><strong>Array-to-pointer decay</strong>: In most contexts, arrays decay to pointers
 *       to their first element (e.g., in function parameters, assignment contexts)</li>
 *   <li><strong>Indexing</strong>: {@code array[index]} is equivalent to {@code *(array + index)}
 *       after array-to-pointer decay</li>
 *   <li><strong>Not assignable</strong>: Arrays cannot be assigned directly (they decay to
 *       pointers in assignment contexts)</li>
 * </ul>
 *
 * <p>Array-to-pointer decay:
 * <ul>
 *   <li>Function parameters: {@code void f(int arr[])} is equivalent to {@code void f(int *arr)}</li>
 *   <li>Assignment: {@code int *p = arr;} - array decays to pointer</li>
 *   <li>Arithmetic: {@code arr + 1} - array decays to pointer</li>
 * </ul>
 *
 * <p>Array indexing:
 * <ul>
 *   <li>Base must be an array type or pointer type</li>
 *   <li>Index must be an integer type (int, char, or convertible to int)</li>
 *   <li>Result type is the element type</li>
 *   <li>Result is an l-value (can be assigned to)</li>
 * </ul>
 *
 * @param elementType the type of each array element. Must not be {@code void}.
 *
 * @see Type for the base type interface
 * @see TypeSystem#decayToArrayPointer for array-to-pointer decay
 * @see PointerType for pointer types (result of array decay)
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ArrayType(Type elementType) implements Type {

  /**
   * Constructs an array type.
   *
   * @param elementType the type of each array element (must not be null or void)
   * @throws NullPointerException if elementType is null
   * @throws IllegalArgumentException if elementType is void
   */
  public ArrayType {
    Objects.requireNonNull(elementType, "elementType must not be null");
    if (elementType.isVoid()) {
      throw new IllegalArgumentException("Array element type cannot be void");
    }
  }

  /**
   * Arrays are not scalar types and cannot be used directly in arithmetic operations.
   *
   * <p>Note: Arrays decay to pointers in most contexts, and pointers are scalar types.
   *
   * @return {@code false} - arrays themselves are not scalar
   */
  @Override
  public boolean isScalar() {
    return false;
  }
}


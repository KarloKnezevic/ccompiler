package hr.fer.ppj.semantics.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * <p>Multi-dimensional arrays:
 * <ul>
 *   <li>{@code int arr[3][2];} - 2D array with dimensions [3, 2]</li>
 *   <li>Dimensions are stored outermost-first: {@code arr[i][j]} means dimensions[0]=3, dimensions[1]=2</li>
 *   <li>For unsized arrays (function parameters), dimensions list is empty</li>
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
 * @param dimensions immutable list of array dimensions (outermost first). Empty for unsized arrays.
 *
 * @see Type for the base type interface
 * @see TypeSystem#decayToArrayPointer for array-to-pointer decay
 * @see PointerType for pointer types (result of array decay)
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ArrayType(Type elementType, List<Integer> dimensions) implements Type {

  /**
   * Constructs an array type with dimensions.
   *
   * @param elementType the type of each array element (must not be null or void)
   * @param dimensions list of array dimensions (outermost first). Empty for unsized arrays.
   * @throws NullPointerException if elementType or dimensions is null
   * @throws IllegalArgumentException if elementType is void
   */
  public ArrayType {
    Objects.requireNonNull(elementType, "elementType must not be null");
    Objects.requireNonNull(dimensions, "dimensions must not be null");
    if (elementType.isVoid()) {
      throw new IllegalArgumentException("Array element type cannot be void");
    }
    // Create defensive copy to ensure immutability
    dimensions = Collections.unmodifiableList(new ArrayList<>(dimensions));
  }

  /**
   * Constructs an array type without dimensions (for unsized arrays or backward compatibility).
   *
   * @param elementType the type of each array element (must not be null or void)
   * @throws NullPointerException if elementType is null
   * @throws IllegalArgumentException if elementType is void
   */
  public ArrayType(Type elementType) {
    this(elementType, List.of());
  }

  /**
   * Constructs a single-dimensional array type.
   *
   * @param elementType the type of each array element (must not be null or void)
   * @param length the array length (must be positive)
   * @throws NullPointerException if elementType is null
   * @throws IllegalArgumentException if elementType is void or length is non-positive
   */
  public ArrayType(Type elementType, int length) {
    this(elementType, List.of(length));
  }

  /**
   * Gets the total number of elements in this array.
   *
   * <p>For multi-dimensional arrays, this is the product of all dimensions.
   * For unsized arrays (empty dimensions), returns 0.
   *
   * @return the total number of elements, or 0 if unsized
   */
  public int totalElements() {
    if (dimensions.isEmpty()) {
      return 0;
    }
    return dimensions.stream().reduce(1, (a, b) -> a * b);
  }

  /**
   * Gets the size of a single element at the given dimension level.
   *
   * <p>For {@code int arr[3][2]}, dimension 0 has element size = 2 * sizeof(int) = 8,
   * and dimension 1 has element size = sizeof(int) = 4.
   *
   * @param dimension the dimension level (0 = outermost)
   * @return the size in bytes of one element at this dimension level
   */
  public int getElementSizeAtDimension(int dimension) {
    // This requires type size information, which should be provided by TypeSystem
    // For now, return 0 as a placeholder - actual implementation needs type size calculation
    return 0;
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


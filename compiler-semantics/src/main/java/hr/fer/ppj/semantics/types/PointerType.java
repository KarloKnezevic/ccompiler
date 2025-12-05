package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Represents a pointer type pointing to another type.
 *
 * <p>Pointers in PPJ-C can point to any type, including:
 * <ul>
 *   <li>Primitive types: {@code int*}, {@code char*}, {@code float*}</li>
 *   <li>Struct types: {@code struct Node*}</li>
 *   <li>Array types: Arrays decay to pointers in most contexts</li>
 *   <li>Other pointers: {@code int**}, {@code struct Node***}</li>
 *   <li>Void: {@code void*} (generic pointer type)</li>
 * </ul>
 *
 * <p>Pointer semantics:
 * <ul>
 *   <li><strong>Scalar type</strong>: Pointers are scalar types and can be used in
 *       arithmetic operations, comparisons, and control flow conditions</li>
 *   <li><strong>Pointer arithmetic</strong>: {@code pointer + int} or {@code pointer - int}
 *       yields a pointer of the same type</li>
 *   <li><strong>Pointer subtraction</strong>: {@code pointer - pointer} yields an {@code int}</li>
 *   <li><strong>Dereferencing</strong>: {@code *pointer} yields the base type (l-value)</li>
 *   <li><strong>Address-of</strong>: {@code &lvalue} yields a pointer to the l-value's type</li>
 * </ul>
 *
 * <p>Const qualification:
 * <ul>
 *   <li>{@code isConst = true}: The pointer itself is const (e.g., {@code int * const p})
 *       - the pointer cannot be reassigned, but the pointed-to value can be modified</li>
 *   <li>{@code isConst = false}: The pointer is mutable (e.g., {@code int * p})
 *       - the pointer can be reassigned</li>
 * </ul>
 *
 * <p>Note: Const qualification on the pointed-to type (e.g., {@code const int *}) is
 * represented by wrapping the base type with {@link ConstType}, not by this flag.
 *
 * <p>Assignment compatibility:
 * <ul>
 *   <li>Pointers with the same base type (ignoring const on pointed-to type) are compatible</li>
 *   <li>Arrays decay to pointers in assignment contexts</li>
 *   <li>Integer literal {@code 0} can be assigned to any pointer (NULL pointer)</li>
 * </ul>
 *
 * @param baseType the type being pointed to. Can be any type including {@code void}
 * @param isConst whether the pointer itself is const-qualified (i.e., {@code T * const})
 *
 * @see Type for the base type interface
 * @see TypeSystem for pointer compatibility checking
 * @see ConstType for const-qualified pointed-to types
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record PointerType(Type baseType, boolean isConst) implements Type {

  /**
   * Constructs a pointer type.
   *
   * @param baseType the type being pointed to (must not be null)
   * @param isConst whether the pointer itself is const-qualified
   * @throws NullPointerException if baseType is null
   */
  public PointerType {
    Objects.requireNonNull(baseType, "baseType must not be null");
  }

  /**
   * Pointers are scalar types and can be used in arithmetic operations and control flow.
   *
   * @return {@code true} - pointers are always scalar
   */
  @Override
  public boolean isScalar() {
    return true; // Pointers are scalar types
  }

  /**
   * Pointers are never the void type (though they can point to void).
   *
   * @return {@code false} - pointers themselves are never void
   */
  @Override
  public boolean isVoid() {
    return false;
  }
}


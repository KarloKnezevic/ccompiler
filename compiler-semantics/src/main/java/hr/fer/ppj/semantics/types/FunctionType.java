package hr.fer.ppj.semantics.types;

import java.util.List;
import java.util.Objects;

/**
 * Represents a function type (function signature) consisting of return type and ordered parameter types.
 *
 * <p>Function types in PPJ-C represent function signatures:
 * <ul>
 *   <li>Return type: Can be any type, including {@code void}</li>
 *   <li>Parameters: Ordered list of parameter types (arrays decay to pointers)</li>
 *   <li>Void parameters: {@code void} parameter list means no parameters</li>
 * </ul>
 *
 * <p>Function semantics:
 * <ul>
 *   <li><strong>Not scalar</strong>: Functions are not scalar types and cannot be used as values</li>
 *   <li><strong>Function calls</strong>: Function calls require matching parameter count and types</li>
 *   <li><strong>Return type</strong>: Return statements must match the function's return type</li>
 *   <li><strong>Void functions</strong>: Functions returning {@code void} cannot return expressions</li>
 * </ul>
 *
 * <p>Parameter type compatibility:
 * <ul>
 *   <li>Arrays decay to pointers in function parameters</li>
 *   <li>Numeric types can be implicitly converted (char → int → float)</li>
 *   <li>Pointers must have compatible base types</li>
 *   <li>Structs must have the same type</li>
 * </ul>
 *
 * <p>Function definition vs declaration:
 * <ul>
 *   <li>Function declarations: Provide the signature without a body</li>
 *   <li>Function definitions: Provide the signature with a body</li>
 *   <li>Multiple declarations are allowed if they match</li>
 *   <li>Only one definition is allowed per function</li>
 * </ul>
 *
 * @param returnType the return type of the function. Can be any type including {@code void}.
 * @param parameterTypes immutable ordered list of parameter types. Arrays decay to pointers.
 *                       Empty list or list containing only {@code void} means no parameters.
 *
 * @see Type for the base type interface
 * @see TypeSystem for function type compatibility checking
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record FunctionType(Type returnType, List<Type> parameterTypes) implements Type {

  /**
   * Constructs a function type.
   *
   * <p>Creates a defensive copy of the parameter types list to ensure immutability.
   *
   * @param returnType the return type (must not be null)
   * @param parameterTypes ordered list of parameter types (must not be null)
   * @throws NullPointerException if returnType or parameterTypes is null
   */
  public FunctionType {
    Objects.requireNonNull(returnType, "returnType must not be null");
    Objects.requireNonNull(parameterTypes, "parameterTypes must not be null");
    parameterTypes = List.copyOf(parameterTypes);
  }

  /**
   * Functions are not scalar types and cannot be used as values in expressions.
   *
   * @return {@code false} - functions are never scalar
   */
  @Override
  public boolean isScalar() {
    return false;
  }

  /**
   * Checks if this function returns void.
   *
   * <p>Void functions have special semantics:
   * <ul>
   *   <li>Cannot return expressions: {@code return expr;} is invalid</li>
   *   <li>Can use bare return: {@code return;} is valid</li>
   *   <li>Function call result cannot be used in expressions</li>
   * </ul>
   *
   * @return {@code true} if the return type is {@code void}, {@code false} otherwise
   */
  public boolean isVoidReturn() {
    return returnType.isVoid();
  }
}


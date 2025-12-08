package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Represents a const-qualified type, making values of this type immutable.
 *
 * <p>Const qualification in PPJ-C:
 * <ul>
 *   <li><strong>Const variables</strong>: Cannot be modified after initialization</li>
 *   <li><strong>Const pointers</strong>: The pointed-to value cannot be modified
 *       (e.g., {@code const int *p})</li>
 *   <li><strong>Const pointer itself</strong>: The pointer cannot be reassigned
 *       (e.g., {@code int * const p}) - represented by {@link PointerType#isConst()}</li>
 * </ul>
 *
 * <p>Assignment compatibility:
 * <ul>
 *   <li>Non-const values can be assigned to const variables</li>
 *   <li>Const values cannot be assigned to non-const variables</li>
 *   <li>Const qualification is ignored for type compatibility in most contexts
 *       (use {@link TypeSystem#equalsIgnoringConst})</li>
 * </ul>
 *
 * <p>Const propagation:
 * <ul>
 *   <li>Const qualification wraps the base type</li>
 *   <li>All type queries delegate to the base type</li>
 *   <li>Use {@link TypeSystem#stripConst} to remove const qualification</li>
 * </ul>
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code const int x;} - const integer variable</li>
 *   <li>{@code const int *p;} - pointer to const int</li>
 *   <li>{@code int * const p;} - const pointer to int (not represented by ConstType)</li>
 *   <li>{@code const struct Node *n;} - pointer to const struct</li>
 * </ul>
 *
 * @param baseType the underlying type being qualified as const. Must not be {@code void}.
 *
 * @see Type for the base type interface
 * @see TypeSystem for const qualification operations
 * @see PointerType for const pointers (pointer itself is const)
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ConstType(Type baseType) implements Type {

  /**
   * Constructs a const-qualified type.
   *
   * @param baseType the underlying type to qualify as const (must not be null or void)
   * @throws NullPointerException if baseType is null
   * @throws IllegalArgumentException if baseType is void (void cannot be const-qualified)
   */
  public ConstType {
    Objects.requireNonNull(baseType, "baseType must not be null");
    if (baseType.isVoid()) {
      throw new IllegalArgumentException("Cannot apply const to void");
    }
  }

  /**
   * Const qualification does not affect scalar status - delegates to base type.
   *
   * @return whether the base type is scalar
   */
  @Override
  public boolean isScalar() {
    return baseType.isScalar();
  }

  /**
   * Const qualification does not affect void status - delegates to base type.
   *
   * @return whether the base type is void (should always be false due to constructor check)
   */
  @Override
  public boolean isVoid() {
    return baseType.isVoid();
  }
}


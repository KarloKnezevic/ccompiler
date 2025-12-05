package hr.fer.ppj.semantics.types;

import java.util.Objects;

/**
 * Type compatibility and conversion checks for PPJ-C.
 *
 * <p>This class provides comprehensive type compatibility checking for the semantic analyzer.
 * It implements C's type compatibility rules for:
 * <ul>
 *   <li><strong>Assignment compatibility</strong>: Determines if a value of one type can be
 *       assigned to a variable of another type (with implicit conversions)</li>
 *   <li><strong>Cast validity</strong>: Validates explicit type casts</li>
 *   <li><strong>Type equality</strong>: Compares types for structural equality, ignoring
 *       const qualifiers and handling forward declarations</li>
 * </ul>
 *
 * <p>All methods are static utility methods. This class cannot be instantiated.
 *
 * <p>Key compatibility rules:
 * <ul>
 *   <li><strong>Numeric types</strong>: char → int → float (implicit promotion)</li>
 *   <li><strong>Pointers</strong>: Same base type (ignoring const), or int 0 → any pointer</li>
 *   <li><strong>Arrays</strong>: Decay to pointers in assignment contexts</li>
 *   <li><strong>Structs</strong>: Exact type match required (by tag for tagged structs)</li>
 *   <li><strong>Const</strong>: Non-const can be assigned to const, but not vice versa</li>
 * </ul>
 *
 * <p>This class is used throughout semantic analysis to validate:
 * <ul>
 *   <li>Variable initializations and assignments</li>
 *   <li>Function call arguments</li>
 *   <li>Function return values</li>
 *   <li>Explicit type casts</li>
 *   <li>Pointer operations</li>
 * </ul>
 *
 * @see TypeSystem for the public API (delegates to this class)
 * @see Type for the base type interface
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeCompatibility {
  
  private TypeCompatibility() {}
  
  /**
   * Checks whether a value of {@code source} type can be implicitly assigned to a variable
   * of {@code target} type.
   *
   * <p>This method implements C's assignment compatibility rules with the following precedence:
   * <ol>
   *   <li><strong>Arrays and functions</strong>: Must be exactly equal (no conversions)</li>
   *   <li><strong>Pointers</strong>:
   *       <ul>
   *         <li>Same base type (ignoring const on pointed-to type)</li>
   *         <li>Array types decay to pointers</li>
   *         <li>Integer literal 0 can be assigned to any pointer (NULL)</li>
   *       </ul>
   *   </li>
   *   <li><strong>Structs</strong>: Must be exactly equal (by tag for tagged structs)</li>
   *   <li><strong>Numeric types</strong>:
   *       <ul>
   *         <li>int: accepts int, char, float</li>
   *         <li>char: accepts char, int</li>
   *         <li>float: accepts int, char, float</li>
   *       </ul>
   *   </li>
   *   <li><strong>Void</strong>: Cannot be assigned</li>
   * </ol>
   *
   * <p>Const qualification is handled as follows:
   * <ul>
   *   <li>Const qualification is stripped from both types before comparison</li>
   *   <li>Non-const values can be assigned to const variables</li>
   *   <li>Const values cannot be assigned to non-const variables (checked separately)</li>
   * </ul>
   *
   * @param source the source type (the type of the value being assigned)
   * @param target the target type (the type of the variable being assigned to)
   * @return {@code true} if assignment is legal, {@code false} otherwise
   * @throws NullPointerException if either parameter is null
   */
  public static boolean canAssign(Type source, Type target) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");

    if (target instanceof ArrayType || target instanceof FunctionType) {
      return source.equals(target);
    }

    Type unqualifiedTarget = TypeSystem.stripConst(target);
    Type unqualifiedSource = TypeSystem.stripConst(source);

    if (unqualifiedTarget instanceof PointerType targetPtr) {
      if (unqualifiedSource instanceof PointerType sourcePtr) {
        return equalsIgnoringConst(targetPtr.baseType(), sourcePtr.baseType());
      }
      if (unqualifiedSource instanceof ArrayType arrayType) {
        return equalsIgnoringConst(targetPtr.baseType(), arrayType.elementType());
      }
      if (unqualifiedSource == PrimitiveType.INT) {
        return true; // Allow int 0 -> pointer (NULL)
      }
      return false;
    }

    if (unqualifiedTarget instanceof StructType) {
      return unqualifiedTarget.equals(unqualifiedSource);
    }

    if (unqualifiedTarget == PrimitiveType.INT) {
      return unqualifiedSource == PrimitiveType.INT 
          || unqualifiedSource == PrimitiveType.CHAR
          || unqualifiedSource == PrimitiveType.FLOAT;
    }

    if (unqualifiedTarget == PrimitiveType.CHAR) {
      return unqualifiedSource == PrimitiveType.CHAR || unqualifiedSource == PrimitiveType.INT;
    }

    if (unqualifiedTarget == PrimitiveType.FLOAT) {
      return unqualifiedSource == PrimitiveType.INT 
          || unqualifiedSource == PrimitiveType.CHAR
          || unqualifiedSource == PrimitiveType.FLOAT;
    }

    if (unqualifiedTarget == PrimitiveType.VOID) {
      return false;
    }

    return false;
  }
  
  /**
   * Determines whether an explicit cast from {@code source} to {@code target} is legal.
   *
   * <p>This method validates explicit type casts in C. The following casts are supported:
   * <ul>
   *   <li><strong>Numeric types</strong>: char, int, float can be cast to each other</li>
   *   <li><strong>Pointers</strong>:
   *       <ul>
   *         <li>Can be cast to pointers with compatible base types</li>
   *         <li>Can be cast to integers</li>
   *       </ul>
   *   </li>
   *   <li><strong>Integers</strong>: Can be cast to pointers</li>
   *   <li><strong>Void</strong>: Cast to void is always allowed (discards value)</li>
   *   <li><strong>Structs</strong>: Can only be cast to the same struct type</li>
   * </ul>
   *
   * <p>The following casts are <strong>not</strong> allowed:
   * <ul>
   *   <li>Arrays cannot be cast (they decay to pointers in most contexts)</li>
   *   <li>Functions cannot be cast</li>
   *   <li>Structs cannot be cast to different struct types</li>
   * </ul>
   *
   * <p>Const qualification is ignored for cast validity (casts can add or remove const).
   *
   * @param source the source type (the type being cast from)
   * @param target the target type (the type being cast to)
   * @return {@code true} if the cast is legal, {@code false} otherwise
   * @throws NullPointerException if either parameter is null
   */
  public static boolean canCast(Type source, Type target) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");
    
    if (target instanceof ArrayType || target instanceof FunctionType) {
      return false;
    }
    
    Type baseSource = TypeSystem.stripConst(source);
    Type baseTarget = TypeSystem.stripConst(target);
    
    if (baseTarget.isVoid()) {
      return true;
    }
    
    if (baseSource.isScalar() && baseTarget.isScalar()) {
      boolean sourceNumeric = baseSource == PrimitiveType.INT 
          || baseSource == PrimitiveType.CHAR 
          || baseSource == PrimitiveType.FLOAT;
      boolean targetNumeric = baseTarget == PrimitiveType.INT 
          || baseTarget == PrimitiveType.CHAR 
          || baseTarget == PrimitiveType.FLOAT;
      
      if (sourceNumeric && targetNumeric) {
        return true;
      }
      
      if (baseSource instanceof PointerType && targetNumeric) {
        return true;
      }
      
      if (sourceNumeric && baseTarget instanceof PointerType) {
        return true;
      }
      
      if (baseSource instanceof PointerType && baseTarget instanceof PointerType) {
        PointerType sourcePtr = (PointerType) baseSource;
        PointerType targetPtr = (PointerType) baseTarget;
        return equalsIgnoringConst(sourcePtr.baseType(), targetPtr.baseType());
      }
    }
    
    if (baseSource instanceof StructType && baseTarget instanceof StructType) {
      return baseSource.equals(baseTarget);
    }
    
    return false;
  }
  
  /**
   * Checks whether two types are identical after removing const qualifiers.
   *
   * <p>This method compares two types for structural equality, ignoring const qualification.
   * It is used when type compatibility needs to be checked without regard to const qualification
   * (e.g., when comparing pointer base types or function parameter types).
   *
   * <p>Special handling for struct types:
   * <ul>
   *   <li>If both structs are tagged and have the same tag name, they are considered equal.
   *       This enables forward declarations where a struct may be declared with empty fields
   *       and later defined with full fields.</li>
   *   <li>If either struct is anonymous or tags don't match, structs are compared by
   *       structural equality (field names and types must match exactly).</li>
   * </ul>
   *
   * <p>Examples:
   * <ul>
   *   <li>{@code equalsIgnoringConst(const int, int)} → {@code true}</li>
   *   <li>{@code equalsIgnoringConst(int*, const int*)} → {@code true}</li>
   *   <li>{@code equalsIgnoringConst(struct Node{...}, struct Node{...})} → {@code true}
   *       (if tags match)</li>
   * </ul>
   *
   * @param left the first type to compare
   * @param right the second type to compare
   * @return {@code true} if the types are equal ignoring const, {@code false} otherwise
   * @throws NullPointerException if either parameter is null
   */
  public static boolean equalsIgnoringConst(Type left, Type right) {
    Objects.requireNonNull(left, "left must not be null");
    Objects.requireNonNull(right, "right must not be null");
    Type leftStripped = TypeSystem.stripConst(left);
    Type rightStripped = TypeSystem.stripConst(right);
    
    if (leftStripped instanceof StructType leftStruct && rightStripped instanceof StructType rightStruct) {
      String leftTag = leftStruct.tag();
      String rightTag = rightStruct.tag();
      if (leftTag != null && rightTag != null && leftTag.equals(rightTag)) {
        return true;
      }
      return leftStripped.equals(rightStripped);
    }
    
    return leftStripped.equals(rightStripped);
  }
}


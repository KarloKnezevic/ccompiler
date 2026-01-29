package hr.fer.ppj.semantics.types;

import hr.fer.ppj.semantics.tree.SemanticAttributes;
import java.util.Objects;

/**
 * Utility class for determining cast categories for IR generation.
 *
 * <p>This class provides methods to determine which IR cast operation should be used
 * for a given source and target type combination.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CastCategoryUtil {

  private CastCategoryUtil() {}

  /**
   * Determines the cast category for a cast from source to target type.
   *
   * <p>Cast categories:
   * <ul>
   *   <li><strong>TRUNC</strong>: int32 → char, int32 → bool</li>
   *   <li><strong>SEXT</strong>: char → int32 (char is signed, so sign extend)</li>
   *   <li><strong>PTRCAST</strong>: ptr<T> → ptr<U></li>
   *   <li><strong>ITOF</strong>: int32 → float, char → float</li>
   *   <li><strong>FTOI</strong>: float → int32, float → char</li>
   * </ul>
   *
   * <p>Note: char is signed 8-bit in this subset, so char → int32 uses SEXT, not ZEXT.
   *
   * @param source the source type (type being cast from)
   * @param target the target type (type being cast to)
   * @return the cast category, or null if not a cast requiring IR operation
   * @throws NullPointerException if either parameter is null
   */
  public static SemanticAttributes.CastCategory determineCastCategory(
      Type source, Type target) {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(target, "target must not be null");

    Type baseSource = TypeSystem.stripConst(source);
    Type baseTarget = TypeSystem.stripConst(target);

    // Void casts don't need IR operations (value is discarded)
    if (baseTarget.isVoid()) {
      return null;
    }

    // Pointer casts
    if (baseSource instanceof PointerType && baseTarget instanceof PointerType) {
      return SemanticAttributes.CastCategory.PTRCAST;
    }

    // Numeric casts
    if (baseSource instanceof PrimitiveType srcPrim
        && baseTarget instanceof PrimitiveType tgtPrim) {
      return determineNumericCastCategory(srcPrim, tgtPrim);
    }

    // Pointer to integer or integer to pointer (not supported in IR grammar, but handle gracefully)
    if (baseSource instanceof PointerType && baseTarget instanceof PrimitiveType) {
      // This would require special handling - not in IR grammar
      return null;
    }
    if (baseSource instanceof PrimitiveType && baseTarget instanceof PointerType) {
      // This would require special handling - not in IR grammar
      return null;
    }

    return null;
  }

  /**
   * Determines cast category for numeric type casts.
   */
  private static SemanticAttributes.CastCategory determineNumericCastCategory(
      PrimitiveType source, PrimitiveType target) {
    if (source == PrimitiveType.INT && target == PrimitiveType.CHAR) {
      return SemanticAttributes.CastCategory.TRUNC;
    }
    if (source == PrimitiveType.CHAR && target == PrimitiveType.INT) {
      return SemanticAttributes.CastCategory.SEXT; // char is signed
    }
    if (source == PrimitiveType.INT && target == PrimitiveType.FLOAT) {
      return SemanticAttributes.CastCategory.ITOF;
    }
    if (source == PrimitiveType.CHAR && target == PrimitiveType.FLOAT) {
      return SemanticAttributes.CastCategory.ITOF;
    }
    if (source == PrimitiveType.FLOAT && target == PrimitiveType.INT) {
      return SemanticAttributes.CastCategory.FTOI;
    }
    if (source == PrimitiveType.FLOAT && target == PrimitiveType.CHAR) {
      return SemanticAttributes.CastCategory.FTOI;
    }
    // Same type casts don't need IR operations
    if (source == target) {
      return null;
    }
    return null;
  }
}


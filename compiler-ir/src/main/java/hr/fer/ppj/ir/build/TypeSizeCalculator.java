package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Calculates type sizes in bytes for layout calculations.
 *
 * <p>Type sizes (for 32-bit target, FRISC):
 * <ul>
 *   <li>{@code int32}: 4 bytes</li>
 *   <li>{@code char}: 1 byte</li>
 *   <li>{@code bool}: 4 bytes</li>
 *   <li>{@code float}: 4 bytes</li>
 *   <li>{@code ptr<T>}: 4 bytes</li>
 *   <li>{@code array<T,n>}: n * sizeof(T) bytes</li>
 *   <li>{@code struct Name}: computed from struct layout</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeSizeCalculator {

  private TypeSizeCalculator() {}

  /**
   * Gets the size of a type in bytes.
   *
   * @param irType the IR type
   * @return the size in bytes
   * @throws UnsupportedOperationException for struct types (use overloaded version)
   */
  public static int getTypeSize(IrType irType) {
    Objects.requireNonNull(irType, "irType must not be null");

    return switch (irType) {
      case IrPrimitiveType prim -> switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
      case IrPointerType ptr -> 4; // 32-bit pointers
      case IrArrayType arr -> {
        if (arr.size() < 0) {
          throw new IllegalArgumentException("Array size must be known for size calculation");
        }
        yield arr.size() * getTypeSize(arr.elementType());
      }
      case IrStructType struct -> throw new UnsupportedOperationException(
          "Struct size must be provided via getTypeSize(IrType, int)");
    };
  }

  /**
   * Gets the size of a type in bytes, with struct size provided.
   *
   * @param irType the IR type
   * @param structSize the struct size in bytes (only used for struct types)
   * @return the size in bytes
   */
  public static int getTypeSize(IrType irType, int structSize) {
    Objects.requireNonNull(irType, "irType must not be null");

    if (irType instanceof IrStructType) {
      if (structSize <= 0) {
        throw new IllegalArgumentException("Struct size must be positive: " + structSize);
      }
      return structSize;
    }
    return getTypeSize(irType);
  }
}

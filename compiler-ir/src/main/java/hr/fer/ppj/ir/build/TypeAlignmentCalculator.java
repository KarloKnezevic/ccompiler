package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Calculates type alignment requirements.
 *
 * <p>Type alignments (for 32-bit target):
 * <ul>
 *   <li>{@code int32}: 4 bytes</li>
 *   <li>{@code char}: 1 byte</li>
 *   <li>{@code bool}: 4 bytes</li>
 *   <li>{@code float}: 4 bytes</li>
 *   <li>{@code ptr<T>}: 4 bytes</li>
 *   <li>{@code array<T,n>}: alignment of element type</li>
 *   <li>{@code struct Name}: computed from struct layout</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeAlignmentCalculator {

  private TypeAlignmentCalculator() {}

  /**
   * Gets the alignment requirement for a type.
   *
   * @param irType the IR type
   * @return the alignment in bytes
   * @throws UnsupportedOperationException for struct types (use overloaded version)
   */
  public static int getTypeAlignment(IrType irType) {
    Objects.requireNonNull(irType, "irType must not be null");

    return switch (irType) {
      case IrPrimitiveType prim -> switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
      case IrPointerType ptr -> 4; // 32-bit pointers
      case IrArrayType arr -> getTypeAlignment(arr.elementType());
      case IrStructType struct -> throw new UnsupportedOperationException(
          "Struct alignment must be provided via getTypeAlignment(IrType, int)");
    };
  }

  /**
   * Gets the alignment requirement for a type, with struct alignment provided.
   *
   * @param irType the IR type
   * @param structAlignment the struct alignment in bytes (only used for struct types)
   * @return the alignment in bytes
   */
  public static int getTypeAlignment(IrType irType, int structAlignment) {
    Objects.requireNonNull(irType, "irType must not be null");

    if (irType instanceof IrStructType) {
      if (structAlignment <= 0) {
        throw new IllegalArgumentException("Struct alignment must be positive: " + structAlignment);
      }
      return structAlignment;
    }
    return getTypeAlignment(irType);
  }
}

package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Determines the appropriate cast operation for converting between IR types.
 *
 * <p>Handles explicit casts such as:
 * <ul>
 *   <li>int32 -> char: truncate (TRUNC)</li>
 *   <li>char -> int32: sign-extend (SEXT)</li>
 *   <li>int32 -> float: int to float (ITOF)</li>
 *   <li>float -> int32: float to int (FTOI)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CastOperationDeterminer {

  private CastOperationDeterminer() {}

  /**
   * Determines the appropriate cast operation for converting from one type to another.
   *
   * @param fromType the source type
   * @param toType the target type
   * @return the cast operation name, or null if no cast is needed or not supported
   */
  public static IrRhs.CastOp.CastName determineCastOperation(IrType fromType, IrType toType) {
    Objects.requireNonNull(fromType, "fromType must not be null");
    Objects.requireNonNull(toType, "toType must not be null");

    // int32 -> char: truncate (TRUNC)
    if (fromType.equals(IrPrimitiveType.INT32) && toType.equals(IrPrimitiveType.CHAR)) {
      return IrRhs.CastOp.CastName.TRUNC;
    }
    // char -> int32: sign-extend (SEXT)
    if (fromType.equals(IrPrimitiveType.CHAR) && toType.equals(IrPrimitiveType.INT32)) {
      return IrRhs.CastOp.CastName.SEXT;
    }
    // int32 -> float: int to float (ITOF)
    if (fromType.equals(IrPrimitiveType.INT32) && toType.equals(IrPrimitiveType.FLOAT)) {
      return IrRhs.CastOp.CastName.ITOF;
    }
    // float -> int32: float to int (FTOI)
    if (fromType.equals(IrPrimitiveType.FLOAT) && toType.equals(IrPrimitiveType.INT32)) {
      return IrRhs.CastOp.CastName.FTOI;
    }

    // No cast operation needed or not supported
    return null;
  }
}

package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Promotes values to target types (e.g., char to int, int to float).
 *
 * <p>This utility handles implicit type promotions required by C's arithmetic
 * conversions. Promotions are performed by inserting cast operations.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypePromoter {

  private TypePromoter() {}

  /**
   * Promotes a value to a target type.
   *
   * <p>Handles promotions such as:
   * <ul>
   *   <li>char -> int: sign-extend (SEXT)</li>
   *   <li>int -> float: int to float (ITOF)</li>
   *   <li>char -> float: char -> int -> float (two-step)</li>
   * </ul>
   *
   * @param value the value to promote
   * @param fromType the source type
   * @param toType the target type
   * @param builder the function builder
   * @return the promoted value (may be the same if no promotion needed)
   */
  public static IrValue promoteValue(
      IrValue value,
      IrType fromType,
      IrType toType,
      IrFunctionBuilder builder) {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(fromType, "fromType must not be null");
    Objects.requireNonNull(toType, "toType must not be null");
    Objects.requireNonNull(builder, "builder must not be null");

    // If types are the same, no promotion needed
    if (fromType.equals(toType)) {
      return value;
    }

    // Determine cast category based on type promotion rules
    IrRhs.CastOp.CastName castName;

    // char -> int: sign-extend (SEXT)
    if (fromType.equals(IrPrimitiveType.CHAR) && toType.equals(IrPrimitiveType.INT32)) {
      castName = IrRhs.CastOp.CastName.SEXT;
    }
    // int -> float: int to float (ITOF)
    else if (fromType.equals(IrPrimitiveType.INT32) && toType.equals(IrPrimitiveType.FLOAT)) {
      castName = IrRhs.CastOp.CastName.ITOF;
    }
    // char -> float: char -> int -> float (two-step)
    else if (fromType.equals(IrPrimitiveType.CHAR) && toType.equals(IrPrimitiveType.FLOAT)) {
      // First promote char to int
      IrValue intValue = promoteValue(value, IrPrimitiveType.CHAR, IrPrimitiveType.INT32, builder);
      // Then promote int to float
      return promoteValue(intValue, IrPrimitiveType.INT32, IrPrimitiveType.FLOAT, builder);
    }
    // float -> int: float to int (FTOI) - but this is not a promotion, it's a conversion
    // We shouldn't do this for promotions, only for explicit casts
    else {
      throw new UnsupportedOperationException(
          "Type promotion from " + fromType + " to " + toType + " not yet supported");
    }

    IrRhs.CastOp castOp = new IrRhs.CastOp(castName, value, toType);
    IrTemp result = builder.tempFactory().newTemp(toType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, castOp));
    return result;
  }
}

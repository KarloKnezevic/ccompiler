package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;

/**
 * Converts C scalar conditions to boolean values for IR.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ConditionConverter {

  private ConditionConverter() {
    // Utility class
  }

  /**
   * Converts a condition value to boolean if needed.
   *
   * <p>In C, conditions are scalar types (int, char, float, pointer), not bool. This method
   * converts scalar values to bool by comparing to zero/null.
   */
  public static IrValue convertToBool(IrValue condition, Type condType, IrFunctionBuilder builder) {
    boolean isAlreadyBool =
        condition instanceof IrTemp temp && temp.type().equals(IrPrimitiveType.BOOL);
    if (condType != null && condType.isScalar() && !isAlreadyBool) {
      IrType condIrType = TypeMapper.toIrType(condType);
      IrConst zero;
      if (condType == PrimitiveType.INT || condType == PrimitiveType.CHAR) {
        zero = new IrConst.IntConst(0, condIrType);
      } else if (condType == PrimitiveType.FLOAT) {
        zero = new IrConst.FloatConst(0.0f);
      } else {
        zero = new IrConst.NullConst(condIrType);
      }
      IrRhs.CmpOp cmpNe = new IrRhs.CmpOp(IrRhs.CmpOp.CmpOpName.NE, condition, zero);
      IrTemp boolTemp = builder.tempFactory().newTemp(IrPrimitiveType.BOOL);
      builder.addInstruction(new IrInstruction.IrAssignInstr(boolTemp, cmpNe));
      return boolTemp;
    }
    return condition;
  }
}

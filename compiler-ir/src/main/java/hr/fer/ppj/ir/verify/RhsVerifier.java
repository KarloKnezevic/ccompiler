package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.types.IrPointerType;
import java.util.Set;

/**
 * Verifies IR RHS expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class RhsVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;

  public RhsVerifier(VerificationContext context, ValueVerifier valueVerifier) {
    this.context = context;
    this.valueVerifier = valueVerifier;
  }

  /**
   * Verifies an RHS expression.
   */
  public void verifyRhs(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrRhs rhs,
      Set<Integer> definedTemps) {
    switch (rhs) {
      case IrRhs.AddrOfSymbol ignored -> {
        // Symbol references don't need verification
      }
      case IrRhs.AddrIndex addr -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, addr.base(), definedTemps);
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, addr.idx(), definedTemps);
        if (!(addr.base().type() instanceof IrPointerType)) {
          context.addError(
              functionName, blockLabel, "Instruction " + instrIndex + ": addr_index base must be pointer type");
        }
      }
      case IrRhs.AddrField field -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, field.base(), definedTemps);
      }
      case IrRhs.Load load -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, load.addr(), definedTemps);
        if (!(load.addr().type() instanceof IrPointerType)) {
          context.addError(
              functionName, blockLabel, "Instruction " + instrIndex + ": load address must be pointer type");
        }
      }
      case IrRhs.BinOp bin -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, bin.left(), definedTemps);
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, bin.right(), definedTemps);
      }
      case IrRhs.CmpOp cmp -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, cmp.left(), definedTemps);
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, cmp.right(), definedTemps);
      }
      case IrRhs.Call call -> {
        for (hr.fer.ppj.ir.model.IrValue arg : call.args()) {
          valueVerifier.verifyValue(functionName, blockLabel, instrIndex, arg, definedTemps);
        }
      }
      case IrRhs.UnaryOp unary -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, unary.operand(), definedTemps);
      }
      case IrRhs.IncDecOp incdec -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, incdec.addr(), definedTemps);
        if (!(incdec.addr().type() instanceof IrPointerType)) {
          context.addError(
              functionName, blockLabel, "Instruction " + instrIndex + ": inc/dec address must be pointer type");
        }
      }
      case IrRhs.CastOp cast -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, cast.operand(), definedTemps);
      }
      case IrRhs.ConstRhs ignored -> {
        // Constants don't need verification
      }
    }
  }
}

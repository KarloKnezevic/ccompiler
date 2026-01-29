package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Set;

/**
 * Verifies IR instructions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class InstructionVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;
  private final RhsVerifier rhsVerifier;

  public InstructionVerifier(VerificationContext context) {
    this.context = context;
    this.valueVerifier = new ValueVerifier(context);
    this.rhsVerifier = new RhsVerifier(context, valueVerifier);
  }

  /**
   * Verifies an instruction.
   */
  public void verifyInstruction(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrInstruction instr,
      Set<Integer> definedTemps) {
    switch (instr) {
      case IrInstruction.IrAssignInstr assign -> {
        definedTemps.add(assign.dest().index());
        rhsVerifier.verifyRhs(functionName, blockLabel, instrIndex, assign.rhs(), definedTemps);
        IrType rhsType = assign.rhs().resultType();
        IrType destType = assign.dest().type();
        if (!typesCompatible(rhsType, destType)) {
          context.addError(
              functionName,
              blockLabel,
              "Instruction "
                  + instrIndex
                  + ": Type mismatch: dest="
                  + destType.toIrString()
                  + ", rhs="
                  + rhsType.toIrString());
        }
      }
      case IrInstruction.IrStoreInstr store -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, store.addr(), definedTemps);
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, store.value(), definedTemps);
        if (!(store.addr().type() instanceof IrPointerType)) {
          context.addError(
              functionName,
              blockLabel,
              "Instruction "
                  + instrIndex
                  + ": store address must be pointer type, got "
                  + store.addr().type().toIrString());
        } else {
          IrPointerType ptrType = (IrPointerType) store.addr().type();
          if (!typesCompatible(store.value().type(), store.storeType())) {
            context.addError(
                functionName,
                blockLabel,
                "Instruction "
                    + instrIndex
                    + ": store value type mismatch: value="
                    + store.value().type().toIrString()
                    + ", storeType="
                    + store.storeType().toIrString());
          }
          if (!typesCompatible(store.storeType(), ptrType.baseType())) {
            context.addError(
                functionName,
                blockLabel,
                "Instruction "
                    + instrIndex
                    + ": store type mismatch with pointer base: storeType="
                    + store.storeType().toIrString()
                    + ", ptrBase="
                    + ptrType.baseType().toIrString());
          }
        }
      }
      case IrInstruction.IrVoidCallInstr call -> {
        for (int i = 0; i < call.args().size(); i++) {
          valueVerifier.verifyValue(functionName, blockLabel, instrIndex, call.args().get(i), definedTemps);
        }
      }
    }
  }

  private boolean typesCompatible(IrType type1, IrType type2) {
    return type1.equals(type2);
  }
}

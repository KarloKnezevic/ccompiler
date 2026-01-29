package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;

/**
 * Prints IR instructions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class InstructionPrinter {

  private final StringBuilder out;
  private final RhsPrinter rhsPrinter;
  private final ValuePrinter valuePrinter;

  public InstructionPrinter(StringBuilder out, RhsPrinter rhsPrinter, ValuePrinter valuePrinter) {
    this.out = out;
    this.rhsPrinter = rhsPrinter;
    this.valuePrinter = valuePrinter;
  }

  /**
   * Prints an instruction.
   */
  public void printInstruction(IrInstruction instr) {
    switch (instr) {
      case IrInstruction.IrAssignInstr assign -> {
        out.append(assign.dest().toIrString()).append(" = ");
        rhsPrinter.printRhs(assign.rhs());
      }
      case IrInstruction.IrStoreInstr store -> {
        out.append("store ");
        valuePrinter.printValue(store.addr());
        out.append(", ");
        valuePrinter.printValue(store.value());
        out.append(" : ").append(store.storeType().toIrString());
      }
      case IrInstruction.IrVoidCallInstr call -> {
        out.append("call func:").append(call.funcName()).append("(");
        valuePrinter.printValueList(call.args());
        out.append(") : void");
      }
    }
  }
}

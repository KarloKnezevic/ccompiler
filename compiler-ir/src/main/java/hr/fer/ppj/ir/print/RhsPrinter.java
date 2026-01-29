package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrValue;

/**
 * Prints IR RHS expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class RhsPrinter {

  private final StringBuilder out;
  private final ValuePrinter valuePrinter;

  public RhsPrinter(StringBuilder out, ValuePrinter valuePrinter) {
    this.out = out;
    this.valuePrinter = valuePrinter;
  }

  /**
   * Prints an RHS expression.
   */
  public void printRhs(IrRhs rhs) {
    switch (rhs) {
      case IrRhs.AddrOfSymbol addr -> {
        out.append("addr_of_symbol ").append(addr.symbolRef().toIrString());
      }
      case IrRhs.AddrIndex addr -> {
        out.append("addr_index ");
        valuePrinter.printValue(addr.base());
        out.append(", ");
        valuePrinter.printValue(addr.idx());
        out.append(", ").append(addr.elemSize());
      }
      case IrRhs.AddrField addr -> {
        out.append("addr_field ");
        valuePrinter.printValue(addr.base());
        out.append(", ").append(addr.structName()).append(".").append(addr.fieldName());
      }
      case IrRhs.Load load -> {
        out.append("load ");
        valuePrinter.printValue(load.addr());
        out.append(" : ").append(load.loadType().toIrString());
      }
      case IrRhs.BinOp bin -> {
        out.append(bin.op().toIrString()).append(" ");
        valuePrinter.printValue(bin.left());
        out.append(", ");
        valuePrinter.printValue(bin.right());
        out.append(" : ").append(bin.resultType().toIrString());
      }
      case IrRhs.CmpOp cmp -> {
        out.append(cmp.op().toIrString()).append(" ");
        valuePrinter.printValue(cmp.left());
        out.append(", ");
        valuePrinter.printValue(cmp.right());
        out.append(" : bool");
      }
      case IrRhs.Call call -> {
        out.append("call func:").append(call.funcName()).append("(");
        valuePrinter.printValueList(call.args());
        out.append(") : ").append(call.resultType().toIrString());
      }
      case IrRhs.UnaryOp unary -> {
        out.append(unary.op().toIrString()).append(" ");
        valuePrinter.printValue(unary.operand());
        out.append(" : ").append(unary.resultType().toIrString());
      }
      case IrRhs.IncDecOp incdec -> {
        out.append(incdec.op().toIrString()).append(" ");
        valuePrinter.printValue(incdec.addr());
        out.append(" : ").append(incdec.resultType().toIrString());
      }
      case IrRhs.CastOp cast -> {
        out.append(cast.op().toIrString()).append(" ");
        valuePrinter.printValue(cast.operand());
        out.append(" : ").append(cast.resultType().toIrString());
      }
      case IrRhs.ConstRhs constRhs -> {
        out.append(constRhs.constant().toIrString());
      }
    }
  }
}

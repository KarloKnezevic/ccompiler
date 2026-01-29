package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrTerminator;

/**
 * Prints IR terminators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TerminatorPrinter {

  private final StringBuilder out;
  private final ValuePrinter valuePrinter;

  public TerminatorPrinter(StringBuilder out, ValuePrinter valuePrinter) {
    this.out = out;
    this.valuePrinter = valuePrinter;
  }

  /**
   * Prints a terminator.
   */
  public void printTerminator(IrTerminator term) {
    switch (term) {
      case IrTerminator.IrBrTerm br -> {
        out.append("br ");
        valuePrinter.printValue(br.condition());
        out.append(", ").append(br.trueLabel()).append(", ").append(br.falseLabel());
      }
      case IrTerminator.IrJmpTerm jmp -> {
        out.append("jmp ").append(jmp.label());
      }
      case IrTerminator.IrRetTerm ret -> {
        out.append("ret");
        if (ret.value() != null) {
          out.append(" ");
          valuePrinter.printValue(ret.value());
        }
      }
    }
  }
}

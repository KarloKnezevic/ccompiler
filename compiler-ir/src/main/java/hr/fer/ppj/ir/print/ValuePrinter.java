package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import java.util.List;

/**
 * Prints IR values (temps and constants).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ValuePrinter {

  private final StringBuilder out;

  public ValuePrinter(StringBuilder out) {
    this.out = out;
  }

  /**
   * Prints a value.
   */
  public void printValue(IrValue value) {
    switch (value) {
      case IrTemp temp -> out.append(temp.toIrString());
      case IrConst constant -> out.append(constant.toIrString());
    }
  }

  /**
   * Prints a list of values.
   */
  public void printValueList(List<IrValue> values) {
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      printValue(values.get(i));
    }
  }
}

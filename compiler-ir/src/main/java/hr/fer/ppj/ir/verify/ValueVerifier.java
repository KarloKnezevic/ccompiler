package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrValue;
import java.util.Set;

/**
 * Verifies that values (temps) are defined before use.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ValueVerifier {

  private final VerificationContext context;

  public ValueVerifier(VerificationContext context) {
    this.context = context;
  }

  /**
   * Verifies that a value is defined (if it's a temp).
   */
  public void verifyValue(
      String functionName, String blockLabel, int instrIndex, IrValue value, Set<Integer> definedTemps) {
    if (value instanceof hr.fer.ppj.ir.model.IrTemp temp) {
      if (!definedTemps.contains(temp.index())) {
        context.addError(
            functionName,
            blockLabel,
            "Instruction " + instrIndex + ": Use of undefined temp " + temp.toIrString());
      }
    }
    // Constants are always valid
  }
}

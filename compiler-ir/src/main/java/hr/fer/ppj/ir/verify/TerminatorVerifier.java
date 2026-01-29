package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import java.util.Map;
import java.util.Set;

/**
 * Verifies IR terminators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TerminatorVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;

  public TerminatorVerifier(VerificationContext context, ValueVerifier valueVerifier) {
    this.context = context;
    this.valueVerifier = valueVerifier;
  }

  /**
   * Verifies a terminator.
   */
  public void verifyTerminator(
      String functionName,
      String blockLabel,
      IrTerminator term,
      Map<String, hr.fer.ppj.ir.model.IrBlock> allBlocks,
      Set<Integer> definedTemps) {
    switch (term) {
      case IrTerminator.IrBrTerm br -> {
        valueVerifier.verifyValue(functionName, blockLabel, -1, br.condition(), definedTemps);
        if (br.condition().type() != IrPrimitiveType.BOOL) {
          context.addError(functionName, blockLabel, "Terminator: br condition must be bool type");
        }
        if (!allBlocks.containsKey(br.trueLabel())) {
          context.addError(
              functionName, blockLabel, "Terminator: br references undefined label " + br.trueLabel());
        }
        if (!allBlocks.containsKey(br.falseLabel())) {
          context.addError(
              functionName, blockLabel, "Terminator: br references undefined label " + br.falseLabel());
        }
      }
      case IrTerminator.IrJmpTerm jmp -> {
        if (!allBlocks.containsKey(jmp.label())) {
          context.addError(
              functionName, blockLabel, "Terminator: jmp references undefined label " + jmp.label());
        }
      }
      case IrTerminator.IrRetTerm ret -> {
        if (ret.value() != null) {
          valueVerifier.verifyValue(functionName, blockLabel, -1, ret.value(), definedTemps);
        }
      }
    }
  }
}

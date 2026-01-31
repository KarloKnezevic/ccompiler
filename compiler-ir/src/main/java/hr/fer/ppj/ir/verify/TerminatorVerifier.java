package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies IR terminators according to the grammar definition.
 *
 * <p>Validates terminators as defined in {@code config/ir_definition.txt}:
 *
 * <pre>
 * Terminator
 *   ::= BrTerm | JmpTerm | RetTerm ;
 *
 * BrTerm
 *   ::= "br" Value "," Label "," Label ;
 *
 * JmpTerm
 *   ::= "jmp" Label ;
 *
 * RetTerm
 *   ::= "ret" [ Value ] ;
 * </pre>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>{@code br} condition must be of type {@code bool}</li>
 *   <li>{@code br} labels must reference existing blocks in the function</li>
 *   <li>{@code jmp} label must reference an existing block</li>
 *   <li>{@code ret} value (if present) must be defined</li>
 * </ul>
 *
 * <h3>Block Terminator Invariant</h3>
 * <p>Every basic block must end with exactly one terminator instruction.
 * Terminators are control-flow instructions that transfer execution to
 * another block or return from the function.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see hr.fer.ppj.ir.model.IrTerminator
 */
public final class TerminatorVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;

  /**
   * Creates a new terminator verifier.
   *
   * @param context the verification context for error reporting
   * @param valueVerifier the value verifier for checking operands
   * @throws NullPointerException if any argument is null
   */
  public TerminatorVerifier(VerificationContext context, ValueVerifier valueVerifier) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.valueVerifier = Objects.requireNonNull(valueVerifier, "valueVerifier must not be null");
  }

  /**
   * Verifies a block terminator.
   *
   * @param functionName the function name for error reporting
   * @param blockLabel the block label for error reporting
   * @param term the terminator to verify
   * @param allBlocks map of all blocks in the function (for label validation)
   * @param definedTemps the set of temp indices defined in this block
   */
  public void verifyTerminator(
      String functionName,
      String blockLabel,
      IrTerminator term,
      Map<String, IrBlock> allBlocks,
      Set<Integer> definedTemps) {

    switch (term) {
      case IrTerminator.IrBrTerm br -> {
        valueVerifier.verifyValue(functionName, blockLabel, -1, br.condition(), definedTemps);

        if (br.condition().type() != IrPrimitiveType.BOOL) {
          context.addError(functionName, blockLabel,
              "br condition must be bool type, got " + br.condition().type().toIrString());
        }

        if (!allBlocks.containsKey(br.trueLabel())) {
          context.addError(functionName, blockLabel,
              "br references undefined true label: " + br.trueLabel());
        }

        if (!allBlocks.containsKey(br.falseLabel())) {
          context.addError(functionName, blockLabel,
              "br references undefined false label: " + br.falseLabel());
        }
      }

      case IrTerminator.IrJmpTerm jmp -> {
        if (!allBlocks.containsKey(jmp.label())) {
          context.addError(functionName, blockLabel,
              "jmp references undefined label: " + jmp.label());
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

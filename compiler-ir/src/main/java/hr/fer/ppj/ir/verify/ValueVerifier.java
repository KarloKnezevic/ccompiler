package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies IR values according to the grammar definition.
 *
 * <p>Validates values as defined in {@code config/ir_definition.txt}:
 *
 * <pre>
 * Value
 *   ::= Temp | Const ;
 *
 * Temp
 *   ::= "t" Int ;
 * </pre>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>Temporaries must be defined before use within the same block</li>
 *   <li>Constants are always valid (validated at construction time)</li>
 * </ul>
 *
 * <h3>Def-Before-Use Invariant</h3>
 * <p>The IR uses per-block temporary scoping without phi nodes. Each
 * temporary must be defined (assigned) before it can be used within
 * the same block. Cross-block temporary usage is prohibited.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see hr.fer.ppj.ir.model.IrValue
 * @see hr.fer.ppj.ir.model.IrTemp
 */
public final class ValueVerifier {

  private final VerificationContext context;

  /**
   * Creates a new value verifier.
   *
   * @param context the verification context for error reporting
   * @throws NullPointerException if context is null
   */
  public ValueVerifier(VerificationContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Verifies that a value is valid and defined (if it's a temporary).
   *
   * <p>For temporaries, checks that the temp index exists in the set of
   * defined temps. For constants, no verification is needed as they are
   * validated at construction time.
   *
   * @param functionName the function name for error reporting
   * @param blockLabel the block label for error reporting
   * @param instrIndex the instruction index for error reporting
   * @param value the value to verify
   * @param definedTemps the set of temp indices defined so far in this block
   */
  public void verifyValue(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrValue value,
      Set<Integer> definedTemps) {

    if (value == null) {
      context.addInstructionError(functionName, blockLabel, instrIndex,
          "Value is null");
      return;
    }

    if (value instanceof IrTemp temp) {
      if (!definedTemps.contains(temp.index())) {
        context.addInstructionError(functionName, blockLabel, instrIndex,
            "Use of undefined temporary " + temp.toIrString());
      }
    }
    // Constants are always valid - validated at construction
  }
}

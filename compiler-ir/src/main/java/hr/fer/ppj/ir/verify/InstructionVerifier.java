package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies IR instructions according to the grammar definition.
 *
 * <p>Validates instructions as defined in {@code config/ir_definition.txt}:
 *
 * <pre>
 * Instr
 *   ::= AssignInstr | StoreInstr | VoidCallInstr ;
 *
 * AssignInstr
 *   ::= Temp "=" Rhs ;
 *
 * StoreInstr
 *   ::= "store" Value "," Value ":" Type ;
 *
 * VoidCallInstr
 *   ::= "call" "func:" Ident "(" [ ArgList ] ")" ":" "void" ;
 * </pre>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>Assignment defines a new temporary (adds to defined set)</li>
 *   <li>Assignment RHS type must match destination temp type</li>
 *   <li>Store address must be a pointer type</li>
 *   <li>Store value type must match the store type declaration</li>
 *   <li>Store type must match pointer base type</li>
 *   <li>Call arguments must all be defined</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see hr.fer.ppj.ir.model.IrInstruction
 */
public final class InstructionVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;
  private final RhsVerifier rhsVerifier;

  /**
   * Creates a new instruction verifier.
   *
   * @param context the verification context for error reporting
   * @throws NullPointerException if context is null
   */
  public InstructionVerifier(VerificationContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.valueVerifier = new ValueVerifier(context);
    this.rhsVerifier = new RhsVerifier(context, valueVerifier);
  }

  /**
   * Verifies an instruction.
   *
   * <p>Updates the definedTemps set for assignment instructions (def-before-use).
   *
   * @param functionName the function name for error reporting
   * @param blockLabel the block label for error reporting
   * @param instrIndex the instruction index within the block
   * @param instr the instruction to verify
   * @param definedTemps the set of temp indices defined so far (mutable)
   */
  public void verifyInstruction(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrInstruction instr,
      Set<Integer> definedTemps) {

    switch (instr) {
      case IrInstruction.IrAssignInstr assign -> {
        // Define the temp first (allows self-referential patterns if needed)
        definedTemps.add(assign.dest().index());

        // Verify the RHS
        rhsVerifier.verifyRhs(functionName, blockLabel, instrIndex, assign.rhs(), definedTemps);

        // Check type compatibility
        IrType rhsType = assign.rhs().resultType();
        IrType destType = assign.dest().type();
        if (!typesCompatible(rhsType, destType)) {
          context.addInstructionError(functionName, blockLabel, instrIndex,
              "Type mismatch: dest=" + destType.toIrString() +
              ", rhs=" + rhsType.toIrString());
        }
      }

      case IrInstruction.IrStoreInstr store -> {
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, store.addr(), definedTemps);
        valueVerifier.verifyValue(functionName, blockLabel, instrIndex, store.value(), definedTemps);

        // Store address must be pointer
        if (!(store.addr().type() instanceof IrPointerType ptrType)) {
          context.addInstructionError(functionName, blockLabel, instrIndex,
              "store address must be pointer type, got " + store.addr().type().toIrString());
        } else {
          // Store type must match pointer base type
          // Note: Value type may differ from store type due to implicit conversions
          // (truncation, extension, array decay). The store type is authoritative.
          if (!typesCompatible(store.storeType(), ptrType.baseType())) {
            context.addInstructionError(functionName, blockLabel, instrIndex,
                "store type mismatch with pointer base: storeType=" +
                store.storeType().toIrString() + ", ptrBase=" + ptrType.baseType().toIrString());
          }
        }
      }

      case IrInstruction.IrVoidCallInstr call -> {
        for (int i = 0; i < call.args().size(); i++) {
          IrValue arg = call.args().get(i);
          valueVerifier.verifyValue(functionName, blockLabel, instrIndex, arg, definedTemps);
        }
      }
    }
  }

  /**
   * Checks if two types are compatible for assignment/store.
   *
   * <p>Currently uses strict equality. Could be extended to handle
   * type coercion rules if needed.
   */
  private boolean typesCompatible(IrType type1, IrType type2) {
    if (type1 == null || type2 == null) {
      return false;
    }
    return type1.equals(type2);
  }
}

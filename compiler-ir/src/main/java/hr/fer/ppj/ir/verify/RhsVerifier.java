package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrStructType;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies IR right-hand-side expressions according to the grammar.
 *
 * <p>Validates RHS expressions as defined in {@code config/ir_definition.txt}:
 *
 * <pre>
 * Rhs
 *   ::= AddrOfSymbol | AddrIndex | AddrField | Load
 *    |  BinOp | CmpOp | Call | UnaryOp | IncDecOp | CastOp | Const ;
 * </pre>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>All operand values must be defined before use</li>
 *   <li>{@code addr_index} base must be a pointer type</li>
 *   <li>{@code load} address must be a pointer type</li>
 *   <li>{@code inc/dec} address must be a pointer type</li>
 *   <li>Binary operation operands should have compatible types</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see hr.fer.ppj.ir.model.IrRhs
 */
public final class RhsVerifier {

  private final VerificationContext context;
  private final ValueVerifier valueVerifier;

  /**
   * Creates a new RHS verifier.
   *
   * @param context the verification context for error reporting
   * @param valueVerifier the value verifier for checking operands
   * @throws NullPointerException if any argument is null
   */
  public RhsVerifier(VerificationContext context, ValueVerifier valueVerifier) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.valueVerifier = Objects.requireNonNull(valueVerifier, "valueVerifier must not be null");
  }

  /**
   * Verifies an RHS expression.
   *
   * @param functionName the function name for error reporting
   * @param blockLabel the block label for error reporting
   * @param instrIndex the instruction index for error reporting
   * @param rhs the RHS expression to verify
   * @param definedTemps the set of temp indices defined so far
   */
  public void verifyRhs(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrRhs rhs,
      Set<Integer> definedTemps) {

    switch (rhs) {
      case IrRhs.AddrOfSymbol ignored -> {
        // Symbol references are validated by symbol resolution
      }

      case IrRhs.AddrIndex addr -> {
        verifyValue(functionName, blockLabel, instrIndex, addr.base(), definedTemps);
        verifyValue(functionName, blockLabel, instrIndex, addr.idx(), definedTemps);
        if (!(addr.base().type() instanceof IrPointerType ptrType)) {
          context.addInstructionError(functionName, blockLabel, instrIndex,
              "addr_index base must be pointer type, got " + addr.base().type().toIrString());
        } else {
          validateAddrIndexElementSize(
              functionName, blockLabel, instrIndex, ptrType, addr.elemSize());
        }
      }

      case IrRhs.AddrField field ->
        verifyValue(functionName, blockLabel, instrIndex, field.base(), definedTemps);

      case IrRhs.Load load -> {
        verifyValue(functionName, blockLabel, instrIndex, load.addr(), definedTemps);
        if (!(load.addr().type() instanceof IrPointerType)) {
          context.addInstructionError(functionName, blockLabel, instrIndex,
              "load address must be pointer type, got " + load.addr().type().toIrString());
        }
      }

      case IrRhs.BinOp bin -> {
        verifyValue(functionName, blockLabel, instrIndex, bin.left(), definedTemps);
        verifyValue(functionName, blockLabel, instrIndex, bin.right(), definedTemps);
      }

      case IrRhs.CmpOp cmp -> {
        verifyValue(functionName, blockLabel, instrIndex, cmp.left(), definedTemps);
        verifyValue(functionName, blockLabel, instrIndex, cmp.right(), definedTemps);
      }

      case IrRhs.Call call -> {
        for (IrValue arg : call.args()) {
          verifyValue(functionName, blockLabel, instrIndex, arg, definedTemps);
        }
      }

      case IrRhs.UnaryOp unary ->
        verifyValue(functionName, blockLabel, instrIndex, unary.operand(), definedTemps);

      case IrRhs.IncDecOp incdec -> {
        verifyValue(functionName, blockLabel, instrIndex, incdec.addr(), definedTemps);
        if (!(incdec.addr().type() instanceof IrPointerType)) {
          context.addInstructionError(functionName, blockLabel, instrIndex,
              "inc/dec address must be pointer type, got " + incdec.addr().type().toIrString());
        }
      }

      case IrRhs.CastOp cast ->
        verifyValue(functionName, blockLabel, instrIndex, cast.operand(), definedTemps);

      case IrRhs.ConstRhs ignored -> {
        // Constants are always valid
      }
    }
  }

  private void verifyValue(
      String functionName, String blockLabel, int instrIndex,
      IrValue value, Set<Integer> definedTemps) {
    valueVerifier.verifyValue(functionName, blockLabel, instrIndex, value, definedTemps);
  }

  private void validateAddrIndexElementSize(
      String functionName,
      String blockLabel,
      int instrIndex,
      IrPointerType basePointerType,
      int elemSize) {

    if (basePointerType.baseType() instanceof IrStructType) {
      // Struct element sizes require layout metadata not available in this verifier.
      return;
    }

    try {
      int expectedSize = TypeSizeCalculator.getTypeSize(basePointerType.baseType());
      int decayedArrayElementSize = -1;
      if (basePointerType.baseType() instanceof IrArrayType arrayType) {
        decayedArrayElementSize = TypeSizeCalculator.getTypeSize(arrayType.elementType());
      }

      boolean matchesDirectPointerStride = expectedSize == elemSize;
      boolean matchesDecayedArrayStride = decayedArrayElementSize == elemSize;
      if (!matchesDirectPointerStride && !matchesDecayedArrayStride) {
        String expectedDescription = String.valueOf(expectedSize);
        if (decayedArrayElementSize >= 0 && decayedArrayElementSize != expectedSize) {
          expectedDescription += " or " + decayedArrayElementSize;
        }
        context.addInstructionError(
            functionName,
            blockLabel,
            instrIndex,
            "addr_index elemSize mismatch: expected " + expectedDescription
                + " for base type " + basePointerType.baseType().toIrString()
                + ", got " + elemSize);
      }
    } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
      // Skip size verification when size cannot be determined statically.
    }
  }
}

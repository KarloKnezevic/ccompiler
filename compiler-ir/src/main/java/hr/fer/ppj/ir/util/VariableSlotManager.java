package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.StructLayoutRegistry;
import hr.fer.ppj.ir.build.TypeAlignmentCalculator;
import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import java.util.Objects;

/**
 * Manages variable slots and name uniqueness for local declarations.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableSlotManager {

  private VariableSlotManager() {}

  /**
   * Gets a unique variable name for a local variable, renaming if it shadows an outer scope
   * variable.
   *
   * <p>If a variable with the same name exists in an outer scope (checked via slots), it's renamed
   * by appending _1, _2, etc.
   *
   * @param varName the original variable name
   * @param functionBuilder the function builder
   * @return a unique variable name
   */
  public static String getUniqueVariableName(String varName, IrFunctionBuilder functionBuilder) {
    Objects.requireNonNull(varName, "varName must not be null");
    if (functionBuilder == null) {
      return varName;
    }

    boolean nameExists =
        functionBuilder.getSlots().stream()
            .anyMatch(s -> s.kind() == IrSlot.Kind.LOCAL && s.name().equals(varName));

    if (!nameExists) {
      return varName;
    }

    int suffix = 1;
    while (true) {
      final String candidateName = varName + "_" + suffix;
      boolean candidateExists =
          functionBuilder.getSlots().stream()
              .anyMatch(s -> s.kind() == IrSlot.Kind.LOCAL && s.name().equals(candidateName));
      if (!candidateExists) {
        return candidateName;
      }
      suffix++;
    }
  }

  /**
   * Creates a slot for a local variable and updates the local offset.
   *
   * @param varName the variable name (already unique)
   * @param varType the variable type
   * @param functionBuilder the function builder
   * @param currentOffset the current local offset (will be updated)
   * @param structLayoutRegistry the struct layout registry (for struct types)
   * @return the slot offset
   */
  public static int createLocalSlot(
      String varName,
      IrType varType,
      IrFunctionBuilder functionBuilder,
      int[] currentOffset,
      StructLayoutRegistry structLayoutRegistry) {
    Objects.requireNonNull(varName, "varName must not be null");
    Objects.requireNonNull(varType, "varType must not be null");
    Objects.requireNonNull(functionBuilder, "functionBuilder must not be null");
    Objects.requireNonNull(currentOffset, "currentOffset must not be null");

    int varSize;
    int varAlign;
    if (varType instanceof IrStructType structType && structLayoutRegistry != null) {
      varSize = structLayoutRegistry.getTypeSize(varType);
      varAlign = structLayoutRegistry.getTypeAlignment(varType);
    } else if (structLayoutRegistry != null) {
      varSize = structLayoutRegistry.getTypeSize(varType);
      varAlign = structLayoutRegistry.getTypeAlignment(varType);
    } else {
      varSize = TypeSizeCalculator.getTypeSize(varType);
      varAlign = TypeAlignmentCalculator.getTypeAlignment(varType);
    }

    currentOffset[0] = (currentOffset[0] + varAlign - 1) / varAlign * varAlign;
    int offset = currentOffset[0];
    currentOffset[0] += varSize;

    boolean slotExists =
        functionBuilder.getSlots().stream()
            .anyMatch(s -> s.kind() == IrSlot.Kind.LOCAL && s.name().equals(varName));
    if (!slotExists) {
      IrSlot slot = new IrSlot(IrSlot.Kind.LOCAL, varName, offset, varType);
      functionBuilder.addSlot(slot);
    }

    return offset;
  }

  /**
   * Creates a slot for a local variable and updates the local offset.
   *
   * @param varName the variable name (already unique)
   * @param varType the variable type
   * @param functionBuilder the function builder
   * @param currentOffset the current local offset (will be updated)
   * @return the slot offset
   * @deprecated Use the overload with StructLayoutRegistry for struct support
   */
  @Deprecated
  public static int createLocalSlot(
      String varName,
      IrType varType,
      IrFunctionBuilder functionBuilder,
      int[] currentOffset) {
    return createLocalSlot(varName, varType, functionBuilder, currentOffset, null);
  }

  /**
   * Declares a variable in the function scope.
   *
   * @param varName the variable name
   * @param varType the variable type
   * @param functionScope the function scope
   */
  public static void declareInScope(
      String varName, hr.fer.ppj.semantics.types.Type varType, SymbolTable functionScope) {
    Objects.requireNonNull(varName, "varName must not be null");
    Objects.requireNonNull(varType, "varType must not be null");
    if (functionScope != null) {
      VariableSymbol varSymbol = new VariableSymbol(varName, varType, false);
      functionScope.declare(varSymbol);
    }
  }
}

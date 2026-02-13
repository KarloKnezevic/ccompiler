package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Determines which address-index temporaries require bounds checks.
 */
final class AddrIndexAnalyzer {

  Set<Integer> analyze(
      IrProgramModel.Function function,
      Map<Integer, IrType> tempTypes) {
    Map<Integer, Integer> intConstants = inferIntConstants(function);
    Set<Integer> addressUsed = new HashSet<>();

    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Store store) {
          addTempIfValue(store.address(), addressUsed);
        } else if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.Load load) {
          addTempIfValue(load.address(), addressUsed);
        }
      }
    }

    boolean changed;
    do {
      changed = false;
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (!(instruction instanceof IrProgramModel.Assign assign)) {
            continue;
          }
          int dest = assign.dest().index();
          if (!addressUsed.contains(dest)) {
            continue;
          }
          IrProgramModel.Rhs rhs = assign.rhs();
          if (rhs instanceof IrProgramModel.AddrField addrField) {
            changed |= addTempIfValue(addrField.base(), addressUsed);
          } else if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
            changed |= addTempIfValue(addrIndex.base(), addressUsed);
          }
        }
      }
    } while (changed);

    Set<Integer> checks = new HashSet<>();
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.AddrIndex addrIndex
            && addressUsed.contains(assign.dest().index())) {
          if (isStaticallyInBounds(addrIndex, tempTypes, intConstants)) {
            continue;
          }
          checks.add(assign.dest().index());
        }
      }
    }
    return checks;
  }

  private boolean isStaticallyInBounds(
      IrProgramModel.AddrIndex addrIndex,
      Map<Integer, IrType> tempTypes,
      Map<Integer, Integer> intConstants) {
    Integer index = constantIndex(addrIndex.index(), intConstants);
    if (index == null || index < 0) {
      return false;
    }

    Integer arraySize = arraySize(addrIndex.base(), tempTypes);
    if (arraySize == null || arraySize <= 0) {
      return false;
    }

    return index < arraySize;
  }

  private Integer constantIndex(IrProgramModel.Value value, Map<Integer, Integer> intConstants) {
    if (!(value instanceof IrProgramModel.Const constant)) {
      if (value instanceof IrProgramModel.Temp temp) {
        return intConstants.get(temp.index());
      }
      return null;
    }
    if (constant.constant() instanceof IrConst.IntConst intConst) {
      return intConst.value();
    }
    return null;
  }

  private Map<Integer, Integer> inferIntConstants(IrProgramModel.Function function) {
    Map<Integer, Integer> constants = new java.util.HashMap<>();
    boolean changed;
    do {
      changed = false;
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (!(instruction instanceof IrProgramModel.Assign assign)) {
            continue;
          }
          Integer folded = foldAssign(assign.rhs(), constants);
          if (folded == null) {
            continue;
          }
          Integer previous = constants.put(assign.dest().index(), folded);
          if (!java.util.Objects.equals(previous, folded)) {
            changed = true;
          }
        }
      }
    } while (changed);
    return java.util.Map.copyOf(constants);
  }

  private Integer foldAssign(IrProgramModel.Rhs rhs, Map<Integer, Integer> constants) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs
        && constRhs.constant() instanceof IrConst.IntConst intConst) {
      return intConst.value();
    }

    if (rhs instanceof IrProgramModel.UnaryOp unaryOp
        && unaryOp.op() == IrProgramModel.UnaryOpName.NEG) {
      Integer operand = constantIndex(unaryOp.operand(), constants);
      return operand == null ? null : -operand;
    }

    if (rhs instanceof IrProgramModel.BinOp binOp) {
      Integer left = constantIndex(binOp.left(), constants);
      Integer right = constantIndex(binOp.right(), constants);
      if (left == null || right == null) {
        return null;
      }
      return switch (binOp.op()) {
        case ADD -> left + right;
        case SUB -> left - right;
        case MUL -> left * right;
        case DIV -> {
          if (right == 0) {
            yield null;
          }
          if (left == Integer.MIN_VALUE && right == -1) {
            yield Integer.MIN_VALUE;
          }
          yield left / right;
        }
        case MOD -> {
          if (right == 0 || right == -1) {
            yield 0;
          }
          yield left % right;
        }
        case SHL -> left << right;
        case SHR -> left >> right;
        case AND -> left & right;
        case OR -> left | right;
        case XOR -> left ^ right;
      };
    }

    return null;
  }

  private Integer arraySize(
      IrProgramModel.Value value,
      Map<Integer, IrType> tempTypes) {
    if (!(value instanceof IrProgramModel.Temp temp)) {
      return null;
    }

    IrType type = tempTypes.get(temp.index());
    if (!(type instanceof IrPointerType pointerType)
        || !(pointerType.baseType() instanceof IrArrayType arrayType)) {
      return null;
    }

    return arrayType.size();
  }

  private boolean addTempIfValue(IrProgramModel.Value value, Set<Integer> set) {
    if (value instanceof IrProgramModel.Temp temp) {
      return set.add(temp.index());
    }
    return false;
  }
}

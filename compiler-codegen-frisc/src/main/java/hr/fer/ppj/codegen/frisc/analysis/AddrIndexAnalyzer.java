package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.HashSet;
import java.util.Set;

/**
 * Determines which address-index temporaries require bounds checks.
 */
final class AddrIndexAnalyzer {

  Set<Integer> analyze(IrProgramModel.Function function) {
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
            && assign.rhs() instanceof IrProgramModel.AddrIndex
            && addressUsed.contains(assign.dest().index())) {
          checks.add(assign.dest().index());
        }
      }
    }
    return checks;
  }

  private boolean addTempIfValue(IrProgramModel.Value value, Set<Integer> set) {
    if (value instanceof IrProgramModel.Temp temp) {
      return set.add(temp.index());
    }
    return false;
  }
}

package hr.fer.ppj.opt.rules.temps;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for temp-use analysis in a basic block.
 */
final class IrUsageAnalyzer {

  private IrUsageAnalyzer() {
  }

  static Map<Integer, Integer> countUses(IrBlock block) {
    Map<Integer, Integer> counts = new HashMap<>();
    for (IrInstruction instruction : block.instructions()) {
      for (Integer temp : usedTemps(instruction)) {
        counts.merge(temp, 1, Integer::sum);
      }
    }
    for (Integer temp : usedTemps(block.terminator())) {
      counts.merge(temp, 1, Integer::sum);
    }
    return counts;
  }

  static Set<Integer> usedTemps(IrTerminator terminator) {
    Set<Integer> used = new HashSet<>();
    switch (terminator) {
      case IrTerminator.IrBrTerm br -> collectValue(br.condition(), used);
      case IrTerminator.IrRetTerm ret -> {
        if (ret.value() != null) {
          collectValue(ret.value(), used);
        }
      }
      case IrTerminator.IrJmpTerm ignored -> {
      }
    }
    return used;
  }

  static Set<Integer> usedTemps(IrInstruction instruction) {
    Set<Integer> used = new HashSet<>();
    switch (instruction) {
      case IrInstruction.IrAssignInstr assign -> collectRhs(assign.rhs(), used);
      case IrInstruction.IrStoreInstr store -> {
        collectValue(store.addr(), used);
        collectValue(store.value(), used);
      }
      case IrInstruction.IrVoidCallInstr call -> call.args().forEach(value -> collectValue(value, used));
    }
    return used;
  }

  static boolean isPure(IrRhs rhs) {
    return switch (rhs) {
      case IrRhs.Load ignored -> false;
      case IrRhs.Call ignored -> false;
      case IrRhs.IncDecOp ignored -> false;
      default -> true;
    };
  }

  private static void collectRhs(IrRhs rhs, Set<Integer> sink) {
    switch (rhs) {
      case IrRhs.AddrOfSymbol ignored -> {
      }
      case IrRhs.ConstRhs ignored -> {
      }
      case IrRhs.AddrIndex addrIndex -> {
        collectValue(addrIndex.base(), sink);
        collectValue(addrIndex.idx(), sink);
      }
      case IrRhs.AddrField addrField -> collectValue(addrField.base(), sink);
      case IrRhs.Load load -> collectValue(load.addr(), sink);
      case IrRhs.BinOp binOp -> {
        collectValue(binOp.left(), sink);
        collectValue(binOp.right(), sink);
      }
      case IrRhs.CmpOp cmpOp -> {
        collectValue(cmpOp.left(), sink);
        collectValue(cmpOp.right(), sink);
      }
      case IrRhs.Call call -> call.args().forEach(arg -> collectValue(arg, sink));
      case IrRhs.UnaryOp unaryOp -> collectValue(unaryOp.operand(), sink);
      case IrRhs.IncDecOp incDecOp -> collectValue(incDecOp.addr(), sink);
      case IrRhs.CastOp castOp -> collectValue(castOp.operand(), sink);
    }
  }

  private static void collectValue(IrValue value, Set<Integer> sink) {
    if (value instanceof IrTemp temp) {
      sink.add(temp.index());
    }
  }
}

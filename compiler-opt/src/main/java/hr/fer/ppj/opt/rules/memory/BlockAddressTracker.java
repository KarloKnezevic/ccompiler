package hr.fer.ppj.opt.rules.memory;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which temporary currently represents the address of a tracked slot.
 */
final class BlockAddressTracker {

  private final List<Map<Integer, String>> addressBefore;

  private BlockAddressTracker(List<Map<Integer, String>> addressBefore) {
    this.addressBefore = addressBefore;
  }

  static BlockAddressTracker build(IrBlock block, Set<String> trackedSlots) {
    List<Map<Integer, String>> before = new ArrayList<>(block.instructions().size());
    Map<Integer, String> current = new HashMap<>();

    for (IrInstruction instruction : block.instructions()) {
      before.add(Map.copyOf(current));

      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        int dest = assign.dest().index();
        current.remove(dest);

        if (assign.rhs() instanceof IrRhs.AddrOfSymbol addrOfSymbol
            && SlotAddressResolver.isTrackableSymbol(addrOfSymbol.symbolRef(), trackedSlots)) {
          current.put(dest, addrOfSymbol.symbolRef().name());
        } else if (assign.rhs() instanceof IrRhs.CastOp cast
            && cast.op() == IrRhs.CastName.PTRCAST
            && cast.operand() instanceof IrTemp sourceTemp) {
          String sourceSlot = current.get(sourceTemp.index());
          if (sourceSlot != null) {
            current.put(dest, sourceSlot);
          }
        }
      }
    }

    return new BlockAddressTracker(before);
  }

  String resolveSlot(IrValue address, int instructionIndex) {
    if (instructionIndex < 0 || instructionIndex >= addressBefore.size()) {
      return null;
    }
    if (address instanceof IrTemp temp) {
      return addressBefore.get(instructionIndex).get(temp.index());
    }
    return null;
  }
}

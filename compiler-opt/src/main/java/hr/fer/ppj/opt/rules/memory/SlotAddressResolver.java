package hr.fer.ppj.opt.rules.memory;

import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared helpers for resolving local/parameter slot access via addr_of_symbol temps.
 */
final class SlotAddressResolver {

  private SlotAddressResolver() {
  }

  static Set<String> trackedSlots(IrFunction function) {
    Set<String> slots = new LinkedHashSet<>();
    for (IrSlot slot : function.slots()) {
      if (slot.kind() == IrSlot.Kind.LOCAL || slot.kind() == IrSlot.Kind.PARAM) {
        slots.add(slot.name());
      }
    }
    return slots;
  }

  static boolean isTrackableSymbol(IrSymbolRef symbolRef, Set<String> trackedSlots) {
    return (symbolRef.kind() == IrSymbolRef.Kind.LOCAL || symbolRef.kind() == IrSymbolRef.Kind.PARAM)
        && trackedSlots.contains(symbolRef.name());
  }

  static String resolveSlot(IrValue address, Map<Integer, String> addressTemps) {
    if (address instanceof IrTemp temp) {
      return addressTemps.get(temp.index());
    }
    return null;
  }
}

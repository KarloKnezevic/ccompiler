package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import hr.fer.ppj.ir.types.IrPointerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Identifies pointer locals that can be backed by global scratch storage.
 */
public final class PointerScratchCollector {

  /**
   * Collects scratch storage requirements for pointer locals that never get reassigned.
   */
  public PointerScratch collect(IrProgramModel program, StructLayoutRegistry structLayouts) {
    Map<String, Map<String, String>> labelsByFunction = new LinkedHashMap<>();
    List<Scratch> scratches = new ArrayList<>();

    for (IrProgramModel.Function function : program.functions()) {
      Map<String, IrProgramModel.Slot> localSlots = new HashMap<>();
      for (IrProgramModel.Slot slot : function.slots()) {
        if (slot.kind() == IrProgramModel.SlotKind.LOCAL) {
          localSlots.put(slot.name(), slot);
        }
      }

      Map<String, IrPointerType> pointerLocals = new LinkedHashMap<>();
      for (IrProgramModel.Slot slot : localSlots.values()) {
        if (slot.type() instanceof IrPointerType pointerType) {
          pointerLocals.put(slot.name(), pointerType);
        }
      }
      if (pointerLocals.isEmpty()) {
        continue;
      }

      Map<Integer, String> addrTemps = new HashMap<>();
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (instruction instanceof IrProgramModel.Assign assign
              && assign.rhs() instanceof IrProgramModel.AddrOfSymbol addr
              && addr.symbolRef().kind() == IrProgramModel.SymbolRefKind.LOCAL
              && pointerLocals.containsKey(addr.symbolRef().name())) {
            addrTemps.put(assign.dest().index(), addr.symbolRef().name());
          }
        }
      }

      java.util.Set<String> assignedPointerLocals = new java.util.HashSet<>();
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (instruction instanceof IrProgramModel.Store store && store.address() instanceof IrProgramModel.Temp temp) {
            String localName = addrTemps.get(temp.index());
            if (localName != null) {
              assignedPointerLocals.add(localName);
            }
          }
        }
      }

      Map<String, String> scratchLabels = new LinkedHashMap<>();
      for (Map.Entry<String, IrPointerType> entry : pointerLocals.entrySet()) {
        String localName = entry.getKey();
        if (assignedPointerLocals.contains(localName)) {
          continue;
        }
        String label = pointerScratchLabel(function.name(), localName);
        IrPointerType pointerType = entry.getValue();
        int size = LoweringSupport.sizeOf(pointerType.baseType(), structLayouts);
        if (size <= 0) {
          size = 4;
        }
        int alignment = LoweringSupport.alignmentOf(pointerType.baseType());
        scratchLabels.put(localName, label);
        scratches.add(new Scratch(label, size, alignment,
            "scratch for " + function.name() + "." + localName));
      }

      if (!scratchLabels.isEmpty()) {
        labelsByFunction.put(function.name(), scratchLabels);
      }
    }

    return new PointerScratch(labelsByFunction, scratches);
  }

  private static String pointerScratchLabel(String functionName, String localName) {
    return "G_SCRATCH_" + functionName.toUpperCase(Locale.ROOT) + "_" + localName.toUpperCase(Locale.ROOT);
  }
}

package hr.fer.ppj.codegen.frisc.frame;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds parameter layouts for every function using slot offsets.
 */
public final class ParamLayoutBuilder {

  /**
   * Builds a map from function name to parameter layout.
   */
  public Map<String, ParamLayout> build(IrProgramModel program, StructLayoutRegistry structLayouts) {
    Map<String, ParamLayout> layouts = new HashMap<>();
    for (IrProgramModel.Function function : program.functions()) {
      Map<String, IrProgramModel.Slot> paramSlots = new HashMap<>();
      for (IrProgramModel.Slot slot : function.slots()) {
        if (slot.kind() == IrProgramModel.SlotKind.PARAM) {
          paramSlots.put(slot.name(), slot);
        }
      }

      List<ParamInfo> params = new ArrayList<>();
      int totalBytes = 0;
      for (IrProgramModel.Parameter parameter : function.parameters()) {
        IrProgramModel.Slot slot = paramSlots.get(parameter.name());
        if (slot == null) {
          throw new CodeGenerationException("Missing param slot for " + function.name() + "." + parameter.name());
        }
        int offset = slot.offset();
        params.add(new ParamInfo(parameter.type(), offset));
        int size = LoweringSupport.sizeOf(parameter.type(), structLayouts);
        totalBytes = Math.max(totalBytes, offset + size);
      }
      totalBytes = LoweringSupport.alignTo(totalBytes, 4);
      layouts.put(function.name(), new ParamLayout(params, totalBytes));
    }
    return layouts;
  }
}

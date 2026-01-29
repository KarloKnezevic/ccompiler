package hr.fer.ppj.ir.lowering.func;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrSlot;
import java.util.List;
import java.util.Objects;

/**
 * Generates function frame layout (parameter slots and frame size).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FrameLayoutGenerator {

  /**
   * Generates parameter slots for a function.
   */
  public void generateParameterSlots(
      IrFunctionBuilder builder, List<IrFunction.Parameter> parameters) {
    Objects.requireNonNull(builder, "builder must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");

    int offset = 0;
    int maxAlign = 4;

    for (IrFunction.Parameter param : parameters) {
      int paramSize = TypeMapper.getTypeSize(param.type());
      int paramAlign = TypeMapper.getTypeAlignment(param.type());
      maxAlign = Math.max(maxAlign, paramAlign);

      // Align offset
      offset = (offset + paramAlign - 1) / paramAlign * paramAlign;

      IrSlot slot = new IrSlot(IrSlot.Kind.PARAM, param.name(), offset, param.type());
      builder.addSlot(slot);
      offset += paramSize;
    }
  }

  /**
   * Computes and sets the final frame size for a function.
   */
  public void computeFrameSize(IrFunctionBuilder builder) {
    Objects.requireNonNull(builder, "builder must not be null");

    List<IrSlot> allSlots = builder.getSlots();
    int maxOffset = 0;
    int maxAlign = 4;

    for (IrSlot slot : allSlots) {
      if (slot.kind() == IrSlot.Kind.LOCAL) {
        int slotEnd = slot.offset() + TypeMapper.getTypeSize(slot.type());
        maxOffset = Math.max(maxOffset, slotEnd);
        maxAlign = Math.max(maxAlign, TypeMapper.getTypeAlignment(slot.type()));
      }
    }

    // Align the total frame size
    if (maxOffset > 0) {
      maxOffset = (maxOffset + maxAlign - 1) / maxAlign * maxAlign;
    }

    builder.setFrame(maxOffset, maxAlign);
  }
}

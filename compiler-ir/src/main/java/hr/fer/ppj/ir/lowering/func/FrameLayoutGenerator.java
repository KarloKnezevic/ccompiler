package hr.fer.ppj.ir.lowering.func;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.StructLayoutRegistry;
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

  private final StructLayoutRegistry structLayoutRegistry;

  public FrameLayoutGenerator(StructLayoutRegistry structLayoutRegistry) {
    this.structLayoutRegistry = structLayoutRegistry; // Can be null for backward compatibility
  }

  public FrameLayoutGenerator() {
    this(null);
  }

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
      int paramSize = getTypeSize(param.type());
      int paramAlign = getTypeAlignment(param.type());
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
        int slotEnd = slot.offset() + getTypeSize(slot.type());
        maxOffset = Math.max(maxOffset, slotEnd);
        maxAlign = Math.max(maxAlign, getTypeAlignment(slot.type()));
      }
    }

    // Align the total frame size
    if (maxOffset > 0) {
      maxOffset = (maxOffset + maxAlign - 1) / maxAlign * maxAlign;
    }

    builder.setFrame(maxOffset, maxAlign);
  }

  private int getTypeSize(hr.fer.ppj.ir.types.IrType type) {
    if (structLayoutRegistry != null) {
      return structLayoutRegistry.getTypeSize(type);
    }
    return TypeMapper.getTypeSize(type);
  }

  private int getTypeAlignment(hr.fer.ppj.ir.types.IrType type) {
    if (structLayoutRegistry != null) {
      return structLayoutRegistry.getTypeAlignment(type);
    }
    return TypeMapper.getTypeAlignment(type);
  }
}

package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;

/**
 * Emits address computations and memory helpers.
 */
public final class AddressLowerer {

  @FunctionalInterface
  public interface ValueEmitter {
    void emitValue(IrProgramModel.Value value, FunctionContext ctx, String targetReg);
  }

  private final LabelGenerator labelGenerator;
  private final ImmediateEmitter immediateEmitter;
  private ValueEmitter valueEmitter;

  public AddressLowerer(LabelGenerator labelGenerator, ImmediateEmitter immediateEmitter) {
    this.labelGenerator = labelGenerator;
    this.immediateEmitter = immediateEmitter;
  }

  public void bindValueEmitter(ValueEmitter emitter) {
    this.valueEmitter = emitter;
  }

  public void emitAddrIndex(IrProgramModel.AddrIndex addrIndex, FunctionContext ctx, Integer destTemp) {
    ensureValueEmitter();
    valueEmitter.emitValue(addrIndex.base(), ctx, "R0");
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Save base address");
    valueEmitter.emitValue(addrIndex.index(), ctx, "R0");
    ctx.emitter().emitInstruction("MOVE", List.of("R0", "R1"), "Index");
    ctx.emitter().emitInstruction("POP", List.of("R0"), "Restore base");

    IrType baseType = valueType(addrIndex.base(), ctx);
    boolean needsCheck = destTemp != null && ctx.addrIndexNeedsCheck().contains(destTemp);
    if (needsCheck && baseType instanceof IrPointerType pointerType && pointerType.baseType() instanceof IrArrayType arrayType) {
      emitBoundsCheck(arrayType.size(), ctx);
    }

    int elemSize = addrIndex.elemSize();
    if (elemSize == 1) {
      // no-op
    } else if (elemSize == 2) {
      ctx.emitter().emitInstruction("SHL", List.of("R1", "1", "R1"), "Index * 2");
    } else if (elemSize == 4) {
      ctx.emitter().emitInstruction("SHL", List.of("R1", "2", "R1"), "Index * 4");
    } else {
      ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Save base");
      ctx.emitter().emitInstruction("PUSH", List.of("R1"), "Save index");
      ctx.emitter().emitInstruction("MOVE", List.of(LoweringSupport.formatImmediate(elemSize), "R0"), "Elem size");
      ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Arg (size)");
      ctx.emitter().emitInstruction("CALL", List.of("F_MUL"), "Index * size");
      ctx.emitter().emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
      ctx.emitter().emitInstruction("POP", List.of("R0"), "Restore base");
      ctx.emitter().emitInstruction("MOVE", List.of("R6", "R1"), "Result");
      ctx.emitter().markMulNeeded();
    }

    ctx.emitter().emitInstruction("ADD", List.of("R0", "R1", "R0"), "Base + offset");
  }

  public void emitAddrField(IrProgramModel.AddrField addrField, FunctionContext ctx) {
    ensureValueEmitter();
    valueEmitter.emitValue(addrField.base(), ctx, "R0");
    int offset = ctx.structLayouts().getFieldOffset(addrField.structName(), addrField.fieldName());
    if (offset != 0) {
      ctx.emitter().emitInstruction("ADD", List.of("R0", LoweringSupport.formatImmediate(offset), "R0"), "Field offset");
    }
  }

  public void emitBoundsCheck(int size, FunctionContext ctx) {
    if (size <= 0) {
      return;
    }
    ctx.emitter().markBoundsCheckNeeded();
    ctx.emitter().emitInstruction("CMP", List.of("R1", "0"), "Bounds check");
    ctx.emitter().emitInstruction("JP_SLT", List.of("L_BOUNDS_ERROR"), null);
    ctx.emitter().emitInstruction("CMP", List.of("R1", LoweringSupport.formatImmediate(size)), null);
    ctx.emitter().emitInstruction("JP_SGE", List.of("L_BOUNDS_ERROR"), null);
  }

  public void emitAddrOfSymbol(IrProgramModel.SymbolRef symbolRef, FunctionContext ctx, String targetReg) {
    switch (symbolRef.kind()) {
      case GLOBAL -> {
        String label = labelGenerator.globalLabel(symbolRef.name());
        ctx.emitter().emitInstruction("MOVE", List.of(label, targetReg), "Address of global");
      }
      case PARAM -> {
        IrProgramModel.Slot slot = ctx.paramSlots().get(symbolRef.name());
        if (slot == null) {
          throw new CodeGenerationException("Unknown param: " + symbolRef.name());
        }
        int offset = 8 + slot.offset();
        ctx.emitter().emitInstruction("MOVE", List.of("R5", targetReg), null);
        ctx.emitter().emitInstruction("ADD", List.of(targetReg, LoweringSupport.formatImmediate(offset), targetReg),
            "Param address");
      }
      case LOCAL -> {
        IrProgramModel.Slot slot = ctx.localSlots().get(symbolRef.name());
        if (slot == null) {
          throw new CodeGenerationException("Unknown local: " + symbolRef.name());
        }
        int offset = 4 + slot.offset();
        int size = LoweringSupport.sizeOf(slot.type(), ctx.structLayouts());
        int align = LoweringSupport.alignmentOf(slot.type());
        if (size > align) {
          offset += (size - align);
        }
        ctx.emitter().emitInstruction("MOVE", List.of("R5", targetReg), null);
        ctx.emitter().emitInstruction("SUB", List.of(targetReg, LoweringSupport.formatImmediate(offset), targetReg),
            "Local address");
      }
    }
  }

  public void emitMemCopy(String srcReg, String dstReg, int size, FunctionContext ctx, String comment) {
    if (size <= 0) {
      return;
    }
    immediateEmitter.emitLoadImmediate(size, ctx, "R2", comment);
    String loop = labelGenerator.newLabel("L_MEMCPY");
    ctx.emitter().emitLabel(loop, null);
    ctx.emitter().emitInstruction("LOADB", List.of("R3", "(" + srcReg + ")"), null);
    ctx.emitter().emitInstruction("STOREB", List.of("R3", "(" + dstReg + ")"), null);
    ctx.emitter().emitInstruction("ADD", List.of(srcReg, "1", srcReg), null);
    ctx.emitter().emitInstruction("ADD", List.of(dstReg, "1", dstReg), null);
    ctx.emitter().emitInstruction("SUB", List.of("R2", "1", "R2"), null);
    ctx.emitter().emitInstruction("JP_NE", List.of(loop), null);
  }

  public IrType valueType(IrProgramModel.Value value, FunctionContext ctx) {
    if (value instanceof IrProgramModel.Temp temp) {
      return ctx.tempTypes().get(temp.index());
    }
    if (value instanceof IrProgramModel.Const c) {
      return c.constant().type();
    }
    return null;
  }

  private void ensureValueEmitter() {
    if (valueEmitter == null) {
      throw new IllegalStateException("ValueEmitter not bound");
    }
  }
}

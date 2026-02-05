package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.util.List;

/**
 * Utilities for accessing temporaries and argument scratch slots in the frame.
 */
public final class FrameAccess {

  public void loadTemp(int tempIndex, FunctionContext ctx, String targetReg) {
    Integer offset = ctx.tempOffsets().get(tempIndex);
    if (offset == null) {
      throw new CodeGenerationException("Unknown temp: t" + tempIndex);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter().emitInstruction("LOAD", List.of(targetReg, "(" + addr + ")"), "Load temp t" + tempIndex);
    if (LoweringSupport.isChar(ctx.tempTypes().get(tempIndex))) {
      ctx.emitter().emitInstruction("AND", List.of(targetReg, LoweringSupport.formatImmediate(0xFF), targetReg),
          "Clamp char");
    }
  }

  public void storeTemp(int tempIndex, FunctionContext ctx) {
    Integer offset = ctx.tempOffsets().get(tempIndex);
    if (offset == null) {
      throw new CodeGenerationException("Unknown temp: t" + tempIndex);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter().emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Store temp t" + tempIndex);
  }

  public void storeArgScratch(int index, FunctionContext ctx) {
    Integer offset = ctx.argOffsets().get(index);
    if (offset == null) {
      throw new CodeGenerationException("Arg scratch missing for index " + index);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter().emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Save arg");
  }

  public void loadArgScratch(int index, FunctionContext ctx, String targetReg) {
    Integer offset = ctx.argOffsets().get(index);
    if (offset == null) {
      throw new CodeGenerationException("Arg scratch missing for index " + index);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter().emitInstruction("LOAD", List.of(targetReg, "(" + addr + ")"), "Load arg");
  }

  public String formatFrameOffset(int offset) {
    if (offset == 0) {
      return "R5";
    }
    if (offset > 0) {
      return "R5+" + LoweringSupport.formatImmediate(offset);
    }
    return "R5-" + LoweringSupport.formatImmediate(-offset);
  }

  public String formatStackOffset(int offset) {
    if (offset == 0) {
      return "R7";
    }
    if (offset > 0) {
      return "R7+" + LoweringSupport.formatImmediate(offset);
    }
    return "R7-" + LoweringSupport.formatImmediate(-offset);
  }
}

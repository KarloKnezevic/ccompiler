package hr.fer.ppj.codegen.frisc.lowering;

import java.util.List;

/**
 * Emits immediate values into registers with proper encoding.
 */
public final class ImmediateEmitter {

  public void emitLoadImmediate(int value, FunctionContext ctx, String targetReg, String comment) {
    if (LoweringSupport.fitsSigned20(value)) {
      ctx.emitter().emitInstruction("MOVE", List.of(LoweringSupport.formatImmediate(value), targetReg), comment);
      return;
    }
    int high = (value >>> 16) & 0xFFFF;
    int low = value & 0xFFFF;
    ctx.emitter().emitInstruction("MOVE", List.of(LoweringSupport.formatImmediate(high), targetReg), comment);
    ctx.emitter().emitInstruction("SHL", List.of(targetReg, "10", targetReg), "imm << 16");
    if (low != 0) {
      ctx.emitter().emitInstruction("OR", List.of(targetReg, LoweringSupport.formatImmediate(low), targetReg),
          "imm low");
    }
  }
}

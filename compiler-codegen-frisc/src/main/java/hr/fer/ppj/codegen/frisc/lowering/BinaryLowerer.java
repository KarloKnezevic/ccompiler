package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.List;

/**
 * Lowers binary operations into FRISC instructions.
 */
final class BinaryLowerer {
  private final ValueEmitter valueEmitter;

  BinaryLowerer(ValueEmitter valueEmitter) {
    this.valueEmitter = valueEmitter;
  }

  void emitBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    valueEmitter.emit(binOp.left(), ctx, "R0");
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Save left");
    valueEmitter.emit(binOp.right(), ctx, "R0");
    ctx.emitter().emitInstruction("MOVE", List.of("R0", "R1"), "Right");
    ctx.emitter().emitInstruction("POP", List.of("R0"), "Left");

    if (LoweringSupport.isFloat(binOp.resultType())) {
      emitFloatBinOp(binOp, ctx);
      return;
    }

    switch (binOp.op()) {
      case ADD -> ctx.emitter().emitInstruction("ADD", List.of("R0", "R1", "R0"), null);
      case SUB -> ctx.emitter().emitInstruction("SUB", List.of("R0", "R1", "R0"), null);
      case AND -> ctx.emitter().emitInstruction("AND", List.of("R0", "R1", "R0"), null);
      case OR -> ctx.emitter().emitInstruction("OR", List.of("R0", "R1", "R0"), null);
      case XOR -> ctx.emitter().emitInstruction("XOR", List.of("R0", "R1", "R0"), null);
      case SHL -> ctx.emitter().emitInstruction("SHL", List.of("R0", "R1", "R0"), null);
      case SHR -> ctx.emitter().emitInstruction("SHR", List.of("R0", "R1", "R0"), null);
      case MUL -> {
        emitBinaryHelper("F_MUL", ctx);
        ctx.emitter().markMulNeeded();
      }
      case DIV -> {
        emitBinaryHelper("F_DIV", ctx);
        ctx.emitter().markDivNeeded();
      }
      case MOD -> {
        emitBinaryHelper("F_MOD", ctx);
        ctx.emitter().markModNeeded();
      }
    }
  }

  private void emitFloatBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    if (binOp.op() == IrProgramModel.BinOpName.MUL) {
      emitBinaryHelper("F_FMUL", ctx);
      ctx.emitter().markFmulNeeded();
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.DIV) {
      emitBinaryHelper("F_FDIV", ctx);
      ctx.emitter().markFdivNeeded();
      ctx.emitter().markDivNeeded();
      ctx.emitter().markModNeeded();
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.ADD) {
      ctx.emitter().emitInstruction("ADD", List.of("R0", "R1", "R0"), "Float add");
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.SUB) {
      ctx.emitter().emitInstruction("SUB", List.of("R0", "R1", "R0"), "Float sub");
      return;
    }
    throw new CodeGenerationException("Unsupported float binop: " + binOp.op());
  }

  private void emitBinaryHelper(String label, FunctionContext ctx) {
    ctx.emitter().emitInstruction("MOVE", List.of("R1", "R2"), "Save right");
    ctx.emitter().emitInstruction("PUSH", List.of("R2"), "Arg right");
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Arg left");
    ctx.emitter().emitInstruction("CALL", List.of(label), null);
    ctx.emitter().emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
    ctx.emitter().emitInstruction("MOVE", List.of("R6", "R0"), "Result");
  }
}

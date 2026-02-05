package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import java.util.List;

/**
 * Lowers unary operations and casts.
 */
final class UnaryLowerer {
  private final LabelGenerator labelGenerator;
  private final ValueEmitter valueEmitter;

  UnaryLowerer(LabelGenerator labelGenerator, ValueEmitter valueEmitter) {
    this.labelGenerator = labelGenerator;
    this.valueEmitter = valueEmitter;
  }

  void emitUnaryOp(IrProgramModel.UnaryOp unaryOp, FunctionContext ctx) {
    valueEmitter.emit(unaryOp.operand(), ctx, "R0");
    if (unaryOp.op() == IrProgramModel.UnaryOpName.NEG) {
      ctx.emitter().emitInstruction("MOVE", List.of("0", "R1"), null);
      ctx.emitter().emitInstruction("SUB", List.of("R1", "R0", "R0"), "Negate");
      return;
    }

    if (unaryOp.op() == IrProgramModel.UnaryOpName.NOT) {
      String trueLabel = labelGenerator.newLabel("L_NOT_TRUE");
      String endLabel = labelGenerator.newLabel("L_NOT_END");
      ctx.emitter().emitInstruction("CMP", List.of("R0", "0"), null);
      ctx.emitter().emitInstruction("JP_EQ", List.of(trueLabel), null);
      ctx.emitter().emitInstruction("MOVE", List.of("0", "R0"), "False");
      ctx.emitter().emitInstruction("JP", List.of(endLabel), null);
      ctx.emitter().emitLabel(trueLabel, null);
      ctx.emitter().emitInstruction("MOVE", List.of("1", "R0"), "True");
      ctx.emitter().emitLabel(endLabel, null);
      return;
    }

    throw new CodeGenerationException("Unsupported unary op: " + unaryOp.op());
  }

  void emitCastOp(IrProgramModel.CastOp castOp, FunctionContext ctx) {
    valueEmitter.emit(castOp.operand(), ctx, "R0");
    switch (castOp.op()) {
      case TRUNC -> {
        ctx.emitter().emitInstruction("AND", List.of("R0", LoweringSupport.formatImmediate(0xFF), "R0"),
            "Truncate to byte");
      }
      case ZEXT -> {
        ctx.emitter().emitInstruction("AND", List.of("R0", LoweringSupport.formatImmediate(0xFF), "R0"),
            "Zero-extend byte");
      }
      case SEXT -> {
        ctx.emitter().emitInstruction("SHL", List.of("R0", "18", "R0"), "Sign-extend (shift)");
        ctx.emitter().emitInstruction("ASHR", List.of("R0", "18", "R0"), null);
      }
      case PTRCAST -> {
        // no-op
      }
      case ITOF -> {
        emitUnaryHelper("F_I2F", ctx);
        ctx.emitter().markI2fNeeded();
      }
      case FTOI -> {
        emitUnaryHelper("F_F2I", ctx);
        ctx.emitter().markF2iNeeded();
      }
    }
  }

  private void emitUnaryHelper(String label, FunctionContext ctx) {
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Arg");
    ctx.emitter().emitInstruction("CALL", List.of(label), null);
    ctx.emitter().emitInstruction("ADD", List.of("R7", "4", "R7"), "Clean args");
    ctx.emitter().emitInstruction("MOVE", List.of("R6", "R0"), "Result");
  }
}

package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import java.util.List;

/**
 * Lowers comparisons into boolean materialization sequences.
 */
final class CompareLowerer {
  private final LabelGenerator labelGenerator;
  private final ValueEmitter valueEmitter;

  CompareLowerer(LabelGenerator labelGenerator, ValueEmitter valueEmitter) {
    this.labelGenerator = labelGenerator;
    this.valueEmitter = valueEmitter;
  }

  void emitCmpOp(IrProgramModel.CmpOp cmpOp, FunctionContext ctx) {
    valueEmitter.emit(cmpOp.left(), ctx, "R0");
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Save left");
    valueEmitter.emit(cmpOp.right(), ctx, "R0");
    ctx.emitter().emitInstruction("MOVE", List.of("R0", "R1"), "Right");
    ctx.emitter().emitInstruction("POP", List.of("R0"), "Left");

    String trueLabel = labelGenerator.newLabel("L_CMP_TRUE");
    String endLabel = labelGenerator.newLabel("L_CMP_END");

    ctx.emitter().emitInstruction("CMP", List.of("R0", "R1"), null);
    ctx.emitter().emitInstruction(conditionJump(cmpOp.op()), List.of(trueLabel), null);
    ctx.emitter().emitInstruction("MOVE", List.of("0", "R0"), "False");
    ctx.emitter().emitInstruction("JP", List.of(endLabel), null);
    ctx.emitter().emitLabel(trueLabel, null);
    ctx.emitter().emitInstruction("MOVE", List.of("1", "R0"), "True");
    ctx.emitter().emitLabel(endLabel, null);
  }

  private String conditionJump(IrProgramModel.CmpOpName op) {
    return switch (op) {
      case EQ -> "JP_EQ";
      case NE -> "JP_NE";
      case LT -> "JP_SLT";
      case LE -> "JP_SLE";
      case GT -> "JP_SGT";
      case GE -> "JP_SGE";
    };
  }
}

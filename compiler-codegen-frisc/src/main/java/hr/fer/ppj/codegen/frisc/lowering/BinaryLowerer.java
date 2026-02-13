package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.List;

/**
 * Lowers binary operations into FRISC instructions.
 */
final class BinaryLowerer {
  private final ValueEmitter valueEmitter;
  private final ImmediateEmitter immediateEmitter;

  BinaryLowerer(ValueEmitter valueEmitter, ImmediateEmitter immediateEmitter) {
    this.valueEmitter = valueEmitter;
    this.immediateEmitter = immediateEmitter;
  }

  void emitBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    if (LoweringSupport.isFloat(binOp.resultType()) && emitFastFloatBinOp(binOp, ctx)) {
      return;
    }

    if (!LoweringSupport.isFloat(binOp.resultType()) && emitFastIntBinOp(binOp, ctx)) {
      return;
    }

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

  private boolean emitFastFloatBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    Integer leftConst = floatConstRawValue(binOp.left());
    Integer rightConst = floatConstRawValue(binOp.right());

    switch (binOp.op()) {
      case ADD -> {
        if (isConst(leftConst, 0)) {
          valueEmitter.emit(binOp.right(), ctx, "R0");
          return true;
        }
        if (isConst(rightConst, 0)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
      }
      case SUB -> {
        if (isConst(rightConst, 0)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
      }
      case MUL -> {
        if (isConst(leftConst, 0) || isConst(rightConst, 0)) {
          emitIntConst(0, ctx);
          return true;
        }
        if (isConst(leftConst, 1 << 16)) {
          valueEmitter.emit(binOp.right(), ctx, "R0");
          return true;
        }
        if (isConst(rightConst, 1 << 16)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
      }
      case DIV -> {
        if (isConst(leftConst, 0)) {
          emitIntConst(0, ctx);
          return true;
        }
        if (isConst(rightConst, 1 << 16)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
      }
      default -> {
        return false;
      }
    }

    return false;
  }

  private boolean emitFastIntBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    Integer leftConst = intConstValue(binOp.left());
    Integer rightConst = intConstValue(binOp.right());

    if (leftConst != null && rightConst != null) {
      Integer folded = foldConstBinOp(binOp.op(), leftConst, rightConst);
      if (folded != null) {
        emitIntConst(folded, ctx);
        return true;
      }
    }

    switch (binOp.op()) {
      case MUL -> {
        if (isConst(leftConst, 0) || isConst(rightConst, 0)) {
          emitIntConst(0, ctx);
          return true;
        }
        if (isConst(leftConst, 1)) {
          valueEmitter.emit(binOp.right(), ctx, "R0");
          return true;
        }
        if (isConst(rightConst, 1)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
        if (isConst(leftConst, -1)) {
          emitNegated(binOp.right(), ctx);
          return true;
        }
        if (isConst(rightConst, -1)) {
          emitNegated(binOp.left(), ctx);
          return true;
        }
        if (rightConst != null && isPositivePowerOfTwo(rightConst)) {
          emitShiftedMul(binOp.left(), rightConst, ctx);
          return true;
        }
        if (leftConst != null && isPositivePowerOfTwo(leftConst)) {
          emitShiftedMul(binOp.right(), leftConst, ctx);
          return true;
        }
        if (rightConst != null && isSmallConstantMulCandidate(rightConst)) {
          emitSmallConstantMul(binOp.left(), rightConst, ctx);
          return true;
        }
        if (leftConst != null && isSmallConstantMulCandidate(leftConst)) {
          emitSmallConstantMul(binOp.right(), leftConst, ctx);
          return true;
        }
      }
      case DIV -> {
        if (isConst(rightConst, 1)) {
          valueEmitter.emit(binOp.left(), ctx, "R0");
          return true;
        }
        if (isConst(rightConst, -1)) {
          emitNegated(binOp.left(), ctx);
          return true;
        }
        if (isConst(leftConst, 0) && rightConst != null && rightConst != 0) {
          emitIntConst(0, ctx);
          return true;
        }
      }
      case MOD -> {
        if (isConst(rightConst, 1) || isConst(rightConst, -1)) {
          emitIntConst(0, ctx);
          return true;
        }
        if (isConst(leftConst, 0) && rightConst != null && rightConst != 0) {
          emitIntConst(0, ctx);
          return true;
        }
      }
      default -> {
        return false;
      }
    }

    return false;
  }

  private Integer foldConstBinOp(IrProgramModel.BinOpName op, int left, int right) {
    return switch (op) {
      case ADD -> left + right;
      case SUB -> left - right;
      case MUL -> left * right;
      case DIV -> intDivide(left, right);
      case MOD -> intModulo(left, right);
      case AND -> left & right;
      case OR -> left | right;
      case XOR -> left ^ right;
      case SHL -> left << right;
      case SHR -> left >> right;
    };
  }

  private Integer intDivide(int left, int right) {
    if (right == 0) {
      return 0;
    }
    if (left == Integer.MIN_VALUE && right == -1) {
      return Integer.MIN_VALUE;
    }
    return left / right;
  }

  private Integer intModulo(int left, int right) {
    if (right == 0 || right == -1) {
      return 0;
    }
    return left % right;
  }

  private void emitShiftedMul(IrProgramModel.Value value, int multiplier, FunctionContext ctx) {
    valueEmitter.emit(value, ctx, "R0");
    int shiftAmount = Integer.numberOfTrailingZeros(multiplier);
    ctx.emitter().emitInstruction(
        "SHL",
        List.of("R0", LoweringSupport.formatImmediate(shiftAmount), "R0"),
        "x * " + multiplier);
  }

  private void emitNegated(IrProgramModel.Value value, FunctionContext ctx) {
    valueEmitter.emit(value, ctx, "R0");
    ctx.emitter().emitInstruction("MOVE", List.of("0", "R1"), "Zero");
    ctx.emitter().emitInstruction("SUB", List.of("R1", "R0", "R0"), "Negate");
  }

  private void emitSmallConstantMul(IrProgramModel.Value value, int multiplier, FunctionContext ctx) {
    int absMultiplier = Math.abs(multiplier);
    valueEmitter.emit(value, ctx, "R0");
    ctx.emitter().emitInstruction("MOVE", List.of("0", "R2"), "Const mul acc");
    ctx.emitter().emitInstruction("MOVE", List.of("R0", "R3"), "Const mul term");

    while (absMultiplier != 0) {
      if ((absMultiplier & 1) != 0) {
        ctx.emitter().emitInstruction("ADD", List.of("R2", "R3", "R2"), "Acc += term");
      }
      absMultiplier >>>= 1;
      if (absMultiplier != 0) {
        ctx.emitter().emitInstruction("SHL", List.of("R3", "1", "R3"), "Next term");
      }
    }

    if (multiplier < 0) {
      ctx.emitter().emitInstruction("MOVE", List.of("0", "R1"), "Zero");
      ctx.emitter().emitInstruction("SUB", List.of("R1", "R2", "R0"), "Negate");
      return;
    }
    ctx.emitter().emitInstruction("MOVE", List.of("R2", "R0"), "Const mul result");
  }

  private void emitIntConst(int value, FunctionContext ctx) {
    immediateEmitter.emitLoadImmediate(value, ctx, "R0", "Folded int");
  }

  private Integer intConstValue(IrProgramModel.Value value) {
    if (!(value instanceof IrProgramModel.Const c)) {
      return null;
    }
    if (c.constant() instanceof hr.fer.ppj.ir.model.IrConst.IntConst intConst) {
      return intConst.value();
    }
    if (c.constant() instanceof hr.fer.ppj.ir.model.IrConst.CharConst charConst) {
      return charConst.value() & 0xFF;
    }
    return null;
  }

  private Integer floatConstRawValue(IrProgramModel.Value value) {
    if (!(value instanceof IrProgramModel.Const c)) {
      return null;
    }
    if (c.constant() instanceof hr.fer.ppj.ir.model.IrConst.FloatConst floatConst) {
      return LoweringSupport.floatToQ16_16(floatConst.value());
    }
    return null;
  }

  private boolean isConst(Integer value, int expected) {
    return value != null && value == expected;
  }

  private boolean isPositivePowerOfTwo(int value) {
    return value > 0 && (value & (value - 1)) == 0;
  }

  private boolean isSmallConstantMulCandidate(int value) {
    int abs = Math.abs(value);
    return abs == 3 || abs == 5 || abs == 6 || abs == 7 || abs == 9 || abs == 10 || abs == 12 || abs == 15;
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

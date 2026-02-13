package hr.fer.ppj.opt.rules.arith;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Typed constant folding for arithmetic, comparisons, unary ops, and casts.
 */
public final class TypedConstantFoldingPass implements IrPass {

  @Override
  public String name() {
    return "typed-constant-folding";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());

      for (IrBlock block : function.blocks()) {
        BlockResult result = foldBlock(block);
        blocks.add(result.block());
        functionChanged |= result.changed();
      }

      if (functionChanged) {
        changed = true;
        functions.add(new IrFunction(
            function.name(),
            function.parameters(),
            function.returnType(),
            function.localsBytes(),
            function.alignBytes(),
            function.slots(),
            blocks));
      } else {
        functions.add(function);
      }
    }

    if (!changed) {
      return PassResult.unchanged(program);
    }
    return PassResult.changed(new IrProgram(program.globals(), program.structDefs(), functions));
  }

  private BlockResult foldBlock(IrBlock block) {
    boolean changed = false;
    List<IrInstruction> rewrittenInstructions = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        IrRhs folded = foldRhs(assign.rhs());
        rewrittenInstructions.add(new IrInstruction.IrAssignInstr(assign.dest(), folded));
        changed |= !folded.equals(assign.rhs());
      } else {
        rewrittenInstructions.add(instruction);
      }
    }

    IrTerminator terminator = block.terminator();
    IrTerminator rewrittenTerminator = terminator;
    if (terminator instanceof IrTerminator.IrBrTerm br) {
      IrValue foldedCondition = foldValue(br.condition());
      rewrittenTerminator = new IrTerminator.IrBrTerm(foldedCondition, br.trueLabel(), br.falseLabel());
      changed |= !rewrittenTerminator.equals(terminator);
    } else if (terminator instanceof IrTerminator.IrRetTerm ret && ret.value() != null) {
      IrValue foldedValue = foldValue(ret.value());
      rewrittenTerminator = new IrTerminator.IrRetTerm(foldedValue);
      changed |= !rewrittenTerminator.equals(terminator);
    }

    if (!changed) {
      return new BlockResult(block, false);
    }

    return new BlockResult(new IrBlock(block.label(), rewrittenInstructions, rewrittenTerminator), true);
  }

  private IrRhs foldRhs(IrRhs rhs) {
    if (rhs instanceof IrRhs.BinOp binOp) {
      IrConst left = constOf(binOp.left());
      IrConst right = constOf(binOp.right());
      if (left != null && right != null) {
        IrConst folded = foldBinOp(binOp.op(), left, right, binOp.resultType());
        if (folded != null) {
          return new IrRhs.ConstRhs(folded);
        }
      }
      return binOp;
    }

    if (rhs instanceof IrRhs.CmpOp cmpOp) {
      IrConst left = constOf(cmpOp.left());
      IrConst right = constOf(cmpOp.right());
      if (left != null && right != null) {
        Boolean folded = foldCmp(cmpOp.op(), left, right);
        if (folded != null) {
          return new IrRhs.ConstRhs(new IrConst.IntConst(folded ? 1 : 0, IrPrimitiveType.BOOL));
        }
      }
      return cmpOp;
    }

    if (rhs instanceof IrRhs.UnaryOp unaryOp) {
      IrConst operand = constOf(unaryOp.operand());
      if (operand != null) {
        IrConst folded = foldUnary(unaryOp, operand);
        if (folded != null) {
          return new IrRhs.ConstRhs(folded);
        }
      }
      return unaryOp;
    }

    if (rhs instanceof IrRhs.CastOp castOp) {
      IrConst operand = constOf(castOp.operand());
      if (operand != null) {
        IrConst folded = foldCast(castOp, operand);
        if (folded != null) {
          return new IrRhs.ConstRhs(folded);
        }
      }
      return castOp;
    }

    return rhs;
  }

  private IrValue foldValue(IrValue value) {
    return value;
  }

  private IrConst foldBinOp(IrRhs.BinOpName op, IrConst left, IrConst right, IrType resultType) {
    if (resultType == IrPrimitiveType.FLOAT) {
      Integer leftRaw = asFloatRaw(left);
      Integer rightRaw = asFloatRaw(right);
      if (leftRaw == null || rightRaw == null) {
        return null;
      }

      int resultRaw = switch (op) {
        case ADD -> Q16FloatSemantics.addRaw(leftRaw, rightRaw);
        case SUB -> Q16FloatSemantics.subRaw(leftRaw, rightRaw);
        case MUL -> Q16FloatSemantics.mulRaw(leftRaw, rightRaw);
        case DIV -> Q16FloatSemantics.divRaw(leftRaw, rightRaw);
        default -> Integer.MIN_VALUE;
      };

      if (resultRaw == Integer.MIN_VALUE) {
        return null;
      }
      if (!Q16FloatSemantics.isRoundTripStable(resultRaw)) {
        return null;
      }
      return new IrConst.FloatConst(Q16FloatSemantics.toFloat(resultRaw));
    }

    if (!(resultType instanceof IrPrimitiveType primitiveType)) {
      return null;
    }

    Integer leftInt = asIntValue(left);
    Integer rightInt = asIntValue(right);
    if (leftInt == null || rightInt == null) {
      return null;
    }

    int result = switch (op) {
      case ADD -> leftInt + rightInt;
      case SUB -> leftInt - rightInt;
      case MUL -> leftInt * rightInt;
      case DIV -> rightInt == 0 ? 0 : Int32Semantics.divide(leftInt, rightInt);
      case MOD -> rightInt == 0 ? 0 : Int32Semantics.modulo(leftInt, rightInt);
      case AND -> leftInt & rightInt;
      case OR -> leftInt | rightInt;
      case XOR -> leftInt ^ rightInt;
      case SHL -> leftInt << rightInt;
      case SHR -> leftInt >> rightInt;
    };

    return normalizeIntConst(result, primitiveType);
  }

  private Boolean foldCmp(IrRhs.CmpOpName op, IrConst left, IrConst right) {
    if (left.type() == IrPrimitiveType.FLOAT && right.type() == IrPrimitiveType.FLOAT) {
      Integer leftRaw = asFloatRaw(left);
      Integer rightRaw = asFloatRaw(right);
      if (leftRaw == null || rightRaw == null) {
        return null;
      }
      return evaluateComparison(op, leftRaw, rightRaw);
    }

    Integer leftInt = asIntValue(left);
    Integer rightInt = asIntValue(right);
    if (leftInt == null || rightInt == null) {
      return null;
    }
    return evaluateComparison(op, leftInt, rightInt);
  }

  private IrConst foldUnary(IrRhs.UnaryOp unaryOp, IrConst operand) {
    if (unaryOp.resultType() == IrPrimitiveType.FLOAT) {
      Integer raw = asFloatRaw(operand);
      if (raw == null || unaryOp.op() != IrRhs.UnaryOpName.NEG) {
        return null;
      }
      int negated = -raw;
      if (!Q16FloatSemantics.isRoundTripStable(negated)) {
        return null;
      }
      return new IrConst.FloatConst(Q16FloatSemantics.toFloat(negated));
    }

    if (!(unaryOp.resultType() instanceof IrPrimitiveType primitiveType)) {
      return null;
    }

    Integer intValue = asIntValue(operand);
    if (intValue == null) {
      return null;
    }

    int result = switch (unaryOp.op()) {
      case NEG -> -intValue;
      case NOT -> intValue == 0 ? 1 : 0;
      case BITNOT -> ~intValue;
    };

    return normalizeIntConst(result, primitiveType);
  }

  private IrConst foldCast(IrRhs.CastOp castOp, IrConst operand) {
    int raw = switch (operand) {
      case IrConst.FloatConst floatConst -> Q16FloatSemantics.toRaw(floatConst.value());
      case IrConst.IntConst intConst -> intConst.value();
      case IrConst.CharConst charConst -> charConst.value() & 0xFF;
      case IrConst.NullConst ignored -> 0;
      case IrConst.ArrayConst ignored -> Integer.MIN_VALUE;
    };

    if (raw == Integer.MIN_VALUE) {
      return null;
    }

    int casted = switch (castOp.op()) {
      case TRUNC, ZEXT -> raw & 0xFF;
      case SEXT -> (raw << 24) >> 24;
      case PTRCAST -> raw;
      case ITOF -> raw << 16;
      case FTOI -> raw >> 16;
    };

    if (castOp.resultType() == IrPrimitiveType.FLOAT) {
      if (!Q16FloatSemantics.isRoundTripStable(casted)) {
        return null;
      }
      return new IrConst.FloatConst(Q16FloatSemantics.toFloat(casted));
    }

    if (castOp.resultType() instanceof IrPrimitiveType primitiveType) {
      return normalizeIntConst(casted, primitiveType);
    }

    if (castOp.resultType() != null) {
      if (casted == 0 && castOp.resultType() instanceof IrPointerType) {
        return new IrConst.NullConst(castOp.resultType());
      }
      return new IrConst.IntConst(casted, castOp.resultType());
    }

    return null;
  }

  private static IrConst normalizeIntConst(int value, IrPrimitiveType type) {
    return switch (type) {
      case INT32 -> new IrConst.IntConst(value, IrPrimitiveType.INT32);
      case BOOL -> new IrConst.IntConst(value == 0 ? 0 : 1, IrPrimitiveType.BOOL);
      case CHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.CHAR);
      case UCHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.UCHAR);
      case FLOAT -> null;
    };
  }

  private static Boolean evaluateComparison(IrRhs.CmpOpName op, int left, int right) {
    return switch (op) {
      case EQ -> left == right;
      case NE -> left != right;
      case LT -> left < right;
      case LE -> left <= right;
      case GT -> left > right;
      case GE -> left >= right;
    };
  }

  private static IrConst constOf(IrValue value) {
    return value instanceof IrConst constant ? constant : null;
  }

  private static Integer asFloatRaw(IrConst constant) {
    if (constant instanceof IrConst.FloatConst floatConst) {
      return Q16FloatSemantics.toRaw(floatConst.value());
    }
    return null;
  }

  private static Integer asIntValue(IrConst constant) {
    if (constant instanceof IrConst.IntConst intConst) {
      return intConst.value();
    }
    if (constant instanceof IrConst.CharConst charConst) {
      return charConst.value() & 0xFF;
    }
    if (constant instanceof IrConst.NullConst) {
      return 0;
    }
    return null;
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }
}

package hr.fer.ppj.opt.rules.arith;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * int32 algebraic simplifications and constant folding.
 */
public final class Int32ArithmeticPass implements IrPass {

  @Override
  public String name() {
    return "int32-arithmetic";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());
      for (IrBlock block : function.blocks()) {
        BlockResult blockResult = simplifyBlock(block);
        blocks.add(blockResult.block());
        functionChanged |= blockResult.changed();
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

  private BlockResult simplifyBlock(IrBlock block) {
    boolean changed = false;
    List<IrInstruction> instructions = new ArrayList<>(block.instructions().size());
    Map<Integer, IrRhs> definitions = new HashMap<>();

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        IrRhs simplifiedRhs = simplifyRhs(assign.rhs(), definitions);
        IrInstruction rewritten = new IrInstruction.IrAssignInstr(assign.dest(), simplifiedRhs);
        instructions.add(rewritten);
        definitions.put(assign.dest().index(), simplifiedRhs);
        changed |= !simplifiedRhs.equals(assign.rhs());
      } else {
        instructions.add(instruction);
      }
    }

    if (!changed) {
      return new BlockResult(block, false);
    }
    return new BlockResult(new IrBlock(block.label(), instructions, block.terminator()), true);
  }

  private IrRhs simplifyRhs(IrRhs rhs, Map<Integer, IrRhs> definitions) {
    return switch (rhs) {
      case IrRhs.BinOp binOp -> simplifyBinOp(binOp);
      case IrRhs.UnaryOp unaryOp -> simplifyUnary(unaryOp, definitions);
      default -> rhs;
    };
  }

  private IrRhs simplifyUnary(IrRhs.UnaryOp unaryOp, Map<Integer, IrRhs> definitions) {
    if (unaryOp.op() != IrRhs.UnaryOpName.NEG || unaryOp.resultType() != IrPrimitiveType.INT32) {
      return unaryOp;
    }

    if (unaryOp.operand() instanceof IrTemp temp) {
      IrRhs previousDefinition = definitions.get(temp.index());
      if (previousDefinition instanceof IrRhs.UnaryOp inner
          && inner.op() == IrRhs.UnaryOpName.NEG
          && inner.resultType() == IrPrimitiveType.INT32) {
        return identity(inner.operand());
      }
    }

    return unaryOp;
  }

  private IrRhs simplifyBinOp(IrRhs.BinOp binOp) {
    if (binOp.resultType() != IrPrimitiveType.INT32) {
      return binOp;
    }

    IrValue left = binOp.left();
    IrValue right = binOp.right();
    Integer leftConst = intConstValue(left);
    Integer rightConst = intConstValue(right);

    switch (binOp.op()) {
      case ADD:
        if (isConst(rightConst, 0)) {
          return identity(left);
        }
        if (isConst(leftConst, 0)) {
          return identity(right);
        }
        if (leftConst != null && rightConst != null) {
          return intConst(leftConst + rightConst);
        }
        return binOp;

      case SUB:
        if (isConst(rightConst, 0)) {
          return identity(left);
        }
        if (left.equals(right)) {
          return intConst(0);
        }
        if (leftConst != null && rightConst != null) {
          return intConst(leftConst - rightConst);
        }
        return binOp;

      case MUL:
        if (isConst(leftConst, 0) || isConst(rightConst, 0)) {
          return intConst(0);
        }
        if (isConst(rightConst, 1)) {
          return identity(left);
        }
        if (isConst(leftConst, 1)) {
          return identity(right);
        }
        if (isConst(rightConst, -1)) {
          return neg(left);
        }
        if (isConst(leftConst, -1)) {
          return neg(right);
        }
        if (leftConst != null && rightConst != null) {
          return intConst(leftConst * rightConst);
        }
        return binOp;

      case DIV:
        if (isConst(rightConst, 1)) {
          return identity(left);
        }
        if (isConst(rightConst, -1)) {
          return neg(left);
        }
        if (leftConst != null && rightConst != null && rightConst != 0) {
          return intConst(Int32Semantics.divide(leftConst, rightConst));
        }
        return binOp;

      case MOD:
        if (isConst(rightConst, 1) || isConst(rightConst, -1)) {
          return intConst(0);
        }
        if (leftConst != null && rightConst != null && rightConst != 0) {
          return intConst(Int32Semantics.modulo(leftConst, rightConst));
        }
        return binOp;

      default:
        return binOp;
    }
  }

  private static IrRhs identity(IrValue value) {
    return new IrRhs.BinOp(
        IrRhs.BinOpName.ADD,
        value,
        new IrConst.IntConst(0, IrPrimitiveType.INT32),
        IrPrimitiveType.INT32);
  }

  private static IrRhs neg(IrValue value) {
    return new IrRhs.UnaryOp(IrRhs.UnaryOpName.NEG, value, IrPrimitiveType.INT32);
  }

  private static IrRhs intConst(int value) {
    return new IrRhs.ConstRhs(new IrConst.IntConst(value, IrPrimitiveType.INT32));
  }

  private static Integer intConstValue(IrValue value) {
    if (value instanceof IrConst.IntConst intConst && intConst.type() == IrPrimitiveType.INT32) {
      return intConst.value();
    }
    return null;
  }

  private static boolean isConst(Integer value, int expected) {
    return value != null && value == expected;
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }
}

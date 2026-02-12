package hr.fer.ppj.opt.rules.shift;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import hr.fer.ppj.opt.rules.arith.Int32Semantics;
import java.util.ArrayList;
import java.util.List;

/**
 * Shift simplifications for int32 arithmetic.
 */
public final class Int32ShiftPass implements IrPass {

  @Override
  public String name() {
    return "int32-shift";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());
      for (IrBlock block : function.blocks()) {
        BlockResult result = simplifyBlock(block);
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

  private BlockResult simplifyBlock(IrBlock block) {
    boolean changed = false;
    List<IrInstruction> rewritten = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign
          && assign.rhs() instanceof IrRhs.BinOp binOp) {
        IrRhs simplified = simplifyBinOp(binOp);
        rewritten.add(new IrInstruction.IrAssignInstr(assign.dest(), simplified));
        changed |= !simplified.equals(binOp);
      } else {
        rewritten.add(instruction);
      }
    }

    if (!changed) {
      return new BlockResult(block, false);
    }
    return new BlockResult(new IrBlock(block.label(), rewritten, block.terminator()), true);
  }

  private IrRhs simplifyBinOp(IrRhs.BinOp binOp) {
    if (binOp.resultType() != IrPrimitiveType.INT32) {
      return binOp;
    }

    Integer leftConst = intConstValue(binOp.left());
    Integer rightConst = intConstValue(binOp.right());

    if (binOp.op() == IrRhs.BinOpName.SHL || binOp.op() == IrRhs.BinOpName.SHR) {
      if (isConst(rightConst, 0)) {
        return identity(binOp.left());
      }
      return binOp;
    }

    if (binOp.op() != IrRhs.BinOpName.MUL) {
      return binOp;
    }

    if (rightConst != null && Int32Semantics.isPowerOfTwo(rightConst) && rightConst > 1) {
      return shiftLeft(binOp.left(), Int32Semantics.powerOfTwoShiftAmount(rightConst));
    }

    if (leftConst != null && Int32Semantics.isPowerOfTwo(leftConst) && leftConst > 1) {
      return shiftLeft(binOp.right(), Int32Semantics.powerOfTwoShiftAmount(leftConst));
    }

    return binOp;
  }

  private static IrRhs shiftLeft(IrValue value, int amount) {
    return new IrRhs.BinOp(
        IrRhs.BinOpName.SHL,
        value,
        new IrConst.IntConst(amount, IrPrimitiveType.INT32),
        IrPrimitiveType.INT32);
  }

  private static IrRhs identity(IrValue value) {
    return new IrRhs.BinOp(
        IrRhs.BinOpName.ADD,
        value,
        new IrConst.IntConst(0, IrPrimitiveType.INT32),
        IrPrimitiveType.INT32);
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

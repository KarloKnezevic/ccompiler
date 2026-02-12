package hr.fer.ppj.opt.rules.temps;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
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
 * Local copy/alias propagation for temp values.
 */
public final class CopyPropagationPass implements IrPass {

  @Override
  public String name() {
    return "copy-propagation";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());
      for (IrBlock block : function.blocks()) {
        BlockResult result = propagate(block);
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

  private BlockResult propagate(IrBlock block) {
    Map<Integer, Integer> useCounts = IrUsageAnalyzer.countUses(block);
    Map<Integer, IrValue> aliases = new HashMap<>();

    boolean changed = false;
    List<IrInstruction> rewritten = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      IrInstruction mappedInstruction =
          IrValueRewriter.rewriteInstruction(instruction, value -> resolveAlias(value, aliases));
      changed |= !mappedInstruction.equals(instruction);

      if (mappedInstruction instanceof IrInstruction.IrAssignInstr assign) {
        IrValue aliasSource = identitySource(assign.rhs());
        if (aliasSource != null && useCounts.getOrDefault(assign.dest().index(), 0) == 1) {
          aliases.put(assign.dest().index(), resolveAlias(aliasSource, aliases));
          changed = true;
          continue;
        }
      }

      rewritten.add(mappedInstruction);
    }

    IrTerminator rewrittenTerminator =
        IrValueRewriter.rewriteTerminator(block.terminator(), value -> resolveAlias(value, aliases));
    changed |= !rewrittenTerminator.equals(block.terminator());

    if (!changed) {
      return new BlockResult(block, false);
    }

    return new BlockResult(new IrBlock(block.label(), rewritten, rewrittenTerminator), true);
  }

  private IrValue resolveAlias(IrValue value, Map<Integer, IrValue> aliases) {
    IrValue current = value;
    int guard = 0;

    while (current instanceof IrTemp temp && aliases.containsKey(temp.index()) && guard < 64) {
      current = aliases.get(temp.index());
      guard++;
    }

    return current;
  }

  private IrValue identitySource(IrRhs rhs) {
    if (rhs instanceof IrRhs.BinOp binOp) {
      if (binOp.resultType() != IrPrimitiveType.INT32) {
        return null;
      }

      Integer leftConst = intConstValue(binOp.left());
      Integer rightConst = intConstValue(binOp.right());

      return switch (binOp.op()) {
        case ADD -> {
          if (isConst(rightConst, 0)) {
            yield binOp.left();
          }
          if (isConst(leftConst, 0)) {
            yield binOp.right();
          }
          yield null;
        }
        case SUB -> isConst(rightConst, 0) ? binOp.left() : null;
        case MUL, DIV -> isConst(rightConst, 1) ? binOp.left() : null;
        case SHL, SHR -> isConst(rightConst, 0) ? binOp.left() : null;
        default -> null;
      };
    }

    if (rhs instanceof IrRhs.CastOp castOp
        && castOp.op() == IrRhs.CastName.PTRCAST
        && castOp.operand().type().equals(castOp.resultType())) {
      return castOp.operand();
    }

    return null;
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

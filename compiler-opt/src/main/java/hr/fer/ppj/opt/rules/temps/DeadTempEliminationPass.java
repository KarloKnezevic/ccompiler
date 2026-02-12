package hr.fer.ppj.opt.rules.temps;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Removes dead pure temp assignments.
 */
public final class DeadTempEliminationPass implements IrPass {

  @Override
  public String name() {
    return "dead-temp-elimination";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());

      for (IrBlock block : function.blocks()) {
        BlockResult result = eliminate(block);
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

  private BlockResult eliminate(IrBlock block) {
    boolean changed = false;
    Set<Integer> live = new HashSet<>(IrUsageAnalyzer.usedTemps(block.terminator()));
    List<IrInstruction> reverseKept = new ArrayList<>(block.instructions().size());

    List<IrInstruction> instructions = block.instructions();
    for (int i = instructions.size() - 1; i >= 0; i--) {
      IrInstruction instruction = instructions.get(i);
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        int dest = assign.dest().index();
        if (!live.contains(dest) && IrUsageAnalyzer.isPure(assign.rhs())) {
          changed = true;
          continue;
        }

        live.remove(dest);
        live.addAll(IrUsageAnalyzer.usedTemps(assign));
        reverseKept.add(instruction);
      } else {
        live.addAll(IrUsageAnalyzer.usedTemps(instruction));
        reverseKept.add(instruction);
      }
    }

    if (!changed) {
      return new BlockResult(block, false);
    }

    Collections.reverse(reverseKept);
    return new BlockResult(new IrBlock(block.label(), reverseKept, block.terminator()), true);
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }
}

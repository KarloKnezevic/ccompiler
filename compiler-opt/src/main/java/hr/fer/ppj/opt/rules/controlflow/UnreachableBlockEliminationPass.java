package hr.fer.ppj.opt.rules.controlflow;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes blocks that are unreachable from the function entry block.
 */
public final class UnreachableBlockEliminationPass implements IrPass {

  @Override
  public String name() {
    return "unreachable-block-elimination";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> rewrittenFunctions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      FunctionResult result = eliminate(function);
      rewrittenFunctions.add(result.function());
      changed |= result.changed();
    }

    if (!changed) {
      return PassResult.unchanged(program);
    }

    return PassResult.changed(
        new IrProgram(program.globals(), program.structDefs(), rewrittenFunctions));
  }

  private FunctionResult eliminate(IrFunction function) {
    List<IrBlock> blocks = function.blocks();
    if (blocks.isEmpty()) {
      return new FunctionResult(function, false);
    }

    String entryLabel = blocks.getFirst().label();
    Map<String, IrBlock> blockByLabel = new HashMap<>();
    for (IrBlock block : blocks) {
      blockByLabel.put(block.label(), block);
    }

    Set<String> reachable = computeReachable(entryLabel, blockByLabel);
    if (reachable.size() == blocks.size()) {
      return new FunctionResult(function, false);
    }

    List<IrBlock> filtered = new ArrayList<>(reachable.size());
    for (IrBlock block : blocks) {
      if (reachable.contains(block.label())) {
        filtered.add(block);
      }
    }

    IrFunction rewritten = new IrFunction(
        function.name(),
        function.parameters(),
        function.returnType(),
        function.localsBytes(),
        function.alignBytes(),
        function.slots(),
        filtered);
    return new FunctionResult(rewritten, true);
  }

  private Set<String> computeReachable(String entryLabel, Map<String, IrBlock> blockByLabel) {
    Set<String> visited = new HashSet<>();
    ArrayDeque<String> worklist = new ArrayDeque<>();
    worklist.add(entryLabel);

    while (!worklist.isEmpty()) {
      String label = worklist.removeFirst();
      if (!visited.add(label)) {
        continue;
      }

      IrBlock block = blockByLabel.get(label);
      if (block == null) {
        continue;
      }

      for (String successor : successors(block.terminator())) {
        if (!visited.contains(successor)) {
          worklist.addLast(successor);
        }
      }
    }

    return visited;
  }

  private List<String> successors(IrTerminator terminator) {
    return switch (terminator) {
      case IrTerminator.IrJmpTerm jmp -> List.of(jmp.label());
      case IrTerminator.IrBrTerm br -> List.of(br.trueLabel(), br.falseLabel());
      case IrTerminator.IrRetTerm ignored -> List.of();
    };
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }
}

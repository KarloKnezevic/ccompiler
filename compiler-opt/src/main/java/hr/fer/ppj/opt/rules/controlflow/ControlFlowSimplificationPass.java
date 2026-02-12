package hr.fer.ppj.opt.rules.controlflow;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simplifies constant branches and trivial jump blocks.
 */
public final class ControlFlowSimplificationPass implements IrPass {

  @Override
  public String name() {
    return "control-flow-simplification";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      FunctionResult result = simplifyFunction(function);
      functions.add(result.function());
      changed |= result.changed();
    }

    if (!changed) {
      return PassResult.unchanged(program);
    }
    return PassResult.changed(new IrProgram(program.globals(), program.structDefs(), functions));
  }

  private FunctionResult simplifyFunction(IrFunction function) {
    if (function.blocks().isEmpty()) {
      return new FunctionResult(function, false);
    }

    boolean changed = false;
    List<IrBlock> blocks = function.blocks();

    List<IrBlock> afterBranchFold = foldConstantBranches(blocks);
    if (!afterBranchFold.equals(blocks)) {
      changed = true;
      blocks = afterBranchFold;
    }

    boolean localChange = true;
    while (localChange) {
      localChange = false;

      RetargetResult retargetResult = retargetThroughPassthrough(blocks);
      if (retargetResult.changed()) {
        localChange = true;
        changed = true;
      }
      blocks = retargetResult.blocks();

      List<IrBlock> withoutPassthrough = removePassthroughBlocks(blocks);
      if (!withoutPassthrough.equals(blocks)) {
        localChange = true;
        changed = true;
        blocks = withoutPassthrough;
      }

      MergeResult mergeResult = mergeJumpToNext(blocks);
      if (mergeResult.changed()) {
        localChange = true;
        changed = true;
        blocks = mergeResult.blocks();
      }
    }

    if (!changed) {
      return new FunctionResult(function, false);
    }

    IrFunction rewritten = new IrFunction(
        function.name(),
        function.parameters(),
        function.returnType(),
        function.localsBytes(),
        function.alignBytes(),
        function.slots(),
        blocks);
    return new FunctionResult(rewritten, true);
  }

  private List<IrBlock> foldConstantBranches(List<IrBlock> blocks) {
    List<IrBlock> rewritten = new ArrayList<>(blocks.size());

    for (IrBlock block : blocks) {
      IrTerminator terminator = block.terminator();
      if (terminator instanceof IrTerminator.IrBrTerm brTerm) {
        Boolean constant = boolConst(brTerm.condition());
        if (constant != null) {
          String target = constant ? brTerm.trueLabel() : brTerm.falseLabel();
          rewritten.add(new IrBlock(block.label(), block.instructions(), new IrTerminator.IrJmpTerm(target)));
          continue;
        }
      }
      rewritten.add(block);
    }

    return rewritten;
  }

  private RetargetResult retargetThroughPassthrough(List<IrBlock> blocks) {
    if (blocks.isEmpty()) {
      return new RetargetResult(blocks, false);
    }

    Map<String, IrBlock> byLabel = mapByLabel(blocks);
    Set<String> passthroughLabels = passthroughLabels(blocks);
    if (passthroughLabels.isEmpty()) {
      return new RetargetResult(blocks, false);
    }

    boolean changed = false;
    List<IrBlock> rewritten = new ArrayList<>(blocks.size());

    for (IrBlock block : blocks) {
      IrTerminator terminator = block.terminator();
      IrTerminator newTerminator = switch (terminator) {
        case IrTerminator.IrJmpTerm jmp ->
            new IrTerminator.IrJmpTerm(resolveTarget(jmp.label(), passthroughLabels, byLabel));
        case IrTerminator.IrBrTerm br ->
            new IrTerminator.IrBrTerm(
                br.condition(),
                resolveTarget(br.trueLabel(), passthroughLabels, byLabel),
                resolveTarget(br.falseLabel(), passthroughLabels, byLabel));
        case IrTerminator.IrRetTerm ret -> ret;
      };

      changed |= !newTerminator.equals(terminator);
      rewritten.add(new IrBlock(block.label(), block.instructions(), newTerminator));
    }

    return new RetargetResult(rewritten, changed);
  }

  private List<IrBlock> removePassthroughBlocks(List<IrBlock> blocks) {
    if (blocks.isEmpty()) {
      return blocks;
    }

    String entryLabel = blocks.get(0).label();
    Set<String> passthroughLabels = passthroughLabels(blocks);
    if (passthroughLabels.isEmpty()) {
      return blocks;
    }

    List<IrBlock> filtered = new ArrayList<>(blocks.size());
    for (IrBlock block : blocks) {
      if (!block.label().equals(entryLabel) && passthroughLabels.contains(block.label())) {
        continue;
      }
      filtered.add(block);
    }

    return filtered;
  }

  private MergeResult mergeJumpToNext(List<IrBlock> blocks) {
    if (blocks.size() < 2) {
      return new MergeResult(blocks, false);
    }

    List<IrBlock> current = new ArrayList<>(blocks);
    boolean changed = false;

    int index = 0;
    while (index < current.size() - 1) {
      IrBlock block = current.get(index);
      IrBlock next = current.get(index + 1);
      if (!(block.terminator() instanceof IrTerminator.IrJmpTerm jmp)
          || !jmp.label().equals(next.label())) {
        index++;
        continue;
      }

      Map<String, Integer> predecessorCounts = predecessorCounts(current);
      if (predecessorCounts.getOrDefault(next.label(), 0) != 1) {
        index++;
        continue;
      }

      List<IrInstruction> mergedInstructions =
          new ArrayList<>(block.instructions().size() + next.instructions().size());
      mergedInstructions.addAll(block.instructions());
      mergedInstructions.addAll(next.instructions());

      IrBlock merged = new IrBlock(block.label(), mergedInstructions, next.terminator());
      current.set(index, merged);
      current.remove(index + 1);
      changed = true;
      if (index > 0) {
        index--;
      }
    }

    return new MergeResult(current, changed);
  }

  private Map<String, IrBlock> mapByLabel(List<IrBlock> blocks) {
    Map<String, IrBlock> byLabel = new HashMap<>();
    for (IrBlock block : blocks) {
      byLabel.put(block.label(), block);
    }
    return byLabel;
  }

  private Set<String> passthroughLabels(List<IrBlock> blocks) {
    Set<String> labels = new HashSet<>();
    for (IrBlock block : blocks) {
      if (block.instructions().isEmpty() && block.terminator() instanceof IrTerminator.IrJmpTerm) {
        labels.add(block.label());
      }
    }
    return labels;
  }

  private String resolveTarget(String label, Set<String> passthroughLabels, Map<String, IrBlock> byLabel) {
    String current = label;
    Set<String> visited = new HashSet<>();

    while (passthroughLabels.contains(current) && visited.add(current)) {
      IrBlock block = byLabel.get(current);
      if (block == null || !(block.terminator() instanceof IrTerminator.IrJmpTerm jmp)) {
        break;
      }
      current = jmp.label();
    }

    return current;
  }

  private Map<String, Integer> predecessorCounts(List<IrBlock> blocks) {
    Map<String, Integer> counts = new HashMap<>();

    for (IrBlock block : blocks) {
      switch (block.terminator()) {
        case IrTerminator.IrJmpTerm jmp -> counts.merge(jmp.label(), 1, Integer::sum);
        case IrTerminator.IrBrTerm br -> {
          counts.merge(br.trueLabel(), 1, Integer::sum);
          counts.merge(br.falseLabel(), 1, Integer::sum);
        }
        case IrTerminator.IrRetTerm ignored -> {
        }
      }
    }

    return counts;
  }

  private Boolean boolConst(hr.fer.ppj.ir.model.IrValue value) {
    if (value instanceof IrConst.IntConst intConst && intConst.type() == IrPrimitiveType.BOOL) {
      return intConst.value() != 0;
    }
    return null;
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record RetargetResult(List<IrBlock> blocks, boolean changed) {
  }

  private record MergeResult(List<IrBlock> blocks, boolean changed) {
  }
}

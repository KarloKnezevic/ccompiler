package hr.fer.ppj.opt.rules.loop;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conservative LICM that reorders loop-invariant pure assignments to the top
 * of loop blocks. It avoids cross-block temp motion to keep IR validity under
 * current verifier constraints.
 */
public final class LoopInvariantCodeMotionPass implements IrPass {

  @Override
  public String name() {
    return "loop-invariant-code-motion";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      FunctionResult result = rewriteFunction(function);
      functions.add(result.function());
      changed |= result.changed();
    }

    if (!changed) {
      return PassResult.unchanged(program);
    }

    return PassResult.changed(new IrProgram(program.globals(), program.structDefs(), functions));
  }

  private FunctionResult rewriteFunction(IrFunction function) {
    if (function.blocks().isEmpty()) {
      return new FunctionResult(function, false);
    }

    Set<String> loopBlocks = detectLoopBlocks(function.blocks());
    if (loopBlocks.isEmpty()) {
      return new FunctionResult(function, false);
    }

    boolean changed = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());

    for (IrBlock block : function.blocks()) {
      if (!loopBlocks.contains(block.label())) {
        rewrittenBlocks.add(block);
        continue;
      }

      BlockResult result = reorderInvariantAssignments(block);
      rewrittenBlocks.add(result.block());
      changed |= result.changed();
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
        rewrittenBlocks);
    return new FunctionResult(rewritten, true);
  }

  private BlockResult reorderInvariantAssignments(IrBlock block) {
    List<IrInstruction> instructions = block.instructions();
    if (instructions.size() < 2) {
      return new BlockResult(block, false);
    }

    Map<Integer, Integer> defIndex = new HashMap<>();
    for (int i = 0; i < instructions.size(); i++) {
      IrInstruction instruction = instructions.get(i);
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        defIndex.put(assign.dest().index(), i);
      }
    }

    Set<Integer> invariantIndexes = new HashSet<>();
    boolean stable = false;
    while (!stable) {
      stable = true;
      for (int i = 0; i < instructions.size(); i++) {
        if (invariantIndexes.contains(i)) {
          continue;
        }
        IrInstruction instruction = instructions.get(i);
        if (!(instruction instanceof IrInstruction.IrAssignInstr assign)) {
          continue;
        }
        if (!isPure(assign.rhs())) {
          continue;
        }

        boolean invariant = true;
        for (Integer usedTemp : usedTemps(instruction)) {
          Integer definedAt = defIndex.get(usedTemp);
          if (definedAt != null && !invariantIndexes.contains(definedAt)) {
            invariant = false;
            break;
          }
        }

        if (invariant) {
          invariantIndexes.add(i);
          stable = false;
        }
      }
    }

    if (invariantIndexes.isEmpty()) {
      return new BlockResult(block, false);
    }

    boolean hasMotionOpportunity = false;
    boolean seenNonInvariant = false;
    for (int i = 0; i < instructions.size(); i++) {
      boolean invariant = invariantIndexes.contains(i);
      if (!invariant) {
        seenNonInvariant = true;
      } else if (seenNonInvariant) {
        hasMotionOpportunity = true;
        break;
      }
    }

    if (!hasMotionOpportunity) {
      return new BlockResult(block, false);
    }

    List<IrInstruction> moved = new ArrayList<>(instructions.size());
    for (int i = 0; i < instructions.size(); i++) {
      if (invariantIndexes.contains(i)) {
        moved.add(instructions.get(i));
      }
    }
    for (int i = 0; i < instructions.size(); i++) {
      if (!invariantIndexes.contains(i)) {
        moved.add(instructions.get(i));
      }
    }

    return new BlockResult(new IrBlock(block.label(), moved, block.terminator()), true);
  }

  private Set<String> detectLoopBlocks(List<IrBlock> blocks) {
    Map<String, Integer> order = new HashMap<>();
    for (int i = 0; i < blocks.size(); i++) {
      order.put(blocks.get(i).label(), i);
    }

    Map<String, List<String>> predecessors = predecessors(blocks);
    Set<String> loopBlocks = new HashSet<>();

    for (IrBlock tail : blocks) {
      Integer tailIndex = order.get(tail.label());
      if (tailIndex == null) {
        continue;
      }

      for (String successor : successors(tail)) {
        Integer headIndex = order.get(successor);
        if (headIndex == null || headIndex > tailIndex) {
          continue;
        }

        loopBlocks.addAll(naturalLoop(successor, tail.label(), predecessors));
      }
    }

    return loopBlocks;
  }

  private Map<String, List<String>> predecessors(List<IrBlock> blocks) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (IrBlock block : blocks) {
      result.put(block.label(), new ArrayList<>());
    }

    for (IrBlock block : blocks) {
      for (String successor : successors(block)) {
        result.computeIfAbsent(successor, ignored -> new ArrayList<>()).add(block.label());
      }
    }

    return result;
  }

  private List<String> successors(IrBlock block) {
    return switch (block.terminator()) {
      case hr.fer.ppj.ir.model.IrTerminator.IrJmpTerm jmp -> List.of(jmp.label());
      case hr.fer.ppj.ir.model.IrTerminator.IrBrTerm br -> List.of(br.trueLabel(), br.falseLabel());
      case hr.fer.ppj.ir.model.IrTerminator.IrRetTerm ignored -> List.of();
    };
  }

  private Set<String> naturalLoop(String head, String tail, Map<String, List<String>> predecessors) {
    Set<String> loop = new HashSet<>();
    ArrayDeque<String> work = new ArrayDeque<>();
    loop.add(head);
    loop.add(tail);
    work.push(tail);

    while (!work.isEmpty()) {
      String current = work.pop();
      for (String pred : predecessors.getOrDefault(current, List.of())) {
        if (loop.add(pred)) {
          work.push(pred);
        }
      }
    }

    return loop;
  }

  private boolean isPure(IrRhs rhs) {
    return switch (rhs) {
      case IrRhs.Load ignored -> false;
      case IrRhs.Call ignored -> false;
      case IrRhs.IncDecOp ignored -> false;
      default -> true;
    };
  }

  private Set<Integer> usedTemps(IrInstruction instruction) {
    Set<Integer> used = new HashSet<>();
    if (instruction instanceof IrInstruction.IrAssignInstr assign) {
      collectRhs(assign.rhs(), used);
    } else if (instruction instanceof IrInstruction.IrStoreInstr store) {
      collectValue(store.addr(), used);
      collectValue(store.value(), used);
    } else if (instruction instanceof IrInstruction.IrVoidCallInstr call) {
      for (IrValue arg : call.args()) {
        collectValue(arg, used);
      }
    }
    return used;
  }

  private void collectRhs(IrRhs rhs, Set<Integer> sink) {
    switch (rhs) {
      case IrRhs.AddrOfSymbol ignored -> {
      }
      case IrRhs.ConstRhs ignored -> {
      }
      case IrRhs.AddrIndex addrIndex -> {
        collectValue(addrIndex.base(), sink);
        collectValue(addrIndex.idx(), sink);
      }
      case IrRhs.AddrField addrField -> collectValue(addrField.base(), sink);
      case IrRhs.Load load -> collectValue(load.addr(), sink);
      case IrRhs.BinOp binOp -> {
        collectValue(binOp.left(), sink);
        collectValue(binOp.right(), sink);
      }
      case IrRhs.CmpOp cmpOp -> {
        collectValue(cmpOp.left(), sink);
        collectValue(cmpOp.right(), sink);
      }
      case IrRhs.Call call -> {
        for (IrValue arg : call.args()) {
          collectValue(arg, sink);
        }
      }
      case IrRhs.UnaryOp unaryOp -> collectValue(unaryOp.operand(), sink);
      case IrRhs.IncDecOp incDecOp -> collectValue(incDecOp.addr(), sink);
      case IrRhs.CastOp castOp -> collectValue(castOp.operand(), sink);
    }
  }

  private void collectValue(IrValue value, Set<Integer> sink) {
    if (value instanceof IrTemp temp) {
      sink.add(temp.index());
    }
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }
}

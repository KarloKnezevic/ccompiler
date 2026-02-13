package hr.fer.ppj.opt.rules.memory;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTerminator;
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
 * Eliminates stores to tracked slots when the stored value is never read
 * before the slot is overwritten or function exits.
 */
public final class DeadSlotStoreEliminationPass implements IrPass {

  @Override
  public String name() {
    return "dead-slot-store-elimination";
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
    Set<String> trackedSlots = SlotAddressResolver.trackedSlots(function);
    if (trackedSlots.isEmpty() || function.blocks().isEmpty()) {
      return new FunctionResult(function, false);
    }

    Map<String, IrBlock> blockByLabel = new HashMap<>();
    Map<String, Set<String>> liveIn = new HashMap<>();
    Map<String, Set<String>> liveOut = new HashMap<>();
    Map<String, BlockAddressTracker> trackers = new HashMap<>();
    for (IrBlock block : function.blocks()) {
      blockByLabel.put(block.label(), block);
      liveIn.put(block.label(), Set.of());
      liveOut.put(block.label(), Set.of());
      trackers.put(block.label(), BlockAddressTracker.build(block, trackedSlots));
    }

    boolean stable = false;
    while (!stable) {
      stable = true;

      for (int i = function.blocks().size() - 1; i >= 0; i--) {
        IrBlock block = function.blocks().get(i);
        Set<String> out = unionSuccessorLiveIn(block, liveIn, blockByLabel);
        Set<String> in = transfer(block, trackers.get(block.label()), trackedSlots, out, false).liveIn();

        if (!out.equals(liveOut.get(block.label()))) {
          liveOut.put(block.label(), out);
          stable = false;
        }

        if (!in.equals(liveIn.get(block.label()))) {
          liveIn.put(block.label(), in);
          stable = false;
        }
      }
    }

    boolean functionChanged = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());
    for (IrBlock block : function.blocks()) {
      BlockTransferResult transfer =
          transfer(block, trackers.get(block.label()), trackedSlots, liveOut.get(block.label()), true);
      rewrittenBlocks.add(transfer.block());
      functionChanged |= transfer.changed();
    }

    if (!functionChanged) {
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

  private Set<String> unionSuccessorLiveIn(
      IrBlock block,
      Map<String, Set<String>> liveIn,
      Map<String, IrBlock> blockByLabel) {

    Set<String> out = new HashSet<>();
    for (String successor : successors(block.terminator(), blockByLabel)) {
      out.addAll(liveIn.getOrDefault(successor, Set.of()));
    }
    return Set.copyOf(out);
  }

  private List<String> successors(IrTerminator terminator, Map<String, IrBlock> blockByLabel) {
    return switch (terminator) {
      case IrTerminator.IrJmpTerm jmp -> blockByLabel.containsKey(jmp.label()) ? List.of(jmp.label()) : List.of();
      case IrTerminator.IrBrTerm br -> {
        List<String> labels = new ArrayList<>(2);
        if (blockByLabel.containsKey(br.trueLabel())) {
          labels.add(br.trueLabel());
        }
        if (blockByLabel.containsKey(br.falseLabel())) {
          labels.add(br.falseLabel());
        }
        yield labels;
      }
      case IrTerminator.IrRetTerm ignored -> List.of();
    };
  }

  private BlockTransferResult transfer(
      IrBlock block,
      BlockAddressTracker tracker,
      Set<String> trackedSlots,
      Set<String> liveOut,
      boolean removeDeadStores) {

    Set<String> live = new HashSet<>(liveOut);
    List<IrInstruction> keptReversed = new ArrayList<>(block.instructions().size());
    boolean changed = false;

    List<IrInstruction> instructions = block.instructions();
    for (int index = instructions.size() - 1; index >= 0; index--) {
      IrInstruction instruction = instructions.get(index);

      if (instruction instanceof IrInstruction.IrVoidCallInstr) {
        live.addAll(trackedSlots);
        keptReversed.add(instruction);
        continue;
      }

      if (instruction instanceof IrInstruction.IrStoreInstr store) {
        String slot = tracker.resolveSlot(store.addr(), index);
        if (slot != null) {
          boolean needed = live.contains(slot);
          if (!needed && removeDeadStores) {
            changed = true;
          } else {
            keptReversed.add(instruction);
          }
          live.remove(slot);
        } else {
          keptReversed.add(instruction);
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        IrRhs rhs = assign.rhs();
        if (rhs instanceof IrRhs.Load load) {
          String slot = tracker.resolveSlot(load.addr(), index);
          if (slot != null) {
            live.add(slot);
          } else {
            live.addAll(trackedSlots);
          }
        } else if (rhs instanceof IrRhs.Call || rhs instanceof IrRhs.IncDecOp) {
          live.addAll(trackedSlots);
        }
        keptReversed.add(instruction);
        continue;
      }

      keptReversed.add(instruction);
    }

    if (!removeDeadStores || !changed) {
      return new BlockTransferResult(block, Set.copyOf(live), removeDeadStores && changed);
    }

    List<IrInstruction> kept = new ArrayList<>(keptReversed.size());
    for (int i = keptReversed.size() - 1; i >= 0; i--) {
      kept.add(keptReversed.get(i));
    }

    return new BlockTransferResult(
        new IrBlock(block.label(), kept, block.terminator()),
        Set.copyOf(live),
        true);
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record BlockTransferResult(IrBlock block, Set<String> liveIn, boolean changed) {
  }
}

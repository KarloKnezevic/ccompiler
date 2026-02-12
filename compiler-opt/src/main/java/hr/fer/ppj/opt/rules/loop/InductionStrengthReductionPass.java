package hr.fer.ppj.opt.rules.loop;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
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
 * Performs loop-focused strength reduction for simple induction variables.
 */
public final class InductionStrengthReductionPass implements IrPass {

  @Override
  public String name() {
    return "induction-strength-reduction";
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

    LoopInfo loopInfo = analyzeLoops(function.blocks());
    if (loopInfo.inductionSlotsByBlock().isEmpty()) {
      return new FunctionResult(function, false);
    }

    int nextTemp = maxTemp(function.blocks()) + 1;
    boolean changed = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());

    for (IrBlock block : function.blocks()) {
      Set<String> inductionSlots = loopInfo.inductionSlotsByBlock().get(block.label());
      if (inductionSlots == null || inductionSlots.isEmpty()) {
        rewrittenBlocks.add(block);
        continue;
      }

      BlockRewriteResult blockResult = rewriteBlock(block, inductionSlots, nextTemp);
      rewrittenBlocks.add(blockResult.block());
      changed |= blockResult.changed();
      nextTemp = blockResult.nextTemp();
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

  private BlockRewriteResult rewriteBlock(IrBlock block, Set<String> inductionSlots, int startTemp) {
    int nextTemp = startTemp;
    boolean changed = false;

    Map<Integer, IrSymbolRef> tempAddresses = new HashMap<>();
    Map<Integer, String> tempLoadSlot = new HashMap<>();

    List<IrInstruction> rewritten = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        tempAddresses.remove(assign.dest().index());
        tempLoadSlot.remove(assign.dest().index());

        if (assign.rhs() instanceof IrRhs.AddrOfSymbol addrOfSymbol) {
          tempAddresses.put(assign.dest().index(), addrOfSymbol.symbolRef());
          rewritten.add(assign);
          continue;
        }

        if (assign.rhs() instanceof IrRhs.Load load) {
          IrSymbolRef symbolRef = resolveAddress(load.addr(), tempAddresses);
          if (symbolRef != null && symbolRef.kind() == IrSymbolRef.Kind.LOCAL) {
            tempLoadSlot.put(assign.dest().index(), symbolRef.name());
          }
          rewritten.add(assign);
          continue;
        }

        if (assign.rhs() instanceof IrRhs.BinOp binOp
            && binOp.op() == IrRhs.BinOpName.MUL
            && binOp.resultType() == IrPrimitiveType.INT32) {

          Match match = matchInductionMultiply(binOp, tempLoadSlot, inductionSlots);
          if (match != null) {
            int shiftAmount = shiftAmount(match.multiplier());
            if (shiftAmount > 0) {
              IrTemp tmp = new IrTemp(nextTemp++, IrPrimitiveType.INT32);
              rewritten.add(new IrInstruction.IrAssignInstr(
                  tmp,
                  new IrRhs.BinOp(
                      IrRhs.BinOpName.SHL,
                      match.inductionTemp(),
                      new IrConst.IntConst(shiftAmount, IrPrimitiveType.INT32),
                      IrPrimitiveType.INT32)));
              rewritten.add(new IrInstruction.IrAssignInstr(
                  assign.dest(),
                  new IrRhs.BinOp(
                      IrRhs.BinOpName.ADD,
                      tmp,
                      match.inductionTemp(),
                      IrPrimitiveType.INT32)));
              changed = true;
              continue;
            }
          }
        }

        rewritten.add(assign);
        continue;
      }

      rewritten.add(instruction);
    }

    if (!changed) {
      return new BlockRewriteResult(block, false, startTemp);
    }

    return new BlockRewriteResult(new IrBlock(block.label(), rewritten, block.terminator()), true, nextTemp);
  }

  private Match matchInductionMultiply(
      IrRhs.BinOp binOp,
      Map<Integer, String> tempLoadSlot,
      Set<String> inductionSlots) {

    if (binOp.left() instanceof IrTemp leftTemp
        && binOp.right() instanceof IrConst.IntConst rightConst
        && rightConst.type() == IrPrimitiveType.INT32
        && isInductionTemp(leftTemp, tempLoadSlot, inductionSlots)
        && isSupportedMultiplier(rightConst.value())) {
      return new Match(leftTemp, rightConst.value());
    }

    if (binOp.right() instanceof IrTemp rightTemp
        && binOp.left() instanceof IrConst.IntConst leftConst
        && leftConst.type() == IrPrimitiveType.INT32
        && isInductionTemp(rightTemp, tempLoadSlot, inductionSlots)
        && isSupportedMultiplier(leftConst.value())) {
      return new Match(rightTemp, leftConst.value());
    }

    return null;
  }

  private boolean isInductionTemp(IrTemp temp, Map<Integer, String> tempLoadSlot, Set<String> inductionSlots) {
    String slot = tempLoadSlot.get(temp.index());
    return slot != null && inductionSlots.contains(slot);
  }

  private int shiftAmount(int multiplier) {
    return switch (multiplier) {
      case 3 -> 1;
      case 5 -> 2;
      case 9 -> 3;
      default -> -1;
    };
  }

  private boolean isSupportedMultiplier(int multiplier) {
    return multiplier == 3 || multiplier == 5 || multiplier == 9;
  }

  private LoopInfo analyzeLoops(List<IrBlock> blocks) {
    Map<String, Integer> order = new HashMap<>();
    Map<String, IrBlock> byLabel = new LinkedHashMap<>();
    for (int i = 0; i < blocks.size(); i++) {
      IrBlock block = blocks.get(i);
      order.put(block.label(), i);
      byLabel.put(block.label(), block);
    }

    Map<String, List<String>> predecessors = predecessors(blocks);
    Map<String, Set<String>> inductionByBlock = new HashMap<>();

    for (IrBlock tail : blocks) {
      for (String successor : successors(tail.terminator())) {
        Integer tailIndex = order.get(tail.label());
        Integer headIndex = order.get(successor);
        if (tailIndex == null || headIndex == null || headIndex > tailIndex) {
          continue;
        }

        Set<String> loopBlocks = naturalLoop(successor, tail.label(), predecessors);
        Set<String> inductionSlots = inductionSlots(loopBlocks, byLabel);
        if (inductionSlots.isEmpty()) {
          continue;
        }

        for (String label : loopBlocks) {
          inductionByBlock.computeIfAbsent(label, ignored -> new HashSet<>()).addAll(inductionSlots);
        }
      }
    }

    Map<String, Set<String>> immutable = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : inductionByBlock.entrySet()) {
      immutable.put(entry.getKey(), Set.copyOf(entry.getValue()));
    }
    return new LoopInfo(Map.copyOf(immutable));
  }

  private Set<String> inductionSlots(Set<String> loopBlocks, Map<String, IrBlock> byLabel) {
    Set<String> result = new HashSet<>();

    for (String label : loopBlocks) {
      IrBlock block = byLabel.get(label);
      if (block == null) {
        continue;
      }

      Map<Integer, IrSymbolRef> tempAddress = new HashMap<>();
      Map<Integer, String> tempLoadSlot = new HashMap<>();
      Map<Integer, Increment> tempIncrements = new HashMap<>();

      for (IrInstruction instruction : block.instructions()) {
        if (instruction instanceof IrInstruction.IrAssignInstr assign) {
          tempAddress.remove(assign.dest().index());
          tempLoadSlot.remove(assign.dest().index());
          tempIncrements.remove(assign.dest().index());

          if (assign.rhs() instanceof IrRhs.AddrOfSymbol addrOfSymbol) {
            tempAddress.put(assign.dest().index(), addrOfSymbol.symbolRef());
            continue;
          }

          if (assign.rhs() instanceof IrRhs.Load load) {
            IrSymbolRef symbolRef = resolveAddress(load.addr(), tempAddress);
            if (symbolRef != null && symbolRef.kind() == IrSymbolRef.Kind.LOCAL) {
              tempLoadSlot.put(assign.dest().index(), symbolRef.name());
            }
            continue;
          }

          if (assign.rhs() instanceof IrRhs.BinOp binOp
              && binOp.resultType() == IrPrimitiveType.INT32) {
            Increment increment = detectIncrement(binOp, tempLoadSlot);
            if (increment != null && increment.step() != 0) {
              tempIncrements.put(assign.dest().index(), increment);
            }
          }
          continue;
        }

        if (instruction instanceof IrInstruction.IrStoreInstr store) {
          IrSymbolRef symbolRef = resolveAddress(store.addr(), tempAddress);
          if (symbolRef == null || symbolRef.kind() != IrSymbolRef.Kind.LOCAL) {
            continue;
          }

          if (store.value() instanceof IrTemp valueTemp) {
            Increment increment = tempIncrements.get(valueTemp.index());
            if (increment != null && increment.slot().equals(symbolRef.name())) {
              result.add(symbolRef.name());
            }
          }
        }
      }
    }

    return result;
  }

  private Increment detectIncrement(IrRhs.BinOp binOp, Map<Integer, String> tempLoadSlot) {
    if (binOp.op() == IrRhs.BinOpName.ADD) {
      if (binOp.left() instanceof IrTemp leftTemp
          && binOp.right() instanceof IrConst.IntConst rightConst
          && rightConst.type() == IrPrimitiveType.INT32) {
        String slot = tempLoadSlot.get(leftTemp.index());
        if (slot != null) {
          return new Increment(slot, rightConst.value());
        }
      }

      if (binOp.right() instanceof IrTemp rightTemp
          && binOp.left() instanceof IrConst.IntConst leftConst
          && leftConst.type() == IrPrimitiveType.INT32) {
        String slot = tempLoadSlot.get(rightTemp.index());
        if (slot != null) {
          return new Increment(slot, leftConst.value());
        }
      }
    }

    if (binOp.op() == IrRhs.BinOpName.SUB
        && binOp.left() instanceof IrTemp leftTemp
        && binOp.right() instanceof IrConst.IntConst rightConst
        && rightConst.type() == IrPrimitiveType.INT32) {
      String slot = tempLoadSlot.get(leftTemp.index());
      if (slot != null) {
        return new Increment(slot, -rightConst.value());
      }
    }

    return null;
  }

  private Set<String> naturalLoop(String header, String tail, Map<String, List<String>> predecessors) {
    Set<String> loop = new HashSet<>();
    loop.add(header);
    loop.add(tail);

    ArrayDeque<String> worklist = new ArrayDeque<>();
    worklist.add(tail);

    while (!worklist.isEmpty()) {
      String current = worklist.removeFirst();
      for (String predecessor : predecessors.getOrDefault(current, List.of())) {
        if (loop.add(predecessor) && !predecessor.equals(header)) {
          worklist.addLast(predecessor);
        }
      }
    }

    return loop;
  }

  private Map<String, List<String>> predecessors(List<IrBlock> blocks) {
    Map<String, List<String>> result = new HashMap<>();
    for (IrBlock block : blocks) {
      result.computeIfAbsent(block.label(), ignored -> new ArrayList<>());
    }

    for (IrBlock block : blocks) {
      for (String successor : successors(block.terminator())) {
        result.computeIfAbsent(successor, ignored -> new ArrayList<>()).add(block.label());
      }
    }

    Map<String, List<String>> immutable = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : result.entrySet()) {
      immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Map.copyOf(immutable);
  }

  private List<String> successors(IrTerminator terminator) {
    return switch (terminator) {
      case IrTerminator.IrJmpTerm jmp -> List.of(jmp.label());
      case IrTerminator.IrBrTerm br -> List.of(br.trueLabel(), br.falseLabel());
      case IrTerminator.IrRetTerm ignored -> List.of();
    };
  }

  private IrSymbolRef resolveAddress(IrValue value, Map<Integer, IrSymbolRef> tempAddress) {
    if (value instanceof IrTemp temp) {
      return tempAddress.get(temp.index());
    }
    return null;
  }

  private int maxTemp(List<IrBlock> blocks) {
    int max = -1;

    for (IrBlock block : blocks) {
      for (IrInstruction instruction : block.instructions()) {
        if (instruction instanceof IrInstruction.IrAssignInstr assign) {
          max = Math.max(max, assign.dest().index());
          max = Math.max(max, maxTemp(assign.rhs()));
        } else if (instruction instanceof IrInstruction.IrStoreInstr store) {
          max = Math.max(max, maxTemp(store.addr()));
          max = Math.max(max, maxTemp(store.value()));
        } else if (instruction instanceof IrInstruction.IrVoidCallInstr call) {
          for (IrValue arg : call.args()) {
            max = Math.max(max, maxTemp(arg));
          }
        }
      }

      max = Math.max(max, maxTemp(block.terminator()));
    }

    return max;
  }

  private int maxTemp(IrRhs rhs) {
    return switch (rhs) {
      case IrRhs.AddrOfSymbol ignored -> -1;
      case IrRhs.ConstRhs ignored -> -1;
      case IrRhs.AddrIndex addrIndex -> Math.max(maxTemp(addrIndex.base()), maxTemp(addrIndex.idx()));
      case IrRhs.AddrField addrField -> maxTemp(addrField.base());
      case IrRhs.Load load -> maxTemp(load.addr());
      case IrRhs.BinOp binOp -> Math.max(maxTemp(binOp.left()), maxTemp(binOp.right()));
      case IrRhs.CmpOp cmpOp -> Math.max(maxTemp(cmpOp.left()), maxTemp(cmpOp.right()));
      case IrRhs.Call call -> {
        int max = -1;
        for (IrValue value : call.args()) {
          max = Math.max(max, maxTemp(value));
        }
        yield max;
      }
      case IrRhs.UnaryOp unaryOp -> maxTemp(unaryOp.operand());
      case IrRhs.IncDecOp incDecOp -> maxTemp(incDecOp.addr());
      case IrRhs.CastOp castOp -> maxTemp(castOp.operand());
    };
  }

  private int maxTemp(IrTerminator terminator) {
    return switch (terminator) {
      case IrTerminator.IrJmpTerm ignored -> -1;
      case IrTerminator.IrRetTerm ret -> ret.value() == null ? -1 : maxTemp(ret.value());
      case IrTerminator.IrBrTerm br -> maxTemp(br.condition());
    };
  }

  private int maxTemp(IrValue value) {
    if (value instanceof IrTemp temp) {
      return temp.index();
    }
    return -1;
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record BlockRewriteResult(IrBlock block, boolean changed, int nextTemp) {
  }

  private record Match(IrTemp inductionTemp, int multiplier) {
  }

  private record Increment(String slot, int step) {
  }

  private record LoopInfo(Map<String, Set<String>> inductionSlotsByBlock) {
  }
}

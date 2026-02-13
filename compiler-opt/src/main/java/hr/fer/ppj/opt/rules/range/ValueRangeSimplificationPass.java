package hr.fer.ppj.opt.rules.range;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Uses conservative int32 range analysis to simplify compare/branch instructions.
 */
public final class ValueRangeSimplificationPass implements IrPass {

  @Override
  public String name() {
    return "value-range-simplification";
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

    Set<String> trackedSlots = trackedInt32Slots(function);
    if (trackedSlots.isEmpty()) {
      return new FunctionResult(function, false);
    }

    Map<String, List<String>> predecessors = predecessors(function.blocks());
    Map<String, Map<String, IntRange>> inStates = new HashMap<>();
    Map<String, Map<String, IntRange>> outStates = new HashMap<>();

    for (IrBlock block : function.blocks()) {
      inStates.put(block.label(), Map.of());
      outStates.put(block.label(), Map.of());
    }

    boolean stable = false;
    while (!stable) {
      stable = true;

      for (IrBlock block : function.blocks()) {
        Map<String, IntRange> incoming = mergePredecessors(block.label(), predecessors, outStates);
        if (block == function.blocks().getFirst()) {
          incoming = Map.of();
        }

        if (!incoming.equals(inStates.get(block.label()))) {
          inStates.put(block.label(), incoming);
          stable = false;
        }

        TransferResult analysis = transfer(block, incoming, trackedSlots, false);
        if (!analysis.outState().equals(outStates.get(block.label()))) {
          outStates.put(block.label(), analysis.outState());
          stable = false;
        }
      }
    }

    boolean functionChanged = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());
    for (IrBlock block : function.blocks()) {
      TransferResult rewritten = transfer(block, inStates.get(block.label()), trackedSlots, true);
      rewrittenBlocks.add(rewritten.block());
      functionChanged |= rewritten.changed();
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

  private TransferResult transfer(
      IrBlock block,
      Map<String, IntRange> initialState,
      Set<String> trackedSlots,
      boolean rewrite) {

    Map<String, IntRange> slotRanges = new HashMap<>(initialState);
    Map<Integer, IntRange> tempRanges = new HashMap<>();
    Map<Integer, Boolean> boolTemps = new HashMap<>();
    Map<Integer, IrSymbolRef> tempAddresses = new HashMap<>();

    boolean changed = false;
    List<IrInstruction> rewrittenInstructions = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        IrRhs rhs = assign.rhs();
        tempRanges.remove(assign.dest().index());
        boolTemps.remove(assign.dest().index());
        tempAddresses.remove(assign.dest().index());

        if (rhs instanceof IrRhs.AddrOfSymbol addrOfSymbol) {
          tempAddresses.put(assign.dest().index(), addrOfSymbol.symbolRef());
        } else if (rhs instanceof IrRhs.ConstRhs constRhs) {
          if (constRhs.constant() instanceof IrConst.IntConst intConst) {
            if (intConst.type() == IrPrimitiveType.INT32) {
              tempRanges.put(assign.dest().index(), IntRange.exact(intConst.value()));
            } else if (intConst.type() == IrPrimitiveType.BOOL) {
              boolTemps.put(assign.dest().index(), intConst.value() != 0);
            }
          }
        } else if (rhs instanceof IrRhs.Load load && load.loadType() == IrPrimitiveType.INT32) {
          IrSymbolRef symbolRef = resolveAddress(load.addr(), tempAddresses);
          if (symbolRef != null && isTracked(symbolRef, trackedSlots)) {
            IntRange range = slotRanges.get(symbolRef.name());
            if (range != null) {
              tempRanges.put(assign.dest().index(), range);
            }
          }
        } else if (rhs instanceof IrRhs.UnaryOp unaryOp
            && unaryOp.op() == IrRhs.UnaryOpName.NEG
            && unaryOp.resultType() == IrPrimitiveType.INT32) {
          IntRange range = intRange(unaryOp.operand(), tempRanges);
          if (range != null && range.isExact()) {
            tempRanges.put(assign.dest().index(), IntRange.exact(-range.min()));
          }
        } else if (rhs instanceof IrRhs.AddrIndex addrIndex) {
          IntRange indexRange = intRange(addrIndex.idx(), tempRanges);
          if (indexRange != null && indexRange.isExact() && rewrite) {
            rhs = new IrRhs.AddrIndex(
                addrIndex.base(),
                new IrConst.IntConst(indexRange.min(), IrPrimitiveType.INT32),
                addrIndex.elemSize(),
                addrIndex.resultType());
          }
        } else if (rhs instanceof IrRhs.BinOp binOp && binOp.resultType() == IrPrimitiveType.INT32) {
          IntRange evaluated = evaluateBinOpRange(binOp, tempRanges);
          if (evaluated != null) {
            tempRanges.put(assign.dest().index(), evaluated);
          }
        } else if (rhs instanceof IrRhs.CmpOp cmpOp) {
          Boolean outcome = evaluateComparison(cmpOp, tempRanges);
          if (outcome != null) {
            boolTemps.put(assign.dest().index(), outcome);
            if (rewrite) {
              rhs = new IrRhs.ConstRhs(new IrConst.IntConst(outcome ? 1 : 0, IrPrimitiveType.BOOL));
            }
          }
        }

        if (rhs instanceof IrRhs.Call || rhs instanceof IrRhs.IncDecOp) {
          slotRanges.clear();
        }

        IrInstruction rewrittenAssign = new IrInstruction.IrAssignInstr(assign.dest(), rhs);
        rewrittenInstructions.add(rewrittenAssign);
        if (rewrite && !rewrittenAssign.equals(assign)) {
          changed = true;
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrStoreInstr store) {
        IrSymbolRef symbolRef = resolveAddress(store.addr(), tempAddresses);
        if (symbolRef != null
            && isTracked(symbolRef, trackedSlots)
            && store.storeType() == IrPrimitiveType.INT32) {
          IntRange valueRange = intRange(store.value(), tempRanges);
          if (valueRange == null) {
            slotRanges.remove(symbolRef.name());
          } else {
            slotRanges.put(symbolRef.name(), valueRange);
          }
        } else {
          slotRanges.clear();
        }

        rewrittenInstructions.add(store);
        continue;
      }

      if (instruction instanceof IrInstruction.IrVoidCallInstr call) {
        rewrittenInstructions.add(call);
        slotRanges.clear();
      }
    }

    IrTerminator terminator = block.terminator();
    IrTerminator rewrittenTerminator = terminator;
    if (terminator instanceof IrTerminator.IrBrTerm brTerm) {
      Boolean outcome = branchCondition(brTerm.condition(), boolTemps);
      if (outcome != null) {
        rewrittenTerminator = new IrTerminator.IrJmpTerm(outcome ? brTerm.trueLabel() : brTerm.falseLabel());
      }
    }

    if (rewrite && !rewrittenTerminator.equals(terminator)) {
      changed = true;
    }

    IrBlock rewrittenBlock = rewrite
        ? new IrBlock(block.label(), rewrittenInstructions, rewrittenTerminator)
        : block;
    return new TransferResult(rewrittenBlock, Map.copyOf(slotRanges), changed);
  }

  private Set<String> trackedInt32Slots(IrFunction function) {
    Set<String> tracked = new HashSet<>();
    for (IrSlot slot : function.slots()) {
      if ((slot.kind() == IrSlot.Kind.LOCAL || slot.kind() == IrSlot.Kind.PARAM)
          && slot.type() == IrPrimitiveType.INT32) {
        tracked.add(slot.name());
      }
    }
    return tracked;
  }

  private Map<String, List<String>> predecessors(List<IrBlock> blocks) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    for (IrBlock block : blocks) {
      result.put(block.label(), new ArrayList<>());
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

  private Map<String, IntRange> mergePredecessors(
      String label,
      Map<String, List<String>> predecessors,
      Map<String, Map<String, IntRange>> outStates) {

    List<String> preds = predecessors.getOrDefault(label, List.of());
    if (preds.isEmpty()) {
      return Map.of();
    }

    List<Map<String, IntRange>> sources = new ArrayList<>(preds.size());
    for (String pred : preds) {
      sources.add(outStates.getOrDefault(pred, Map.of()));
    }

    Set<String> commonSlots = new HashSet<>(sources.getFirst().keySet());
    for (int i = 1; i < sources.size(); i++) {
      commonSlots.retainAll(sources.get(i).keySet());
    }

    Map<String, IntRange> merged = new HashMap<>();
    for (String slot : commonSlots) {
      IntRange range = sources.getFirst().get(slot);
      for (int i = 1; i < sources.size(); i++) {
        range = IntRange.hull(range, sources.get(i).get(slot));
      }
      merged.put(slot, range);
    }

    return Map.copyOf(merged);
  }

  private IntRange evaluateBinOpRange(IrRhs.BinOp binOp, Map<Integer, IntRange> tempRanges) {
    IntRange left = intRange(binOp.left(), tempRanges);
    IntRange right = intRange(binOp.right(), tempRanges);

    if (left == null || right == null) {
      return null;
    }

    if (left.isExact() && right.isExact()) {
      int lv = left.min();
      int rv = right.min();
      return switch (binOp.op()) {
        case ADD -> IntRange.exact(lv + rv);
        case SUB -> IntRange.exact(lv - rv);
        case MUL -> IntRange.exact(lv * rv);
        case DIV -> rv == 0 ? null : IntRange.exact(lv / rv);
        case MOD -> rv == 0 ? null : IntRange.exact(lv % rv);
        case SHL -> IntRange.exact(lv << rv);
        case SHR -> IntRange.exact(lv >> rv);
        case AND -> IntRange.exact(lv & rv);
        case OR -> IntRange.exact(lv | rv);
        case XOR -> IntRange.exact(lv ^ rv);
      };
    }

    if (binOp.op() == IrRhs.BinOpName.ADD && right.isExact()) {
      return left.add(right.min());
    }
    if (binOp.op() == IrRhs.BinOpName.ADD && left.isExact()) {
      return right.add(left.min());
    }
    if (binOp.op() == IrRhs.BinOpName.SUB && right.isExact()) {
      return left.add(-right.min());
    }

    return null;
  }

  private Boolean evaluateComparison(IrRhs.CmpOp cmpOp, Map<Integer, IntRange> tempRanges) {
    IntRange left = intRange(cmpOp.left(), tempRanges);
    IntRange right = intRange(cmpOp.right(), tempRanges);
    if (left == null || right == null) {
      return null;
    }

    return switch (cmpOp.op()) {
      case EQ -> {
        if (left.max() < right.min() || right.max() < left.min()) {
          yield false;
        }
        if (left.isExact() && right.isExact() && left.min() == right.min()) {
          yield true;
        }
        yield null;
      }
      case NE -> {
        if (left.max() < right.min() || right.max() < left.min()) {
          yield true;
        }
        if (left.isExact() && right.isExact() && left.min() == right.min()) {
          yield false;
        }
        yield null;
      }
      case LT -> {
        if (left.max() < right.min()) {
          yield true;
        }
        if (left.min() >= right.max()) {
          yield false;
        }
        yield null;
      }
      case LE -> {
        if (left.max() <= right.min()) {
          yield true;
        }
        if (left.min() > right.max()) {
          yield false;
        }
        yield null;
      }
      case GT -> {
        if (left.min() > right.max()) {
          yield true;
        }
        if (left.max() <= right.min()) {
          yield false;
        }
        yield null;
      }
      case GE -> {
        if (left.min() >= right.max()) {
          yield true;
        }
        if (left.max() < right.min()) {
          yield false;
        }
        yield null;
      }
    };
  }

  private IntRange intRange(IrValue value, Map<Integer, IntRange> tempRanges) {
    if (value instanceof IrConst.IntConst intConst && intConst.type() == IrPrimitiveType.INT32) {
      return IntRange.exact(intConst.value());
    }
    if (value instanceof IrTemp temp) {
      return tempRanges.get(temp.index());
    }
    return null;
  }

  private Boolean branchCondition(IrValue value, Map<Integer, Boolean> boolTemps) {
    if (value instanceof IrConst.IntConst intConst && intConst.type() == IrPrimitiveType.BOOL) {
      return intConst.value() != 0;
    }
    if (value instanceof IrTemp temp) {
      return boolTemps.get(temp.index());
    }
    return null;
  }

  private IrSymbolRef resolveAddress(IrValue value, Map<Integer, IrSymbolRef> tempAddresses) {
    if (value instanceof IrTemp temp) {
      return tempAddresses.get(temp.index());
    }
    return null;
  }

  private boolean isTracked(IrSymbolRef symbolRef, Set<String> trackedSlots) {
    return (symbolRef.kind() == IrSymbolRef.Kind.LOCAL || symbolRef.kind() == IrSymbolRef.Kind.PARAM)
        && trackedSlots.contains(symbolRef.name());
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record TransferResult(IrBlock block, Map<String, IntRange> outState, boolean changed) {
  }

  private record IntRange(int min, int max) {
    static IntRange exact(int value) {
      return new IntRange(value, value);
    }

    static IntRange hull(IntRange left, IntRange right) {
      return new IntRange(Math.min(left.min, right.min), Math.max(left.max, right.max));
    }

    boolean isExact() {
      return min == max;
    }

    IntRange add(int delta) {
      long newMin = (long) min + delta;
      long newMax = (long) max + delta;
      if (newMin < Integer.MIN_VALUE || newMin > Integer.MAX_VALUE) {
        return null;
      }
      if (newMax < Integer.MIN_VALUE || newMax > Integer.MAX_VALUE) {
        return null;
      }
      return new IntRange((int) newMin, (int) newMax);
    }
  }
}

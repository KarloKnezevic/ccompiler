package hr.fer.ppj.opt.rules.memory;

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
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Replaces redundant slot loads with known values when slot contents are unchanged.
 *
 * <p>Constants are propagated across blocks conservatively. Temp forwarding is
 * intentionally local to one block to avoid invalid cross-block temp references.
 */
public final class LoadForwardingPass implements IrPass {

  @Override
  public String name() {
    return "load-forwarding";
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
    if (trackedSlots.isEmpty()) {
      return new FunctionResult(function, false);
    }

    Map<String, List<String>> predecessors = predecessors(function.blocks());
    Map<String, Map<String, IrConst>> inStates = new HashMap<>();
    Map<String, Map<String, IrConst>> outStates = new HashMap<>();

    for (IrBlock block : function.blocks()) {
      inStates.put(block.label(), Map.of());
      outStates.put(block.label(), Map.of());
    }

    boolean stable = false;
    while (!stable) {
      stable = true;
      for (int index = 0; index < function.blocks().size(); index++) {
        IrBlock block = function.blocks().get(index);
        Map<String, IrConst> incoming =
            index == 0
                ? Map.of()
                : mergeIncomingConstants(predecessors.getOrDefault(block.label(), List.of()), outStates);

        if (!incoming.equals(inStates.get(block.label()))) {
          inStates.put(block.label(), incoming);
          stable = false;
        }

        Map<String, IrConst> outgoing = transferConstants(block, trackedSlots, incoming);
        if (!outgoing.equals(outStates.get(block.label()))) {
          outStates.put(block.label(), outgoing);
          stable = false;
        }
      }
    }

    boolean functionChanged = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());
    for (IrBlock block : function.blocks()) {
      BlockResult result = rewriteBlock(block, trackedSlots, inStates.getOrDefault(block.label(), Map.of()));
      rewrittenBlocks.add(result.block());
      functionChanged |= result.changed();
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

  private Map<String, IrConst> mergeIncomingConstants(
      List<String> predecessors,
      Map<String, Map<String, IrConst>> outStates) {

    if (predecessors.isEmpty()) {
      return Map.of();
    }

    Map<String, IrConst> merged = new HashMap<>(outStates.getOrDefault(predecessors.getFirst(), Map.of()));
    for (int i = 1; i < predecessors.size(); i++) {
      Map<String, IrConst> next = outStates.getOrDefault(predecessors.get(i), Map.of());
      merged.entrySet().removeIf(entry -> {
        IrConst other = next.get(entry.getKey());
        return other == null || !other.equals(entry.getValue());
      });
    }
    return Map.copyOf(merged);
  }

  private Map<String, IrConst> transferConstants(
      IrBlock block,
      Set<String> trackedSlots,
      Map<String, IrConst> incomingConstants) {

    Map<Integer, String> addressTemps = new HashMap<>();
    Map<String, IrConst> slotConstants = new HashMap<>(incomingConstants);

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        int destIndex = assign.dest().index();
        addressTemps.remove(destIndex);
        trackAddressAlias(assign, trackedSlots, addressTemps);

        IrRhs rhs = assign.rhs();
        if (rhs instanceof IrRhs.Load load) {
          String slot = SlotAddressResolver.resolveSlot(load.addr(), addressTemps);
          if (slot == null) {
            slotConstants.clear();
          }
        } else if (rhs instanceof IrRhs.Call || rhs instanceof IrRhs.IncDecOp) {
          slotConstants.clear();
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrStoreInstr store) {
        String slot = SlotAddressResolver.resolveSlot(store.addr(), addressTemps);
        if (slot == null) {
          slotConstants.clear();
        } else {
          IrConst constant = normalizeConstantValue(store.value(), store.storeType());
          if (constant == null) {
            slotConstants.remove(slot);
          } else {
            slotConstants.put(slot, constant);
          }
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrVoidCallInstr) {
        slotConstants.clear();
      }
    }

    return Map.copyOf(slotConstants);
  }

  private BlockResult rewriteBlock(
      IrBlock block,
      Set<String> trackedSlots,
      Map<String, IrConst> incomingConstants) {

    boolean changed = false;
    Map<Integer, String> addressTemps = new HashMap<>();
    Map<String, KnownValue> slotValues = new HashMap<>();
    for (Map.Entry<String, IrConst> entry : incomingConstants.entrySet()) {
      slotValues.put(entry.getKey(), new ConstValue(entry.getValue()));
    }

    List<IrInstruction> rewritten = new ArrayList<>(block.instructions().size());
    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        int destIndex = assign.dest().index();
        addressTemps.remove(destIndex);
        invalidateValuesUsingTemp(slotValues, destIndex);
        trackAddressAlias(assign, trackedSlots, addressTemps);

        IrRhs rhs = assign.rhs();
        if (rhs instanceof IrRhs.Load load) {
          String slot = SlotAddressResolver.resolveSlot(load.addr(), addressTemps);
          if (slot != null) {
            KnownValue knownValue = slotValues.get(slot);
            IrRhs forwarded = toForwardedRhs(knownValue, load.loadType());
            if (forwarded != null) {
              rhs = forwarded;
              changed = true;
            }
            slotValues.put(slot, new TempValue(assign.dest()));
          } else {
            slotValues.clear();
          }
        } else if (rhs instanceof IrRhs.Call || rhs instanceof IrRhs.IncDecOp) {
          slotValues.clear();
        }

        rewritten.add(new IrInstruction.IrAssignInstr(assign.dest(), rhs));
        continue;
      }

      if (instruction instanceof IrInstruction.IrStoreInstr store) {
        String slot = SlotAddressResolver.resolveSlot(store.addr(), addressTemps);
        if (slot != null) {
          KnownValue value = normalizeStoredValue(store.value(), store.storeType());
          if (value == null) {
            slotValues.remove(slot);
          } else {
            slotValues.put(slot, value);
          }
        } else {
          slotValues.clear();
        }
        rewritten.add(store);
        continue;
      }

      if (instruction instanceof IrInstruction.IrVoidCallInstr) {
        slotValues.clear();
      }
      rewritten.add(instruction);
    }

    if (!changed) {
      return new BlockResult(block, false);
    }
    return new BlockResult(new IrBlock(block.label(), rewritten, block.terminator()), true);
  }

  private void trackAddressAlias(
      IrInstruction.IrAssignInstr assign,
      Set<String> trackedSlots,
      Map<Integer, String> addressTemps) {

    if (assign.rhs() instanceof IrRhs.AddrOfSymbol addrOfSymbol
        && SlotAddressResolver.isTrackableSymbol(addrOfSymbol.symbolRef(), trackedSlots)) {
      addressTemps.put(assign.dest().index(), addrOfSymbol.symbolRef().name());
      return;
    }

    if (assign.rhs() instanceof IrRhs.CastOp cast
        && cast.op() == IrRhs.CastName.PTRCAST
        && cast.operand() instanceof IrTemp sourceTemp) {
      String sourceSlot = addressTemps.get(sourceTemp.index());
      if (sourceSlot != null) {
        addressTemps.put(assign.dest().index(), sourceSlot);
      }
    }
  }

  private void invalidateValuesUsingTemp(Map<String, KnownValue> slotValues, int tempIndex) {
    slotValues.entrySet().removeIf(entry -> entry.getValue() instanceof TempValue tv && tv.temp().index() == tempIndex);
  }

  private KnownValue normalizeStoredValue(IrValue value, IrType storeType) {
    if (value instanceof IrTemp temp) {
      return temp.type() == storeType ? new TempValue(temp) : null;
    }
    if (!(value instanceof IrConst constant)) {
      return null;
    }
    IrConst normalized = normalizeConstant(constant, storeType);
    return normalized == null ? null : new ConstValue(normalized);
  }

  private IrConst normalizeConstantValue(IrValue value, IrType targetType) {
    if (value instanceof IrConst constant) {
      return normalizeConstant(constant, targetType);
    }
    return null;
  }

  private IrConst normalizeConstant(IrConst constant, IrType targetType) {
    if (constant.type().equals(targetType)) {
      return constant;
    }

    if (!(targetType instanceof IrPrimitiveType primitiveType)) {
      return null;
    }

    if (constant instanceof IrConst.IntConst intConst) {
      int value = intConst.value();
      return switch (primitiveType) {
        case INT32 -> new IrConst.IntConst(value, IrPrimitiveType.INT32);
        case BOOL -> new IrConst.IntConst(value == 0 ? 0 : 1, IrPrimitiveType.BOOL);
        case CHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.CHAR);
        case UCHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.UCHAR);
        case FLOAT -> null;
      };
    }

    if (constant instanceof IrConst.CharConst charConst) {
      int value = charConst.value();
      return switch (primitiveType) {
        case INT32 -> new IrConst.IntConst(value, IrPrimitiveType.INT32);
        case BOOL -> new IrConst.IntConst(value == 0 ? 0 : 1, IrPrimitiveType.BOOL);
        case CHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.CHAR);
        case UCHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.UCHAR);
        case FLOAT -> null;
      };
    }

    if (constant instanceof IrConst.FloatConst floatConst && primitiveType == IrPrimitiveType.FLOAT) {
      return floatConst;
    }
    return null;
  }

  private IrRhs toForwardedRhs(KnownValue knownValue, IrType loadType) {
    if (knownValue == null) {
      return null;
    }

    if (knownValue instanceof ConstValue cv) {
      return cv.constant().type().equals(loadType) ? new IrRhs.ConstRhs(cv.constant()) : null;
    }

    if (knownValue instanceof TempValue tv
        && loadType == IrPrimitiveType.INT32
        && tv.temp().type() == IrPrimitiveType.INT32) {
      return new IrRhs.BinOp(
          IrRhs.BinOpName.ADD,
          tv.temp(),
          new IrConst.IntConst(0, IrPrimitiveType.INT32),
          IrPrimitiveType.INT32);
    }
    return null;
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }

  private sealed interface KnownValue permits ConstValue, TempValue {
  }

  private record ConstValue(IrConst constant) implements KnownValue {
  }

  private record TempValue(IrTemp temp) implements KnownValue {
  }
}

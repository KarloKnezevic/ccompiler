package hr.fer.ppj.opt.rules.flow;

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
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import hr.fer.ppj.opt.rules.arith.Int32Semantics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Function-level constant/copy propagation through local and parameter slots.
 */
public final class GlobalValuePropagationPass implements IrPass {

  @Override
  public String name() {
    return "global-value-propagation";
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

    Map<String, IrType> trackedSlots = trackedSlots(function);
    if (trackedSlots.isEmpty()) {
      return new FunctionResult(function, false);
    }

    FunctionGraph graph = FunctionGraph.of(function.blocks());
    Map<String, Map<String, ValueFact>> inStates = new HashMap<>();
    Map<String, Map<String, ValueFact>> outStates = new HashMap<>();

    for (IrBlock block : function.blocks()) {
      inStates.put(block.label(), Map.of());
      outStates.put(block.label(), Map.of());
    }

    boolean stable = false;
    while (!stable) {
      stable = true;
      for (IrBlock block : function.blocks()) {
        Map<String, ValueFact> incoming = mergePredecessors(block.label(), graph.predecessors(), outStates);
        if (block == function.blocks().getFirst()) {
          incoming = Map.of();
        }

        Map<String, ValueFact> previousIn = inStates.get(block.label());
        if (!incoming.equals(previousIn)) {
          inStates.put(block.label(), incoming);
          stable = false;
        }

        TransferResult transfer = transferBlock(block, incoming, trackedSlots, false);
        Map<String, ValueFact> previousOut = outStates.get(block.label());
        if (!transfer.outState().equals(previousOut)) {
          outStates.put(block.label(), transfer.outState());
          stable = false;
        }
      }
    }

    boolean functionChanged = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());
    for (IrBlock block : function.blocks()) {
      TransferResult transfer = transferBlock(block, inStates.get(block.label()), trackedSlots, true);
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

  private TransferResult transferBlock(
      IrBlock block,
      Map<String, ValueFact> initialState,
      Map<String, IrType> trackedSlots,
      boolean rewrite) {

    Map<String, ValueFact> slotState = new HashMap<>(initialState);
    Map<Integer, ValueFact> tempFacts = new HashMap<>();
    Map<Integer, IrSymbolRef> tempAddresses = new HashMap<>();

    boolean changed = false;
    List<IrInstruction> rewrittenInstructions = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      if (instruction instanceof IrInstruction.IrAssignInstr assign) {
        IrRhs rewrittenRhs = rewrite ? rewriteRhs(assign.rhs(), tempFacts, tempAddresses, slotState, trackedSlots) : assign.rhs();

        if (rewrite && !rewrittenRhs.equals(assign.rhs())) {
          changed = true;
        }

        IrInstruction.IrAssignInstr rewrittenAssign = new IrInstruction.IrAssignInstr(assign.dest(), rewrittenRhs);
        rewrittenInstructions.add(rewrittenAssign);

        tempAddresses.remove(assign.dest().index());
        ValueFact fact = evaluateRhsFact(rewrittenRhs, tempFacts, tempAddresses, slotState, trackedSlots);

        if (rewrittenRhs instanceof IrRhs.AddrOfSymbol addrOfSymbol) {
          tempAddresses.put(assign.dest().index(), addrOfSymbol.symbolRef());
        }

        if (fact == null) {
          tempFacts.remove(assign.dest().index());
        } else {
          tempFacts.put(assign.dest().index(), fact);
        }

        if (hasUnknownMemoryWrite(rewrittenRhs)) {
          slotState.clear();
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrStoreInstr store) {
        IrInstruction rewrittenStore = store;
        if (rewrite) {
          IrValue newValue = rewriteValue(store.value(), tempFacts, null);
          rewrittenStore = new IrInstruction.IrStoreInstr(store.addr(), newValue, store.storeType());
          if (!rewrittenStore.equals(store)) {
            changed = true;
          }
        }
        rewrittenInstructions.add(rewrittenStore);

        IrSymbolRef target = resolveAddress(store.addr(), tempAddresses);
        if (target != null && isTrackedSlot(target, trackedSlots)) {
          ValueFact source = valueFactOf((rewrite ? ((IrInstruction.IrStoreInstr) rewrittenStore).value() : store.value()), tempFacts);
          ValueFact normalized = normalizeForSlot(target.name(), source, trackedSlots, slotState);
          if (normalized == null) {
            slotState.remove(target.name());
          } else if (normalized instanceof CopyFact copy && copy.slot().equals(target.name())) {
            // x = x leaves the slot state unchanged.
          } else {
            slotState.put(target.name(), normalized);
          }
        } else {
          slotState.clear();
        }
        continue;
      }

      if (instruction instanceof IrInstruction.IrVoidCallInstr call) {
        IrInstruction rewrittenCall = call;
        if (rewrite) {
          List<IrValue> rewrittenArgs = new ArrayList<>(call.args().size());
          for (IrValue arg : call.args()) {
            rewrittenArgs.add(rewriteValue(arg, tempFacts, null));
          }
          rewrittenCall = new IrInstruction.IrVoidCallInstr(call.funcName(), rewrittenArgs);
          if (!rewrittenCall.equals(call)) {
            changed = true;
          }
        }
        rewrittenInstructions.add(rewrittenCall);
        slotState.clear();
      }
    }

    IrTerminator terminator = block.terminator();
    IrTerminator rewrittenTerminator = terminator;
    if (rewrite && terminator instanceof IrTerminator.IrBrTerm brTerm) {
      IrValue condition = rewriteValue(brTerm.condition(), tempFacts, IrPrimitiveType.BOOL);
      rewrittenTerminator = new IrTerminator.IrBrTerm(condition, brTerm.trueLabel(), brTerm.falseLabel());
      if (!rewrittenTerminator.equals(terminator)) {
        changed = true;
      }
    } else if (rewrite && terminator instanceof IrTerminator.IrRetTerm retTerm && retTerm.value() != null) {
      IrValue value = rewriteValue(retTerm.value(), tempFacts, null);
      rewrittenTerminator = new IrTerminator.IrRetTerm(value);
      if (!rewrittenTerminator.equals(terminator)) {
        changed = true;
      }
    }

    IrBlock rewrittenBlock = rewrite ? new IrBlock(block.label(), rewrittenInstructions, rewrittenTerminator) : block;
    return new TransferResult(rewrittenBlock, Map.copyOf(slotState), changed);
  }

  private IrRhs rewriteRhs(
      IrRhs rhs,
      Map<Integer, ValueFact> tempFacts,
      Map<Integer, IrSymbolRef> tempAddresses,
      Map<String, ValueFact> slotState,
      Map<String, IrType> trackedSlots) {

    if (rhs instanceof IrRhs.Load load) {
      IrSymbolRef address = resolveAddress(load.addr(), tempAddresses);
      if (address != null && isTrackedSlot(address, trackedSlots)) {
        IrConst constant = resolveConstForSlot(address.name(), slotState, trackedSlots.get(address.name()), new HashSet<>());
        if (constant != null && constant.type().equals(load.loadType())) {
          return new IrRhs.ConstRhs(constant);
        }
      }
      return rhs;
    }

    if (rhs instanceof IrRhs.BinOp binOp) {
      IrValue left = rewriteValue(binOp.left(), tempFacts, null);
      IrValue right = rewriteValue(binOp.right(), tempFacts, null);
      return new IrRhs.BinOp(binOp.op(), left, right, binOp.resultType());
    }

    if (rhs instanceof IrRhs.CmpOp cmpOp) {
      IrValue left = rewriteValue(cmpOp.left(), tempFacts, null);
      IrValue right = rewriteValue(cmpOp.right(), tempFacts, null);
      return new IrRhs.CmpOp(cmpOp.op(), left, right);
    }

    if (rhs instanceof IrRhs.UnaryOp unaryOp) {
      return new IrRhs.UnaryOp(unaryOp.op(), rewriteValue(unaryOp.operand(), tempFacts, null), unaryOp.resultType());
    }

    if (rhs instanceof IrRhs.CastOp castOp) {
      return new IrRhs.CastOp(castOp.op(), rewriteValue(castOp.operand(), tempFacts, null), castOp.resultType());
    }

    if (rhs instanceof IrRhs.AddrIndex addrIndex) {
      return new IrRhs.AddrIndex(
          rewriteValue(addrIndex.base(), tempFacts, null),
          rewriteValue(addrIndex.idx(), tempFacts, null),
          addrIndex.elemSize(),
          addrIndex.resultType());
    }

    if (rhs instanceof IrRhs.AddrField addrField) {
      return new IrRhs.AddrField(
          rewriteValue(addrField.base(), tempFacts, null),
          addrField.structName(),
          addrField.fieldName(),
          addrField.resultType());
    }

    if (rhs instanceof IrRhs.Call call) {
      List<IrValue> args = new ArrayList<>(call.args().size());
      for (IrValue arg : call.args()) {
        args.add(rewriteValue(arg, tempFacts, null));
      }
      return new IrRhs.Call(call.funcName(), args, call.resultType());
    }

    if (rhs instanceof IrRhs.IncDecOp incDecOp) {
      return new IrRhs.IncDecOp(incDecOp.op(), rewriteValue(incDecOp.addr(), tempFacts, null), incDecOp.resultType());
    }

    return rhs;
  }

  private IrValue rewriteValue(IrValue value, Map<Integer, ValueFact> tempFacts, IrType expectedType) {
    if (!(value instanceof IrTemp temp)) {
      return value;
    }

    ValueFact fact = tempFacts.get(temp.index());
    if (!(fact instanceof ConstFact constFact)) {
      return value;
    }

    IrConst constant = constFact.constant();
    if (expectedType != null && !constant.type().equals(expectedType)) {
      return value;
    }

    return constant;
  }

  private ValueFact evaluateRhsFact(
      IrRhs rhs,
      Map<Integer, ValueFact> tempFacts,
      Map<Integer, IrSymbolRef> tempAddresses,
      Map<String, ValueFact> slotState,
      Map<String, IrType> trackedSlots) {

    if (rhs instanceof IrRhs.ConstRhs constRhs) {
      return new ConstFact(constRhs.constant());
    }

    if (rhs instanceof IrRhs.Load load) {
      IrSymbolRef target = resolveAddress(load.addr(), tempAddresses);
      if (target == null || !isTrackedSlot(target, trackedSlots)) {
        return null;
      }

      IrConst constant = resolveConstForSlot(target.name(), slotState, load.loadType(), new HashSet<>());
      if (constant != null) {
        return new ConstFact(constant);
      }

      ValueFact fact = slotState.get(target.name());
      return fact instanceof CopyFact copy ? copy : null;
    }

    if (rhs instanceof IrRhs.UnaryOp unaryOp
        && unaryOp.op() == IrRhs.UnaryOpName.NEG
        && unaryOp.resultType() == IrPrimitiveType.INT32) {
      Integer value = intValue(valueFactOf(unaryOp.operand(), tempFacts));
      if (value != null) {
        return new ConstFact(new IrConst.IntConst(-value, IrPrimitiveType.INT32));
      }
      return null;
    }

    if (rhs instanceof IrRhs.BinOp binOp && binOp.resultType() == IrPrimitiveType.INT32) {
      ValueFact left = valueFactOf(binOp.left(), tempFacts);
      ValueFact right = valueFactOf(binOp.right(), tempFacts);

      Integer leftInt = intValue(left);
      Integer rightInt = intValue(right);

      if (binOp.op() == IrRhs.BinOpName.ADD && rightInt != null && rightInt == 0) {
        return left;
      }
      if (binOp.op() == IrRhs.BinOpName.ADD && leftInt != null && leftInt == 0) {
        return right;
      }
      if (binOp.op() == IrRhs.BinOpName.SUB && rightInt != null && rightInt == 0) {
        return left;
      }

      if (leftInt != null && rightInt != null) {
        return foldIntBinOp(binOp.op(), leftInt, rightInt);
      }
      return null;
    }

    if (rhs instanceof IrRhs.CmpOp cmpOp) {
      ValueFact left = valueFactOf(cmpOp.left(), tempFacts);
      ValueFact right = valueFactOf(cmpOp.right(), tempFacts);
      Integer leftInt = intValue(left);
      Integer rightInt = intValue(right);
      if (leftInt != null && rightInt != null) {
        return new ConstFact(boolConst(compare(cmpOp.op(), leftInt, rightInt)));
      }
      return null;
    }

    if (rhs instanceof IrRhs.CastOp castOp
        && castOp.op() == IrRhs.CastName.PTRCAST
        && castOp.operand().type().equals(castOp.resultType())) {
      return valueFactOf(castOp.operand(), tempFacts);
    }

    return null;
  }

  private ValueFact foldIntBinOp(IrRhs.BinOpName op, int left, int right) {
    return switch (op) {
      case ADD -> new ConstFact(new IrConst.IntConst(left + right, IrPrimitiveType.INT32));
      case SUB -> new ConstFact(new IrConst.IntConst(left - right, IrPrimitiveType.INT32));
      case MUL -> new ConstFact(new IrConst.IntConst(left * right, IrPrimitiveType.INT32));
      case DIV -> right == 0 ? null : new ConstFact(new IrConst.IntConst(Int32Semantics.divide(left, right), IrPrimitiveType.INT32));
      case MOD -> right == 0 ? null : new ConstFact(new IrConst.IntConst(Int32Semantics.modulo(left, right), IrPrimitiveType.INT32));
      case SHL -> new ConstFact(new IrConst.IntConst(left << right, IrPrimitiveType.INT32));
      case SHR -> new ConstFact(new IrConst.IntConst(left >> right, IrPrimitiveType.INT32));
      case AND -> new ConstFact(new IrConst.IntConst(left & right, IrPrimitiveType.INT32));
      case OR -> new ConstFact(new IrConst.IntConst(left | right, IrPrimitiveType.INT32));
      case XOR -> new ConstFact(new IrConst.IntConst(left ^ right, IrPrimitiveType.INT32));
    };
  }

  private static boolean compare(IrRhs.CmpOpName op, int left, int right) {
    return switch (op) {
      case EQ -> left == right;
      case NE -> left != right;
      case LT -> left < right;
      case LE -> left <= right;
      case GT -> left > right;
      case GE -> left >= right;
    };
  }

  private static IrConst.IntConst boolConst(boolean value) {
    return new IrConst.IntConst(value ? 1 : 0, IrPrimitiveType.BOOL);
  }

  private ValueFact valueFactOf(IrValue value, Map<Integer, ValueFact> tempFacts) {
    if (value instanceof IrConst constant) {
      return new ConstFact(constant);
    }
    if (value instanceof IrTemp temp) {
      return tempFacts.get(temp.index());
    }
    return null;
  }

  private Integer intValue(ValueFact valueFact) {
    if (!(valueFact instanceof ConstFact constFact)) {
      return null;
    }
    if (constFact.constant() instanceof IrConst.IntConst intConst
        && intConst.type() == IrPrimitiveType.INT32) {
      return intConst.value();
    }
    return null;
  }

  private IrConst resolveConstForSlot(
      String slot,
      Map<String, ValueFact> slotState,
      IrType expectedType,
      Set<String> visited) {
    if (!visited.add(slot)) {
      return null;
    }

    ValueFact fact = slotState.get(slot);
    if (fact instanceof ConstFact constFact) {
      IrConst coerced = coerceConstant(constFact.constant(), expectedType);
      return coerced;
    }

    if (fact instanceof CopyFact copyFact) {
      return resolveConstForSlot(copyFact.slot(), slotState, expectedType, visited);
    }

    return null;
  }

  private IrConst coerceConstant(IrConst constant, IrType expectedType) {
    if (expectedType == null) {
      return constant;
    }
    if (constant.type().equals(expectedType)) {
      return constant;
    }
    if (!(expectedType instanceof IrPrimitiveType expectedPrimitive)) {
      return null;
    }

    if (constant instanceof IrConst.IntConst intConst) {
      int value = intConst.value();
      return switch (expectedPrimitive) {
        case INT32 -> new IrConst.IntConst(value, IrPrimitiveType.INT32);
        case BOOL -> new IrConst.IntConst(value == 0 ? 0 : 1, IrPrimitiveType.BOOL);
        case CHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.CHAR);
        case UCHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.UCHAR);
        case FLOAT -> null;
      };
    }

    if (constant instanceof IrConst.CharConst charConst) {
      int value = charConst.value();
      return switch (expectedPrimitive) {
        case INT32 -> new IrConst.IntConst(value, IrPrimitiveType.INT32);
        case BOOL -> new IrConst.IntConst(value == 0 ? 0 : 1, IrPrimitiveType.BOOL);
        case CHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.CHAR);
        case UCHAR -> new IrConst.IntConst(value & 0xFF, IrPrimitiveType.UCHAR);
        case FLOAT -> null;
      };
    }

    if (constant instanceof IrConst.FloatConst && expectedPrimitive == IrPrimitiveType.FLOAT) {
      return constant;
    }

    return null;
  }

  private ValueFact normalizeForSlot(
      String destinationSlot,
      ValueFact source,
      Map<String, IrType> trackedSlots,
      Map<String, ValueFact> slotState) {
    if (source == null) {
      return null;
    }

    IrType destinationType = trackedSlots.get(destinationSlot);
    if (destinationType == null) {
      return null;
    }

    if (source instanceof ConstFact constFact) {
      IrConst coerced = coerceConstant(constFact.constant(), destinationType);
      return coerced == null ? null : new ConstFact(coerced);
    }

    if (source instanceof CopyFact copyFact) {
      IrType sourceType = trackedSlots.get(copyFact.slot());
      if (sourceType == null || !sourceType.equals(destinationType)) {
        return null;
      }
      if (copyFact.slot().equals(destinationSlot)) {
        return slotState.get(destinationSlot);
      }
      return copyFact;
    }

    return null;
  }

  private boolean hasUnknownMemoryWrite(IrRhs rhs) {
    return rhs instanceof IrRhs.Call || rhs instanceof IrRhs.IncDecOp;
  }

  private boolean isTrackedSlot(IrSymbolRef symbolRef, Map<String, IrType> trackedSlots) {
    return (symbolRef.kind() == IrSymbolRef.Kind.LOCAL || symbolRef.kind() == IrSymbolRef.Kind.PARAM)
        && trackedSlots.containsKey(symbolRef.name());
  }

  private IrSymbolRef resolveAddress(IrValue value, Map<Integer, IrSymbolRef> tempAddresses) {
    if (value instanceof IrTemp temp) {
      return tempAddresses.get(temp.index());
    }
    return null;
  }

  private Map<String, IrType> trackedSlots(IrFunction function) {
    Map<String, IrType> tracked = new LinkedHashMap<>();
    for (IrSlot slot : function.slots()) {
      if ((slot.kind() == IrSlot.Kind.LOCAL || slot.kind() == IrSlot.Kind.PARAM)
          && slot.type() instanceof IrPrimitiveType) {
        tracked.put(slot.name(), slot.type());
      }
    }
    return tracked;
  }

  private Map<String, ValueFact> mergePredecessors(
      String label,
      Map<String, List<String>> predecessors,
      Map<String, Map<String, ValueFact>> outStates) {

    List<String> preds = predecessors.getOrDefault(label, List.of());
    if (preds.isEmpty()) {
      return Map.of();
    }

    Map<String, ValueFact> merged = new HashMap<>(outStates.getOrDefault(preds.getFirst(), Map.of()));
    for (int i = 1; i < preds.size(); i++) {
      Map<String, ValueFact> other = outStates.getOrDefault(preds.get(i), Map.of());
      merged.entrySet().removeIf(entry -> !entry.getValue().equals(other.get(entry.getKey())));
    }

    return Map.copyOf(merged);
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }

  private record TransferResult(IrBlock block, Map<String, ValueFact> outState, boolean changed) {
  }

  private sealed interface ValueFact permits ConstFact, CopyFact {
  }

  private record ConstFact(IrConst constant) implements ValueFact {
  }

  private record CopyFact(String slot) implements ValueFact {
  }

  private record FunctionGraph(Map<String, List<String>> predecessors) {
    static FunctionGraph of(List<IrBlock> blocks) {
      Map<String, List<String>> preds = new HashMap<>();
      for (IrBlock block : blocks) {
        preds.computeIfAbsent(block.label(), ignored -> new ArrayList<>());
      }

      for (IrBlock block : blocks) {
        for (String successor : successors(block.terminator())) {
          preds.computeIfAbsent(successor, ignored -> new ArrayList<>()).add(block.label());
        }
      }

      Map<String, List<String>> immutable = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : preds.entrySet()) {
        immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      return new FunctionGraph(Map.copyOf(immutable));
    }

    private static List<String> successors(IrTerminator terminator) {
      return switch (terminator) {
        case IrTerminator.IrJmpTerm jmp -> List.of(jmp.label());
        case IrTerminator.IrBrTerm br -> List.of(br.trueLabel(), br.falseLabel());
        case IrTerminator.IrRetTerm ignored -> List.of();
      };
    }
  }
}

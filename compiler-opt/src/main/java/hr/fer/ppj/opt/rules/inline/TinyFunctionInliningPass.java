package hr.fer.ppj.opt.rules.inline;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
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
 * Inlines tiny pure leaf int32 functions into direct call sites.
 */
public final class TinyFunctionInliningPass implements IrPass {

  private static final int MAX_INLINE_ASSIGNMENTS = 8;

  @Override
  public String name() {
    return "tiny-function-inlining";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    Map<String, InlineCandidate> candidates = collectCandidates(program.functions());
    if (candidates.isEmpty()) {
      return PassResult.unchanged(program);
    }

    boolean changed = false;
    List<IrFunction> rewrittenFunctions = new ArrayList<>(program.functions().size());
    for (IrFunction function : program.functions()) {
      FunctionResult result = rewriteFunction(function, candidates);
      rewrittenFunctions.add(result.function());
      changed |= result.changed();
    }

    if (!changed) {
      return PassResult.unchanged(program);
    }
    return PassResult.changed(new IrProgram(program.globals(), program.structDefs(), rewrittenFunctions));
  }

  private Map<String, InlineCandidate> collectCandidates(List<IrFunction> functions) {
    Map<String, InlineCandidate> result = new LinkedHashMap<>();
    for (IrFunction function : functions) {
      InlineCandidate candidate = candidateOf(function);
      if (candidate != null) {
        result.put(function.name(), candidate);
      }
    }
    return result;
  }

  private InlineCandidate candidateOf(IrFunction function) {
    if (function.returnType() != IrPrimitiveType.INT32 || function.blocks().size() != 1) {
      return null;
    }
    if (function.localsBytes() != 0 || hasLocalOrSpillSlots(function.slots())) {
      return null;
    }

    IrBlock block = function.blocks().getFirst();
    if (!(block.terminator() instanceof IrTerminator.IrRetTerm ret) || ret.value() == null) {
      return null;
    }
    if (block.instructions().size() > MAX_INLINE_ASSIGNMENTS) {
      return null;
    }

    Set<String> parameters = function.parameters().stream().map(IrFunction.Parameter::name).collect(java.util.stream.Collectors.toSet());
    List<IrInstruction.IrAssignInstr> assigns = new ArrayList<>(block.instructions().size());
    for (IrInstruction instruction : block.instructions()) {
      if (!(instruction instanceof IrInstruction.IrAssignInstr assign) || !isSupported(assign.rhs(), parameters)) {
        return null;
      }
      assigns.add(assign);
    }

    return new InlineCandidate(function.name(), function.parameters().stream().map(IrFunction.Parameter::name).toList(), assigns, ret.value());
  }

  private boolean hasLocalOrSpillSlots(List<IrSlot> slots) {
    for (IrSlot slot : slots) {
      if (slot.kind() == IrSlot.Kind.LOCAL || slot.kind() == IrSlot.Kind.SPILL) {
        return true;
      }
    }
    return false;
  }

  private boolean isSupported(IrRhs rhs, Set<String> parameters) {
    return switch (rhs) {
      case IrRhs.AddrOfSymbol addr -> addr.symbolRef().kind() == IrSymbolRef.Kind.PARAM
          && parameters.contains(addr.symbolRef().name());
      case IrRhs.Load load -> load.loadType() == IrPrimitiveType.INT32;
      case IrRhs.BinOp bin -> bin.resultType() == IrPrimitiveType.INT32;
      case IrRhs.CmpOp ignored -> true;
      case IrRhs.UnaryOp unary -> unary.resultType() == IrPrimitiveType.INT32;
      case IrRhs.CastOp cast -> cast.resultType() == IrPrimitiveType.INT32;
      case IrRhs.ConstRhs constRhs -> asInt32Const(constRhs.constant()) != null;
      default -> false;
    };
  }

  private FunctionResult rewriteFunction(IrFunction function, Map<String, InlineCandidate> candidates) {
    int nextTemp = maxTemp(function) + 1;
    boolean changed = false;
    List<IrBlock> rewrittenBlocks = new ArrayList<>(function.blocks().size());

    for (IrBlock block : function.blocks()) {
      List<IrInstruction> rewrittenInstructions = new ArrayList<>();
      for (IrInstruction instruction : block.instructions()) {
        if (instruction instanceof IrInstruction.IrAssignInstr assign
            && assign.rhs() instanceof IrRhs.Call call
            && !function.name().equals(call.funcName())) {

          InlineCandidate candidate = candidates.get(call.funcName());
          if (candidate != null && call.args().size() == candidate.parameterNames().size()) {
            Expansion expansion = inlineCall(assign.dest(), call.args(), candidate, nextTemp);
            if (expansion.success()) {
              rewrittenInstructions.addAll(expansion.instructions());
              nextTemp = expansion.nextTemp();
              changed = true;
              continue;
            }
          }
        }
        rewrittenInstructions.add(instruction);
      }
      rewrittenBlocks.add(new IrBlock(block.label(), rewrittenInstructions, block.terminator()));
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

  private Expansion inlineCall(
      IrTemp destination,
      List<IrValue> args,
      InlineCandidate candidate,
      int startTemp) {

    Map<String, IrValue> parameterValues = new HashMap<>();
    for (int i = 0; i < candidate.parameterNames().size(); i++) {
      parameterValues.put(candidate.parameterNames().get(i), args.get(i));
    }

    int nextTemp = startTemp;
    List<IrInstruction> emitted = new ArrayList<>();
    Map<Integer, String> paramAddrTemps = new HashMap<>();
    Map<Integer, IrValue> valueTemps = new HashMap<>();

    for (IrInstruction.IrAssignInstr assign : candidate.assignments()) {
      boolean returnsThisTemp = candidate.returnValue() instanceof IrTemp rt && rt.index() == assign.dest().index();
      IrRhs rhs = assign.rhs();

      if (rhs instanceof IrRhs.AddrOfSymbol addr) {
        paramAddrTemps.put(assign.dest().index(), addr.symbolRef().name());
        continue;
      }

      if (rhs instanceof IrRhs.Load load) {
        if (!(load.addr() instanceof IrTemp addrTemp)) {
          return Expansion.failure(startTemp);
        }
        String paramName = paramAddrTemps.get(addrTemp.index());
        IrValue mapped = paramName == null ? null : parameterValues.get(paramName);
        if (mapped == null) {
          return Expansion.failure(startTemp);
        }
        if (returnsThisTemp) {
          IrInstruction copy = copyAssign(destination, mapped);
          if (copy != null) {
            emitted.add(copy);
          }
        } else {
          valueTemps.put(assign.dest().index(), mapped);
        }
        continue;
      }

      if (rhs instanceof IrRhs.ConstRhs constRhs) {
        IrConst.IntConst asInt = asInt32Const(constRhs.constant());
        if (asInt == null) {
          return Expansion.failure(startTemp);
        }
        if (returnsThisTemp) {
          emitted.add(new IrInstruction.IrAssignInstr(destination, new IrRhs.ConstRhs(asInt)));
        } else {
          valueTemps.put(assign.dest().index(), asInt);
        }
        continue;
      }

      IrRhs rewritten = rewritePureRhs(rhs, valueTemps);
      if (rewritten == null) {
        return Expansion.failure(startTemp);
      }

      IrTemp out = returnsThisTemp ? destination : new IrTemp(nextTemp++, assign.dest().type());
      emitted.add(new IrInstruction.IrAssignInstr(out, rewritten));
      valueTemps.put(assign.dest().index(), out);
    }

    if (candidate.returnValue() instanceof IrConst constant) {
      IrConst.IntConst asInt = asInt32Const(constant);
      if (asInt == null) {
        return Expansion.failure(startTemp);
      }
      emitted.add(new IrInstruction.IrAssignInstr(destination, new IrRhs.ConstRhs(asInt)));
      return new Expansion(true, emitted, nextTemp);
    }

    if (!(candidate.returnValue() instanceof IrTemp returnTemp)) {
      return Expansion.failure(startTemp);
    }

    IrValue returnValue = valueTemps.get(returnTemp.index());
    if (returnValue == null) {
      return Expansion.failure(startTemp);
    }

    IrInstruction copy = copyAssign(destination, returnValue);
    if (copy != null) {
      emitted.add(copy);
    }

    return new Expansion(true, emitted, nextTemp);
  }

  private IrRhs rewritePureRhs(IrRhs rhs, Map<Integer, IrValue> valueTemps) {
    return switch (rhs) {
      case IrRhs.BinOp bin -> {
        IrValue left = resolveValue(bin.left(), valueTemps);
        IrValue right = resolveValue(bin.right(), valueTemps);
        yield left == null || right == null ? null : new IrRhs.BinOp(bin.op(), left, right, bin.resultType());
      }
      case IrRhs.CmpOp cmp -> {
        IrValue left = resolveValue(cmp.left(), valueTemps);
        IrValue right = resolveValue(cmp.right(), valueTemps);
        yield left == null || right == null ? null : new IrRhs.CmpOp(cmp.op(), left, right);
      }
      case IrRhs.UnaryOp unary -> {
        IrValue operand = resolveValue(unary.operand(), valueTemps);
        yield operand == null ? null : new IrRhs.UnaryOp(unary.op(), operand, unary.resultType());
      }
      case IrRhs.CastOp cast -> {
        IrValue operand = resolveValue(cast.operand(), valueTemps);
        yield operand == null ? null : new IrRhs.CastOp(cast.op(), operand, cast.resultType());
      }
      default -> null;
    };
  }

  private IrValue resolveValue(IrValue value, Map<Integer, IrValue> valueTemps) {
    if (value instanceof IrConst constant) {
      return asInt32Const(constant);
    }
    if (value instanceof IrTemp temp) {
      return valueTemps.get(temp.index());
    }
    return null;
  }

  private IrConst.IntConst asInt32Const(IrConst constant) {
    if (constant instanceof IrConst.IntConst intConst) {
      if (intConst.type() == IrPrimitiveType.INT32) {
        return intConst;
      }
      return new IrConst.IntConst(intConst.value(), IrPrimitiveType.INT32);
    }
    if (constant instanceof IrConst.CharConst charConst) {
      return new IrConst.IntConst(charConst.value() & 0xFF, IrPrimitiveType.INT32);
    }
    return null;
  }

  private IrInstruction copyAssign(IrTemp destination, IrValue value) {
    if (value instanceof IrConst constant) {
      IrConst.IntConst asInt = asInt32Const(constant);
      return asInt == null ? null : new IrInstruction.IrAssignInstr(destination, new IrRhs.ConstRhs(asInt));
    }
    if (value instanceof IrTemp source) {
      if (source.index() == destination.index()) {
        return null;
      }
      return new IrInstruction.IrAssignInstr(
          destination,
          new IrRhs.BinOp(
              IrRhs.BinOpName.ADD,
              source,
              new IrConst.IntConst(0, IrPrimitiveType.INT32),
              IrPrimitiveType.INT32));
    }
    return null;
  }

  private int maxTemp(IrFunction function) {
    int max = -1;
    for (IrBlock block : function.blocks()) {
      for (IrInstruction instruction : block.instructions()) {
        if (instruction instanceof IrInstruction.IrAssignInstr assign) {
          max = Math.max(max, assign.dest().index());
        }
      }
    }
    return max;
  }

  private record InlineCandidate(
      String name,
      List<String> parameterNames,
      List<IrInstruction.IrAssignInstr> assignments,
      IrValue returnValue) {
  }

  private record Expansion(boolean success, List<IrInstruction> instructions, int nextTemp) {
    static Expansion failure(int nextTemp) {
      return new Expansion(false, List.of(), nextTemp);
    }
  }

  private record FunctionResult(IrFunction function, boolean changed) {
  }
}

package hr.fer.ppj.opt.rules.cast;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
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
 * Simplifies redundant casts by replacing them with aliases when type-stable.
 */
public final class CastSimplificationPass implements IrPass {

  @Override
  public String name() {
    return "cast-simplification";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());

      for (IrBlock block : function.blocks()) {
        BlockResult result = simplifyBlock(block);
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

  private BlockResult simplifyBlock(IrBlock block) {
    boolean changed = false;
    Map<Integer, IrValue> aliases = new HashMap<>();
    List<IrInstruction> rewrittenInstructions = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      IrInstruction rewritten = rewriteInstruction(instruction, aliases);

      if (rewritten instanceof IrInstruction.IrAssignInstr assign
          && assign.rhs() instanceof IrRhs.CastOp castOp
          && isRedundantCast(castOp)) {
        aliases.put(assign.dest().index(), resolveAlias(castOp.operand(), aliases));
        invalidateAliasesPointingTo(assign.dest().index(), aliases);
        changed = true;
        continue;
      }

      if (rewritten instanceof IrInstruction.IrAssignInstr assign) {
        aliases.remove(assign.dest().index());
        invalidateAliasesPointingTo(assign.dest().index(), aliases);
      }

      rewrittenInstructions.add(rewritten);
      changed |= !rewritten.equals(instruction);
    }

    IrTerminator rewrittenTerminator = rewriteTerminator(block.terminator(), aliases);
    changed |= !rewrittenTerminator.equals(block.terminator());

    if (!changed) {
      return new BlockResult(block, false);
    }

    return new BlockResult(new IrBlock(block.label(), rewrittenInstructions, rewrittenTerminator), true);
  }

  private IrInstruction rewriteInstruction(IrInstruction instruction, Map<Integer, IrValue> aliases) {
    return switch (instruction) {
      case IrInstruction.IrAssignInstr assign ->
          new IrInstruction.IrAssignInstr(assign.dest(), rewriteRhs(assign.rhs(), aliases));
      case IrInstruction.IrStoreInstr store ->
          new IrInstruction.IrStoreInstr(
              resolveAlias(store.addr(), aliases),
              resolveAlias(store.value(), aliases),
              store.storeType());
      case IrInstruction.IrVoidCallInstr call -> {
        List<IrValue> rewrittenArgs = new ArrayList<>(call.args().size());
        for (IrValue arg : call.args()) {
          rewrittenArgs.add(resolveAlias(arg, aliases));
        }
        yield new IrInstruction.IrVoidCallInstr(call.funcName(), rewrittenArgs);
      }
    };
  }

  private IrTerminator rewriteTerminator(IrTerminator terminator, Map<Integer, IrValue> aliases) {
    return switch (terminator) {
      case IrTerminator.IrBrTerm br ->
          new IrTerminator.IrBrTerm(resolveAlias(br.condition(), aliases), br.trueLabel(), br.falseLabel());
      case IrTerminator.IrRetTerm ret ->
          new IrTerminator.IrRetTerm(ret.value() == null ? null : resolveAlias(ret.value(), aliases));
      case IrTerminator.IrJmpTerm jmp -> jmp;
    };
  }

  private IrRhs rewriteRhs(IrRhs rhs, Map<Integer, IrValue> aliases) {
    return switch (rhs) {
      case IrRhs.AddrOfSymbol addr -> addr;
      case IrRhs.ConstRhs constant -> constant;
      case IrRhs.AddrIndex addrIndex ->
          new IrRhs.AddrIndex(
              resolveAlias(addrIndex.base(), aliases),
              resolveAlias(addrIndex.idx(), aliases),
              addrIndex.elemSize(),
              addrIndex.resultType());
      case IrRhs.AddrField addrField ->
          new IrRhs.AddrField(
              resolveAlias(addrField.base(), aliases),
              addrField.structName(),
              addrField.fieldName(),
              addrField.resultType());
      case IrRhs.Load load -> new IrRhs.Load(resolveAlias(load.addr(), aliases), load.loadType());
      case IrRhs.BinOp binOp ->
          new IrRhs.BinOp(
              binOp.op(),
              resolveAlias(binOp.left(), aliases),
              resolveAlias(binOp.right(), aliases),
              binOp.resultType());
      case IrRhs.CmpOp cmpOp ->
          new IrRhs.CmpOp(cmpOp.op(), resolveAlias(cmpOp.left(), aliases), resolveAlias(cmpOp.right(), aliases));
      case IrRhs.Call call -> {
        List<IrValue> rewrittenArgs = new ArrayList<>(call.args().size());
        for (IrValue arg : call.args()) {
          rewrittenArgs.add(resolveAlias(arg, aliases));
        }
        yield new IrRhs.Call(call.funcName(), rewrittenArgs, call.resultType());
      }
      case IrRhs.UnaryOp unaryOp ->
          new IrRhs.UnaryOp(unaryOp.op(), resolveAlias(unaryOp.operand(), aliases), unaryOp.resultType());
      case IrRhs.IncDecOp incDecOp ->
          new IrRhs.IncDecOp(incDecOp.op(), resolveAlias(incDecOp.addr(), aliases), incDecOp.resultType());
      case IrRhs.CastOp castOp ->
          new IrRhs.CastOp(castOp.op(), resolveAlias(castOp.operand(), aliases), castOp.resultType());
    };
  }

  private boolean isRedundantCast(IrRhs.CastOp castOp) {
    return castOp.operand().type().equals(castOp.resultType());
  }

  private IrValue resolveAlias(IrValue value, Map<Integer, IrValue> aliases) {
    if (!(value instanceof IrTemp temp)) {
      return value;
    }

    IrValue current = value;
    Set<Integer> seen = new HashSet<>();

    while (current instanceof IrTemp currentTemp) {
      if (!seen.add(currentTemp.index())) {
        break;
      }
      IrValue replacement = aliases.get(currentTemp.index());
      if (replacement == null) {
        break;
      }
      current = replacement;
    }

    return current;
  }

  private void invalidateAliasesPointingTo(int tempIndex, Map<Integer, IrValue> aliases) {
    aliases.entrySet().removeIf(entry -> entry.getValue() instanceof IrTemp t && t.index() == tempIndex);
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }
}

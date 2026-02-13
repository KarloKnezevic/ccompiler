package hr.fer.ppj.opt.rules.temps;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
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
import java.util.Objects;
import java.util.Set;

/**
 * Local common subexpression elimination for side-effect-free RHS expressions.
 */
public final class CommonSubexpressionEliminationPass implements IrPass {

  @Override
  public String name() {
    return "local-cse";
  }

  @Override
  public PassResult run(IrProgram program, PassContext context) {
    boolean changed = false;
    List<IrFunction> functions = new ArrayList<>(program.functions().size());

    for (IrFunction function : program.functions()) {
      boolean functionChanged = false;
      List<IrBlock> blocks = new ArrayList<>(function.blocks().size());

      for (IrBlock block : function.blocks()) {
        BlockResult result = rewriteBlock(block);
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

  private BlockResult rewriteBlock(IrBlock block) {
    boolean changed = false;
    Map<Integer, IrValue> aliases = new HashMap<>();
    Map<ExpressionKey, Integer> expressionToTemp = new HashMap<>();
    Map<Integer, ExpressionKey> tempToExpression = new HashMap<>();
    List<IrInstruction> rewrittenInstructions = new ArrayList<>(block.instructions().size());

    for (IrInstruction instruction : block.instructions()) {
      IrInstruction rewritten = IrValueRewriter.rewriteInstruction(instruction, value -> resolveAlias(value, aliases));

      if (rewritten instanceof IrInstruction.IrAssignInstr assign) {
        int dest = assign.dest().index();
        killDest(dest, aliases, expressionToTemp, tempToExpression);

        ExpressionKey key = keyOf(assign.rhs());
        if (key != null) {
          Integer existing = expressionToTemp.get(key);
          if (existing != null && existing != dest) {
            aliases.put(dest, new IrTemp(existing, assign.dest().type()));
            changed = true;
            continue;
          }

          expressionToTemp.put(key, dest);
          tempToExpression.put(dest, key);
        }

        rewrittenInstructions.add(rewritten);
        changed |= !rewritten.equals(instruction);
        continue;
      }

      rewrittenInstructions.add(rewritten);
      changed |= !rewritten.equals(instruction);
    }

    IrTerminator rewrittenTerminator = IrValueRewriter.rewriteTerminator(
        block.terminator(), value -> resolveAlias(value, aliases));
    changed |= !rewrittenTerminator.equals(block.terminator());

    if (!changed) {
      return new BlockResult(block, false);
    }

    return new BlockResult(new IrBlock(block.label(), rewrittenInstructions, rewrittenTerminator), true);
  }

  private void killDest(
      int dest,
      Map<Integer, IrValue> aliases,
      Map<ExpressionKey, Integer> expressionToTemp,
      Map<Integer, ExpressionKey> tempToExpression) {

    aliases.remove(dest);
    aliases.entrySet().removeIf(entry -> entry.getValue() instanceof IrTemp alias && alias.index() == dest);

    ExpressionKey oldKey = tempToExpression.remove(dest);
    if (oldKey != null && Objects.equals(expressionToTemp.get(oldKey), dest)) {
      expressionToTemp.remove(oldKey);
    }
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

  private ExpressionKey keyOf(IrRhs rhs) {
    if (!IrUsageAnalyzer.isPure(rhs)) {
      return null;
    }

    return switch (rhs) {
      case IrRhs.ConstRhs ignored -> null;
      case IrRhs.Load ignored -> null;
      case IrRhs.Call ignored -> null;
      case IrRhs.IncDecOp ignored -> null;
      case IrRhs.AddrOfSymbol addr -> new AddrOfSymbolKey(addr.symbolRef().kind(), addr.symbolRef().name());
      case IrRhs.AddrIndex addrIndex -> new AddrIndexKey(addrIndex.base(), addrIndex.idx(), addrIndex.elemSize(), addrIndex.resultType().toIrString());
      case IrRhs.AddrField addrField -> new AddrFieldKey(addrField.base(), addrField.structName(), addrField.fieldName(), addrField.resultType().toIrString());
      case IrRhs.BinOp binOp -> createBinKey(binOp);
      case IrRhs.CmpOp cmpOp -> createCmpKey(cmpOp);
      case IrRhs.UnaryOp unaryOp -> new UnaryKey(unaryOp.op(), unaryOp.operand(), unaryOp.resultType().toIrString());
      case IrRhs.CastOp castOp -> new CastKey(castOp.op(), castOp.operand(), castOp.resultType().toIrString());
    };
  }

  private ExpressionKey createBinKey(IrRhs.BinOp binOp) {
    if (isCommutative(binOp.op())) {
      OrderedValues ordered = order(binOp.left(), binOp.right());
      return new BinKey(binOp.op(), ordered.first(), ordered.second(), binOp.resultType().toIrString());
    }
    return new BinKey(binOp.op(), binOp.left(), binOp.right(), binOp.resultType().toIrString());
  }

  private ExpressionKey createCmpKey(IrRhs.CmpOp cmpOp) {
    if (cmpOp.op() == IrRhs.CmpOpName.EQ || cmpOp.op() == IrRhs.CmpOpName.NE) {
      OrderedValues ordered = order(cmpOp.left(), cmpOp.right());
      return new CmpKey(cmpOp.op(), ordered.first(), ordered.second());
    }
    return new CmpKey(cmpOp.op(), cmpOp.left(), cmpOp.right());
  }

  private static OrderedValues order(IrValue left, IrValue right) {
    String leftKey = sortKey(left);
    String rightKey = sortKey(right);
    if (leftKey.compareTo(rightKey) <= 0) {
      return new OrderedValues(left, right);
    }
    return new OrderedValues(right, left);
  }

  private static String sortKey(IrValue value) {
    if (value instanceof IrTemp temp) {
      return "T" + temp.index() + ":" + temp.type().toIrString();
    }
    if (value instanceof IrConst constant) {
      return "C" + constant.toIrString();
    }
    return value.toString();
  }

  private static boolean isCommutative(IrRhs.BinOpName op) {
    return op == IrRhs.BinOpName.ADD
        || op == IrRhs.BinOpName.MUL
        || op == IrRhs.BinOpName.AND
        || op == IrRhs.BinOpName.OR
        || op == IrRhs.BinOpName.XOR;
  }

  private record BlockResult(IrBlock block, boolean changed) {
  }

  private record OrderedValues(IrValue first, IrValue second) {
  }

  private sealed interface ExpressionKey permits
      AddrOfSymbolKey,
      AddrIndexKey,
      AddrFieldKey,
      BinKey,
      CmpKey,
      UnaryKey,
      CastKey {
  }

  private record AddrOfSymbolKey(hr.fer.ppj.ir.model.IrSymbolRef.Kind kind, String name) implements ExpressionKey {
  }

  private record AddrIndexKey(IrValue base, IrValue index, int elemSize, String resultType) implements ExpressionKey {
  }

  private record AddrFieldKey(IrValue base, String structName, String fieldName, String resultType)
      implements ExpressionKey {
  }

  private record BinKey(IrRhs.BinOpName op, IrValue left, IrValue right, String resultType) implements ExpressionKey {
  }

  private record CmpKey(IrRhs.CmpOpName op, IrValue left, IrValue right) implements ExpressionKey {
  }

  private record UnaryKey(IrRhs.UnaryOpName op, IrValue operand, String resultType) implements ExpressionKey {
  }

  private record CastKey(IrRhs.CastName op, IrValue operand, String resultType) implements ExpressionKey {
  }
}

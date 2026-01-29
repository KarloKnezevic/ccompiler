package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Handles deferred postfix increment/decrement operations in binary expressions.
 *
 * <p>In expressions like {@code x + y++}, the value of {@code y} must be loaded first,
 * then the addition performed, and finally the increment applied. This class manages
 * that deferred operation lifecycle.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class PostfixIncrementHandler {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;

  public PostfixIncrementHandler(ExpressionEmitter emitter, LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.lValueEmitter = lValueEmitter;
  }

  /**
   * Loads the value for a postfix increment/decrement expression without performing the increment.
   *
   * @param node the expression node that contains a postfix increment
   * @param ctx the function context
   * @return the loaded value (before increment)
   */
  public IrValue loadValueForPostfixIncrement(NonTerminalNode node, FunctionContext ctx) {
    NonTerminalNode postfixNode = ExpressionAnalyzer.findPostfixIncrementNode(node);
    if (postfixNode == null) {
      return emitter.emitRValue(node, ctx);
    }

    List<ParseNode> children = postfixNode.children();
    if (children.size() != 2) {
      return emitter.emitRValue(node, ctx);
    }

    ParseNode second = children.get(1);
    if (!(second instanceof TerminalNode term)
        || (!term.symbol().equals("OP_INC") && !term.symbol().equals("OP_DEC"))) {
      return emitter.emitRValue(node, ctx);
    }

    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");

    if (lValueEmitter == null) {
      return emitter.emitRValue(node, ctx);
    }

    IrFunctionBuilder builder = ctx.functionBuilder();
    Type exprType = baseNode.attributes().type();
    IrType irType = TypeMapper.toIrType(exprType);

    IrTemp addr = lValueEmitter.emitLValue(baseNode, ctx);
    IrRhs.Load load = new IrRhs.Load(addr, irType);
    IrTemp value = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(value, load));

    String varName = ExpressionNameExtractor.extractVariableName(baseNode);
    if (varName != null) {
      ctx.setDeferredPostfixIncrementValue(varName, value, addr);
    }

    return value;
  }

  /**
   * Performs the deferred increment/decrement for a postfix expression.
   *
   * @param node the expression node that contains a postfix increment
   * @param ctx the function context
   */
  public void performPostfixIncrement(NonTerminalNode node, FunctionContext ctx) {
    NonTerminalNode postfixNode = ExpressionAnalyzer.findPostfixIncrementNode(node);
    if (postfixNode == null) {
      return;
    }

    List<ParseNode> children = postfixNode.children();
    if (children.size() != 2) {
      return;
    }

    ParseNode second = children.get(1);
    if (!(second instanceof TerminalNode term)
        || (!term.symbol().equals("OP_INC") && !term.symbol().equals("OP_DEC"))) {
      return;
    }

    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");

    if (lValueEmitter == null) {
      return;
    }

    IrFunctionBuilder builder = ctx.functionBuilder();
    Type exprType = baseNode.attributes().type();
    IrType irType = TypeMapper.toIrType(exprType);

    String varName = ExpressionNameExtractor.extractVariableName(baseNode);
    IrTemp currentValue = null;
    IrTemp addr = null;
    if (varName != null) {
      currentValue = ctx.getDeferredPostfixIncrementValue(varName);
      addr = ctx.getDeferredPostfixIncrementAddr(varName);
    }

    if (currentValue == null || addr == null) {
      if (addr == null) {
        addr = lValueEmitter.emitLValue(baseNode, ctx);
      }
      if (currentValue == null) {
        IrRhs.Load load = new IrRhs.Load(addr, irType);
        currentValue = builder.tempFactory().newTemp(irType);
        builder.addInstruction(new IrInstruction.IrAssignInstr(currentValue, load));
      }
    }

    if (varName != null) {
      ctx.clearDeferredPostfixIncrementValue();
    }

    IrConst oneConst = term.symbol().equals("OP_INC")
        ? new IrConst.IntConst(1, irType)
        : new IrConst.IntConst(-1, irType);
    IrRhs.BinOp addOp = new IrRhs.BinOp(
        IrRhs.BinOp.BinOpName.ADD, currentValue, oneConst, irType);
    IrTemp newValue = builder.tempFactory().newTemp(irType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(newValue, addOp));

    builder.addInstruction(new IrInstruction.IrStoreInstr(addr, newValue, irType));

    if (varName != null) {
      ctx.addressReuseContext().clearLastLoadedValue(varName);
    }
  }
}

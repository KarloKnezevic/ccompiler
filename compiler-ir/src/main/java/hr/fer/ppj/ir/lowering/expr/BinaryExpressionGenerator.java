package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.OperatorMapper;
import hr.fer.ppj.ir.util.TypePromoter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for binary expressions (multiplicative and additive).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BinaryExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final PostfixIncrementHandler postfixHandler;

  public BinaryExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    LValueEmitter lValueEmitter = (emitter instanceof LValueEmitter lv) ? lv : null;
    this.postfixHandler = new PostfixIncrementHandler(emitter, lValueEmitter);
  }

  /**
   * Emits r-value for a multiplicative expression (*, /, %).
   */
  public IrValue emitMultiplicative(NonTerminalNode node, FunctionContext ctx) {
    return emitBinary(node, ctx,
        new String[] {"OP_PUTA", "OP_DIJELI", "OP_MOD"},
        new IrRhs.BinOp.BinOpName[] {
            IrRhs.BinOp.BinOpName.MUL, IrRhs.BinOp.BinOpName.DIV, IrRhs.BinOp.BinOpName.MOD
        });
  }

  /**
   * Emits r-value for an additive expression (+, -).
   */
  public IrValue emitAdditive(NonTerminalNode node, FunctionContext ctx) {
    return emitBinary(node, ctx,
        new String[] {"PLUS", "MINUS"},
        new IrRhs.BinOp.BinOpName[] {IrRhs.BinOp.BinOpName.ADD, IrRhs.BinOp.BinOpName.SUB});
  }

  private IrValue emitBinary(NonTerminalNode node, FunctionContext ctx,
      String[] opSymbols, IrRhs.BinOp.BinOpName[] opNames) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      if (children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, ctx);
      }
      throw new IllegalArgumentException("Invalid binary expression: single child is not a non-terminal");
    }

    if (children.size() != 3) {
      throw new IllegalStateException("Binary expression must have 1 or 3 children, but has "
          + children.size() + ": " + node.symbol());
    }

    TerminalNode opTerm = extractOperator(children.get(1), node.symbol());
    String opSymbol = opTerm.symbol();

    NonTerminalNode leftNode = extractOperand(children.get(0), "left");
    NonTerminalNode rightNode = extractOperand(children.get(2), "right");

    IrType irResultType = determineResultType(leftNode, rightNode);
    IrType leftIrType = TypeMapper.toIrType(leftNode.attributes().type());
    IrType rightIrType = TypeMapper.toIrType(rightNode.attributes().type());

    IrFunctionBuilder builder = ctx.functionBuilder();
    IrValue left, right;

    if (shouldEvaluateRightFirst(leftNode, rightNode)) {
      right = evaluateOperand(rightNode, rightIrType, irResultType, ctx, builder);
      ctx.addressReuseContext().clearAllLastLoadedValues();
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
    } else if (shouldEvaluateRightFirstForArrayIndex(leftNode, rightNode)) {
      right = evaluateOperand(rightNode, rightIrType, irResultType, ctx, builder);
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
    } else {
      left = evaluateOperand(leftNode, leftIrType, irResultType, ctx, builder);
      if (opSymbol.equals("PLUS") || opSymbol.equals("MINUS")) {
        ctx.addressReuseContext().clearAllLastLoadedValues();
      }
      right = evaluateRightOperand(rightNode, rightIrType, irResultType, ctx, builder);
    }

    IrRhs.BinOp.BinOpName binOpName = mapOperator(opSymbol, opSymbols, opNames, node.symbol());
    IrRhs.BinOp binOp = new IrRhs.BinOp(binOpName, left, right, irResultType);
    IrTemp result = builder.tempFactory().newTemp(irResultType);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, binOp));

    if (ExpressionAnalyzer.containsPostfixIncrement(rightNode)) {
      postfixHandler.performPostfixIncrement(rightNode, ctx);
    }

    if (binOpName == IrRhs.BinOp.BinOpName.ADD || binOpName == IrRhs.BinOp.BinOpName.SUB) {
      ctx.addressReuseContext().clearAllLastLoadedValues();
    }

    return result;
  }

  private TerminalNode extractOperator(ParseNode opNode, String nodeSymbol) {
    if (!(opNode instanceof TerminalNode term)) {
      throw new IllegalStateException("Binary expression operator must be a terminal node in " + nodeSymbol);
    }
    return term;
  }

  private NonTerminalNode extractOperand(ParseNode node, String side) {
    if (!(node instanceof NonTerminalNode nt)) {
      throw new IllegalArgumentException(side + " operand must be a non-terminal");
    }
    return nt;
  }

  private IrType determineResultType(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    Type leftType = leftNode.attributes().type();
    Type rightType = rightNode.attributes().type();
    Type resultType;
    if (leftType != null && rightType != null && leftType.isScalar() && rightType.isScalar()) {
      resultType = TypeSystem.arithmeticResult(leftType, rightType);
    } else {
      resultType = leftType != null ? leftType : rightType;
    }
    return TypeMapper.toIrType(resultType);
  }

  private boolean shouldEvaluateRightFirst(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    boolean leftContainsCall = ExpressionAnalyzer.containsFunctionCall(leftNode);
    boolean rightContainsCall = ExpressionAnalyzer.containsFunctionCall(rightNode);
    boolean rightIsSimpleVar = ExpressionAnalyzer.isSimpleVariable(rightNode);
    return leftContainsCall && !rightContainsCall && rightIsSimpleVar;
  }

  private boolean shouldEvaluateRightFirstForArrayIndex(NonTerminalNode leftNode, NonTerminalNode rightNode) {
    boolean leftContainsCall = ExpressionAnalyzer.containsFunctionCall(leftNode);
    boolean rightContainsCall = ExpressionAnalyzer.containsFunctionCall(rightNode);
    boolean leftIsSimpleVar = ExpressionAnalyzer.isSimpleVariable(leftNode);
    boolean rightHasArrayIndex = ExpressionAnalyzer.containsArrayIndexing(rightNode);
    return leftIsSimpleVar && !leftContainsCall && rightHasArrayIndex && !rightContainsCall;
  }

  private IrValue evaluateOperand(NonTerminalNode node, IrType nodeType, IrType resultType,
      FunctionContext ctx, IrFunctionBuilder builder) {
    IrValue value = emitter.emitRValue(node, ctx);
    if (!nodeType.equals(resultType)) {
      return TypePromoter.promoteValue(value, nodeType, resultType, builder);
    }
    return value;
  }

  private IrValue evaluateRightOperand(NonTerminalNode node, IrType nodeType, IrType resultType,
      FunctionContext ctx, IrFunctionBuilder builder) {
    boolean hasPostfixInc = ExpressionAnalyzer.containsPostfixIncrement(node);
    IrValue value;
    if (hasPostfixInc) {
      value = postfixHandler.loadValueForPostfixIncrement(node, ctx);
    } else {
      value = emitter.emitRValue(node, ctx);
    }
    if (!nodeType.equals(resultType)) {
      return TypePromoter.promoteValue(value, nodeType, resultType, builder);
    }
    return value;
  }

  private IrRhs.BinOp.BinOpName mapOperator(String opSymbol, String[] opSymbols,
      IrRhs.BinOp.BinOpName[] opNames, String nodeSymbol) {
    for (int i = 0; i < opSymbols.length && i < opNames.length; i++) {
      if (opSymbol.equals(opSymbols[i])) {
        return opNames[i];
      }
    }
    try {
      return OperatorMapper.mapBinaryOperator(opSymbol);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Cannot map operator '" + opSymbol + "' in " + nodeSymbol, e);
    }
  }
}

package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for bitwise expressions (&, ^, |).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BitwiseExpressionGenerator {

  private final ExpressionEmitter emitter;

  public BitwiseExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
  }

  /**
   * Emits r-value for binary AND expression (&).
   */
  public IrValue emitBinaryAnd(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitBitwise(node, functionContext, "AMPERSAND", IrRhs.BinOp.BinOpName.AND);
  }

  /**
   * Emits r-value for binary XOR expression (^).
   */
  public IrValue emitBinaryXor(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitBitwise(node, functionContext, "OP_BIN_XILI", IrRhs.BinOp.BinOpName.XOR);
  }

  /**
   * Emits r-value for binary OR expression (|).
   */
  public IrValue emitBinaryOr(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitBitwise(node, functionContext, "OP_BIN_ILI", IrRhs.BinOp.BinOpName.OR);
  }

  private IrValue emitBitwise(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      String opSymbol,
      IrRhs.BinOp.BinOpName opName) {
    List<ParseNode> children = node.children();
    if (children.size() >= 3) {
      ParseNode opNode = children.get(1);
      if (opNode instanceof TerminalNode term && term.symbol().equals(opSymbol)) {
        ParseNode leftParseNode = children.get(0);
        ParseNode rightParseNode = children.get(2);
        if (leftParseNode instanceof NonTerminalNode leftNode
            && rightParseNode instanceof NonTerminalNode rightNode) {
          IrValue left = emitter.emitRValue(leftNode, functionContext);
          IrValue right = emitter.emitRValue(rightNode, functionContext);

          Type leftType = leftNode.attributes().type();
          IrType irType = TypeMapper.toIrType(leftType);
          IrRhs.BinOp binOpRhs = new IrRhs.BinOp(opName, left, right, irType);
          IrFunctionBuilder builder = functionContext.functionBuilder();
          IrTemp result = builder.tempFactory().newTemp(irType);
          builder.addInstruction(new IrInstruction.IrAssignInstr(result, binOpRhs));
          return result;
        }
      }
    }

    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
      return emitter.emitRValue(nt, functionContext);
    }

    throw new IllegalArgumentException(
        "Cannot emit r-value for bitwise expression with operator: " + opSymbol);
  }
}

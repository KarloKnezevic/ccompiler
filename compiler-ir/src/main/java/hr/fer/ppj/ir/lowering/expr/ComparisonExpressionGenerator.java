package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.util.OperatorMapper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for comparison expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ComparisonExpressionGenerator {

  private final ExpressionEmitter emitter;

  public ComparisonExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
  }

  /**
   * Emits r-value for a relational expression (<, >, <=, >=).
   */
  public IrValue emitRelational(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitComparison(
        node,
        functionContext,
        new String[] {"OP_LT", "OP_GT", "OP_LTE", "OP_GTE"},
        IrRhs.CmpOp.CmpOpName.LT,
        IrRhs.CmpOp.CmpOpName.GT,
        IrRhs.CmpOp.CmpOpName.LE,
        IrRhs.CmpOp.CmpOpName.GE);
  }

  /**
   * Emits r-value for an equality expression (==, !=).
   */
  public IrValue emitEquality(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return emitComparison(
        node,
        functionContext,
        new String[] {"OP_EQ", "OP_NEQ"},
        IrRhs.CmpOp.CmpOpName.EQ,
        IrRhs.CmpOp.CmpOpName.NE,
        null,
        null);
  }

  private IrValue emitComparison(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      String[] opSymbols,
      IrRhs.CmpOp.CmpOpName opName1,
      IrRhs.CmpOp.CmpOpName opName2,
      IrRhs.CmpOp.CmpOpName opName3,
      IrRhs.CmpOp.CmpOpName opName4) {
    List<ParseNode> children = node.children();
    if (children.size() < 3) {
      if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException("Invalid comparison expression");
    }

    // Find operator
    ParseNode opNode = null;
    int opIndex = -1;
    for (int i = 1; i < children.size(); i += 2) {
      if (children.get(i) instanceof TerminalNode term) {
        for (String opSymbol : opSymbols) {
          if (term.symbol().equals(opSymbol)) {
            opNode = term;
            opIndex = i;
            break;
          }
        }
        if (opNode != null) {
          break;
        }
      }
    }

    if (opNode == null) {
      if (children.get(0) instanceof NonTerminalNode nt) {
        return emitter.emitRValue(nt, functionContext);
      }
      throw new IllegalArgumentException("No operator in comparison expression");
    }

    // Get left and right operands
    ParseNode leftParseNode = children.get(opIndex - 1);
    ParseNode rightParseNode = children.get(opIndex + 1);
    if (!(leftParseNode instanceof NonTerminalNode leftNode)) {
      throw new IllegalArgumentException("Left operand must be a non-terminal");
    }
    if (!(rightParseNode instanceof NonTerminalNode rightNode)) {
      throw new IllegalArgumentException("Right operand must be a non-terminal");
    }

    IrValue left = emitter.emitRValue(leftNode, functionContext);
    IrValue right = emitter.emitRValue(rightNode, functionContext);

    // Map operator
    String opSymbol = ((TerminalNode) opNode).symbol();
    IrRhs.CmpOp.CmpOpName cmpOpName;
    if (opSymbol.equals(opSymbols[0])) {
      cmpOpName = opName1;
    } else if (opSymbols.length > 1 && opSymbol.equals(opSymbols[1])) {
      cmpOpName = opName2;
    } else if (opSymbols.length > 2 && opSymbol.equals(opSymbols[2]) && opName3 != null) {
      cmpOpName = opName3;
    } else if (opSymbols.length > 3 && opSymbol.equals(opSymbols[3]) && opName4 != null) {
      cmpOpName = opName4;
    } else {
      try {
        cmpOpName = OperatorMapper.mapComparisonOperator(opSymbol);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "Cannot map operator '" + opSymbol + "' to IR comparison operation", e);
      }
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();
    IrRhs.CmpOp cmpOp = new IrRhs.CmpOp(cmpOpName, left, right);
    IrTemp result = builder.tempFactory().newTemp(IrPrimitiveType.BOOL);
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, cmpOp));
    return result;
  }
}

package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.lowering.LoopContext;
import hr.fer.ppj.ir.util.TypePromoter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for jump statements (return, break, continue).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class JumpStatementGenerator {

  private final ExpressionGenerator expressionGenerator;

  public JumpStatementGenerator(ExpressionGenerator expressionGenerator) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
  }

  /**
   * Generates a jump statement (return, break, continue).
   */
  public void generateJumpStatement(NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();
    ParseNode firstChild = children.get(0);
    if (firstChild instanceof TerminalNode term) {
      String keyword = term.symbol();
      if (keyword.equals("KR_RETURN")) {
        generateReturn(children, functionContext, builder);
      } else if (keyword.equals("KR_BREAK")) {
        LoopContext loopContext = functionContext.loopContext();
        if (loopContext.exitLabel() != null) {
          builder.setTerminator(new IrTerminator.IrJmpTerm(loopContext.exitLabel()));
        } else {
          throw new IllegalStateException("break statement outside of loop");
        }
      } else if (keyword.equals("KR_CONTINUE")) {
        LoopContext loopContext = functionContext.loopContext();
        if (loopContext.continueLabel() != null) {
          builder.setTerminator(new IrTerminator.IrJmpTerm(loopContext.continueLabel()));
        } else {
          throw new IllegalStateException("continue statement outside of loop");
        }
      }
    }
  }

  private void generateReturn(
      List<ParseNode> children, FunctionContext functionContext, IrFunctionBuilder builder) {
    // Find expression after RETURN
    IrValue returnValue = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
        returnValue = expressionGenerator.emitRValue(nt, functionContext);
        break;
      }
    }

    // Check if return type promotion is needed (e.g., char -> int32)
    if (returnValue != null && functionContext.returnType() != null) {
      IrType valueType =
          returnValue instanceof hr.fer.ppj.ir.model.IrTemp temp
              ? temp.type()
              : (returnValue instanceof hr.fer.ppj.ir.model.IrConst constVal
                  ? constVal.type()
                  : null);
      if (valueType != null && !valueType.equals(functionContext.returnType())) {
        returnValue =
            TypePromoter.promoteValue(
                returnValue, valueType, functionContext.returnType(), builder);
      }
    }

    builder.setTerminator(new IrTerminator.IrRetTerm(returnValue));
  }
}

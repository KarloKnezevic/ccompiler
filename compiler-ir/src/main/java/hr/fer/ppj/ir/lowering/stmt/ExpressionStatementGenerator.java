package hr.fer.ppj.ir.lowering.stmt;

import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.lowering.expr.LogicalExpressionGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.Objects;

/**
 * Generates IR for expression statements.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionStatementGenerator {

  private final ExpressionGenerator expressionGenerator;
  private final LogicalExpressionGenerator logicalGenerator;

  public ExpressionStatementGenerator(ExpressionGenerator expressionGenerator) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.logicalGenerator = new LogicalExpressionGenerator(expressionGenerator);
  }

  /**
   * Generates an expression statement.
   *
   * <p>Evaluates expression for side effects (discards result).
   */
  public void generateExpressionStatement(
      NonTerminalNode node, FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    // <izraz_naredba> contains <izraz>?
    // Evaluate expression for side effects (discard result)
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<izraz>")) {
        // Check if it's a logical expression - if so, use materializeResult=false
        NonTerminalNode exprNode = findLogicalExpressionInIzraz(nt);
        if (exprNode != null) {
          // For logical expressions in expression statements, evaluate for side effects only
          logicalGenerator.emitRValueLogical(exprNode, functionContext, false);
        } else {
          expressionGenerator.emitRValue(nt, functionContext);
        }
      }
    }
  }

  private NonTerminalNode findLogicalExpressionInIzraz(NonTerminalNode izrazNode) {
    String symbol = izrazNode.symbol();
    if (symbol.equals("<log_i_izraz>") || symbol.equals("<log_ili_izraz>")) {
      // Check if this logical expression is just a wrapper (single child)
      var children = izrazNode.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode firstChild) {
        String firstChildSymbol = firstChild.symbol();
        if (firstChildSymbol.equals("<log_i_izraz>") || firstChildSymbol.equals("<log_ili_izraz>")) {
          // It's a wrapper - return the nested logical expression
          return findLogicalExpressionInIzraz(firstChild);
        }
      }
      // It's a real logical expression (has operator) - return it
      return izrazNode;
    }
    if (symbol.equals("<izraz>")) {
      var children = izrazNode.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        String childSymbol = child.symbol();
        if (childSymbol.equals("<log_i_izraz>") || childSymbol.equals("<log_ili_izraz>")) {
          return child;
        }
        if (childSymbol.equals("<izraz_pridruzivanja>")) {
          var pridruzivanjaChildren = child.children();
          // Check if it's a top-level assignment (has OP_PRIDRUZI as a direct child)
          boolean isTopLevelAssignment = false;
          for (ParseNode c : pridruzivanjaChildren) {
            if (c instanceof hr.fer.ppj.semantics.tree.TerminalNode t && t.symbol().equals("OP_PRIDRUZI")) {
              isTopLevelAssignment = true;
              break;
            }
          }
          if (isTopLevelAssignment) {
            return null;
          }
          // For <izraz_pridruzivanja> without assignment, it's just <log_ili_izraz>
          // Check directly for logical expressions first
          for (ParseNode c : pridruzivanjaChildren) {
            if (c instanceof NonTerminalNode nt) {
              String cSymbol = nt.symbol();
              if (cSymbol.equals("<log_i_izraz>") || cSymbol.equals("<log_ili_izraz>")) {
                // Check recursively for nested logical expressions
                NonTerminalNode nested = findLogicalExpressionInIzraz(nt);
                if (nested != null) {
                  return nested;
                }
                return nt;
              }
              // Recursively check nested expressions
              NonTerminalNode nested = findLogicalExpressionInIzraz(nt);
              if (nested != null) {
                return nested;
              }
            }
          }
        }
      }
    }
    // Recursively check all children for logical expressions
    for (ParseNode child : izrazNode.children()) {
      if (child instanceof NonTerminalNode childNode) {
        NonTerminalNode nested = findLogicalExpressionInIzraz(childNode);
        if (nested != null) {
          return nested;
        }
      }
    }
    return null;
  }
}

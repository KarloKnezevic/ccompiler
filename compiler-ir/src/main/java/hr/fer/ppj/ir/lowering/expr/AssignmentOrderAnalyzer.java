package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;

/**
 * Analyzes expressions to determine optimal assignment evaluation order.
 *
 * <p>This class examines expression AST structure to detect patterns that
 * require special evaluation ordering to match expected IR output format.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class AssignmentOrderAnalyzer {

  private AssignmentOrderAnalyzer() {}

  /**
   * Checks if an expression contains a cast operation.
   */
  static boolean containsCast(NonTerminalNode node) {
    if (node.symbol().equals("<cast_izraz>")) {
      if (node.children().size() >= 4) {
        return true;
      }
    }
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsCast(nt)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if an expression contains array indexing.
   */
  static boolean containsArrayIndexing(NonTerminalNode node) {
    if (node.symbol().equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode t && t.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsArrayIndexing(nt)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if an expression contains pre-increment or pre-decrement.
   */
  static boolean containsPreIncrement(NonTerminalNode node) {
    if (node.symbol().equals("<unarni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 2) {
        ParseNode first = children.get(0);
        if (first instanceof TerminalNode t
            && (t.symbol().equals("OP_INC") || t.symbol().equals("OP_DEC"))) {
          return true;
        }
        if (first instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
          List<ParseNode> opChildren = nt.children();
          if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opT
              && (opT.symbol().equals("OP_INC") || opT.symbol().equals("OP_DEC"))) {
            return true;
          }
        }
      }
    }
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt && containsPreIncrement(nt)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if an expression is array indexing.
   */
  static boolean isArrayIndexing(NonTerminalNode node) {
    String symbol = node.symbol();
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode t && t.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }
    if (symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isArrayIndexing(child);
      }
    }
    return false;
  }

  /**
   * Checks if left and right expressions share the same first variable.
   */
  static boolean shouldReuseAddress(NonTerminalNode left, NonTerminalNode right) {
    String leftVar = ExpressionNameExtractor.extractVariableName(left);
    String rightFirstVar = ExpressionNameExtractor.extractFirstVariableName(right);
    return leftVar != null && rightFirstVar != null && leftVar.equals(rightFirstVar);
  }

  /**
   * Checks if left side is a simple variable reference.
   */
  static boolean isSimpleVariable(NonTerminalNode node) {
    return ExpressionNameExtractor.extractVariableName(node) != null;
  }
}
